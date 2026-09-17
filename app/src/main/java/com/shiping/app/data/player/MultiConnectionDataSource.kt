package com.shiping.app.data.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.shiping.app.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * 多连接并行缓冲数据源（缓冲线路倍数）。
 *
 * 将大的视频切片请求拆分为 N 个并行 Range 子请求以提升缓冲下载速度：
 * 1. 首连接正常打开切片拿到总长度，顺序向播放器供数
 * 2. 其余 N-1 个连接并行拉取各自区段到内存
 * 3. 首连接读完后按序消费各区段缓冲，总内存占用 ≈ 一个切片大小
 *
 * 直接透传的场景：单连接（倍数=1）、非 HTTP、播放列表（.m3u8）、
 * 带位置/已知长度的请求、服务器不支持 Range（按 host 记忆探测结果）。
 */
@UnstableApi
class MultiConnectionDataSource(
    private val childFactory: DataSource.Factory,
) : DataSource {

    /** 已探测的 Range 支持结果：true 支持 / false 不支持 */
    private val rangeSupportedHosts = ConcurrentHashMap<String, Boolean>()

    /** 同时连接数（缓冲线路倍数），动态可调 */
    @Volatile
    private var connections = 1

    private val listeners = mutableListOf<TransferListener>()

    /** 单连接模式下的委托数据源 */
    private var child: DataSource? = null

    /** 并行模式状态 */
    private var stream0: DataSource? = null
    private var fetchers: List<Fetcher> = emptyList()
    private var serveBuffer: ByteArray? = null
    private var servePos = 0
    private var fetcherIndex = 0
    private var scope: CoroutineScope? = null
    private var currentHost: String = ""
    private var currentDataSpec: DataSpec? = null
    private var lastUri: Uri? = null

    /** 动态设置同时连接数 */
    fun setConnections(count: Int) {
        connections = count.coerceAtLeast(1)
    }

    override fun addTransferListener(transferListener: TransferListener) {
        listeners.add(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        closeInternal()
        transferInitializing(dataSpec)
        currentDataSpec = dataSpec

        val uri = dataSpec.uri
        lastUri = uri
        currentHost = uri.host ?: ""
        val host = currentHost
        val canParallel = connections > 1 &&
            (uri.scheme == "http" || uri.scheme == "https") &&
            !uri.path.orEmpty().substringBefore('?').endsWith(".m3u8") &&
            dataSpec.position == 0L &&
            dataSpec.length == C.LENGTH_UNSET.toLong()

        if (canParallel) {
            probeRangeSupport(dataSpec, host)
        }

        // 不满足并行条件或该 host 不支持 Range：单连接直读
        if (!canParallel || rangeSupportedHosts[host] != true) {
            val ds = childFactory.createDataSource()
            child = ds
            val length = ds.open(dataSpec)
            lastUri = ds.uri
            transferStarted(dataSpec)
            return length
        }

        // 首连接：整体打开拿到总长度
        val first = childFactory.createDataSource()
        val total = first.open(dataSpec)
        if (total == C.LENGTH_UNSET.toLong() || total <= Constants.BUFFER_MIN_PARALLEL_BYTES) {
            // 小切片或长度未知，不值得并行，直接用首连接
            child = first
            lastUri = first.uri
            transferStarted(dataSpec)
            return total
        }

        // 并行模式：首连接 + N-1 个区段拉取协程
        stream0 = first
        lastUri = first.uri
        val corScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = corScope
        val created = mutableListOf<Fetcher>()
        try {
            val chunk = total / connections
            for (i in 1 until connections) {
                val start = i * chunk
                val len = if (i == connections - 1) total - start else chunk
                if (len <= 0L) break
                created.add(launchFetcher(corScope, dataSpec, start, len))
            }
        } catch (t: Throwable) {
            rangeSupportedHosts[host] = false
            corScope.cancel()
            closeQuietly(first)
            stream0 = null
            fetchers = emptyList()
            throw if (t is IOException) t else IOException(t)
        }
        fetchers = created
        transferStarted(dataSpec)
        return total
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val single = child
        if (single != null) {
            val n = single.read(buffer, offset, length)
            if (n > 0) bytesTransferred(n)
            return n
        }

        val first = stream0 ?: throw IOException("MultiConnectionDataSource not open")
        while (true) {
            if (serveBuffer == null) {
                val n = first.read(buffer, offset, length)
                if (n != C.RESULT_END_OF_INPUT) {
                    if (n > 0) bytesTransferred(n)
                    return n
                }
                // 首连接读完，切换到并行区段缓冲
                closeQuietly(first)
                stream0 = null
                if (fetcherIndex >= fetchers.size) return C.RESULT_END_OF_INPUT
                val f = fetchers[fetcherIndex]
                serveBuffer = try {
                    runBlocking { f.buffer.await() }
                } catch (t: Throwable) {
                    rangeSupportedHosts[currentHost] = false
                    throw if (t is IOException) t else IOException(t)
                }
                servePos = 0
                continue
            }
            val buf = serveBuffer!!
            if (servePos >= buf.size) {
                // 当前区段消费完，切换到下一个
                serveBuffer = null
                fetcherIndex++
                if (fetcherIndex >= fetchers.size) return C.RESULT_END_OF_INPUT
                continue
            }
            val n = minOf(length, buf.size - servePos)
            System.arraycopy(buf, servePos, buffer, offset, n)
            servePos += n
            bytesTransferred(n)
            return n
        }
    }

    override fun getUri(): Uri? = child?.uri ?: lastUri

    override fun getResponseHeaders(): Map<String, List<String>> =
        child?.responseHeaders ?: emptyMap()

    override fun close() {
        closeInternal()
        transferEnded()
    }

    /** 探测 host 是否支持 Range：请求 1 字节，返回长度为 1 即支持 */
    private fun probeRangeSupport(dataSpec: DataSpec, host: String) {
        if (host.isEmpty() || rangeSupportedHosts.containsKey(host)) return
        val probe = childFactory.createDataSource()
        try {
            val len = probe.open(dataSpec.buildUpon().setPosition(0L).setLength(1L).build())
            rangeSupportedHosts[host] = len == 1L
        } catch (_: IOException) {
            // 探测失败不缓存结果，本次走单连接
        } finally {
            closeQuietly(probe)
        }
    }

    /** 启动一个区段拉取协程：打开 Range 子请求并整体读入内存 */
    private fun launchFetcher(
        corScope: CoroutineScope,
        dataSpec: DataSpec,
        position: Long,
        length: Long,
    ): Fetcher {
        val ds = childFactory.createDataSource()
        return Fetcher(ds, corScope.async {
            try {
                val opened = ds.open(dataSpec.buildUpon().setPosition(position).setLength(length).build())
                if (opened != length) {
                    // 服务器未按 Range 返回（如回 200 全量），判定不支持，避免拼错数据
                    throw IOException("Range not honored: expected $length got $opened")
                }
                readAll(ds)
            } finally {
                closeQuietly(ds)
            }
        })
    }

    private fun readAll(ds: DataSource): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = ds.read(buf, 0, buf.size)
            if (n == C.RESULT_END_OF_INPUT) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** 重置内部状态并关闭所有子连接 */
    private fun closeInternal() {
        scope?.cancel()
        scope = null
        closeQuietly(stream0)
        stream0 = null
        fetchers.forEach { closeQuietly(it.source) }
        fetchers = emptyList()
        serveBuffer = null
        servePos = 0
        fetcherIndex = 0
        closeQuietly(child)
        child = null
    }

    private fun closeQuietly(ds: DataSource?) {
        if (ds == null) return
        try {
            ds.close()
        } catch (_: Exception) {
        }
    }

    private fun transferInitializing(dataSpec: DataSpec) {
        listeners.forEach { it.onTransferInitializing(this, dataSpec, true) }
    }

    private fun transferStarted(dataSpec: DataSpec) {
        listeners.forEach { it.onTransferStart(this, dataSpec, true) }
    }

    private fun bytesTransferred(bytes: Int) {
        val dataSpec = currentDataSpec ?: return
        listeners.forEach { it.onBytesTransferred(this, dataSpec, true, bytes) }
    }

    private fun transferEnded() {
        val dataSpec = DataSpec(lastUri ?: Uri.EMPTY)
        listeners.forEach { it.onTransferEnd(this, dataSpec, true) }
    }

    /** 并行区段拉取器 */
    private class Fetcher(
        val source: DataSource,
        val buffer: Deferred<ByteArray>,
    )
}

/** [MultiConnectionDataSource] 工厂，支持动态调整同时连接数 */
@UnstableApi
class MultiConnectionDataSourceFactory(
    private val childFactory: DataSource.Factory,
) : DataSource.Factory {

    @Volatile
    private var connections = 1

    override fun createDataSource(): DataSource =
        MultiConnectionDataSource(childFactory).apply { setConnections(connections) }

    /** 动态设置同时连接数（缓冲线路倍数） */
    fun setConnections(count: Int) {
        connections = count.coerceAtLeast(1)
    }
}
