package com.shiping.app.data.download

import android.net.Uri
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.TransferListener

/**
 * 限速数据源：包装上游数据源，通过时间窗口算法限制读取速度。
 * @param upstream 上游数据源
 * @param bytesPerSecond 限速字节数/秒，0 表示不限速
 */
class RateLimitedDataSource(
    private val upstream: DataSource,
    private var bytesPerSecond: Long,
) : BaseDataSource(true) {

    private var bytesReadInWindow: Long = 0L
    private var windowStartMs: Long = 0L

    override fun open(dataSpec: DataSpec): Long {
        bytesReadInWindow = 0L
        windowStartMs = System.currentTimeMillis()
        transferInitializing(dataSpec)
        val opened = upstream.open(dataSpec)
        transferStarted(dataSpec)
        return opened
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (bytesPerSecond > 0) {
            throttle()
        }
        val bytesRead = upstream.read(buffer, offset, length)
        if (bytesRead > 0) {
            bytesReadInWindow += bytesRead
            bytesTransferred(bytesRead)
        }
        return bytesRead
    }

    /** 时间窗口限速：当前窗口内已读取字节超过限速时，sleep 到下一窗口 */
    private fun throttle() {
        val elapsedMs = System.currentTimeMillis() - windowStartMs
        if (elapsedMs >= 1000L) {
            bytesReadInWindow = 0L
            windowStartMs = System.currentTimeMillis()
            return
        }
        val allowedBytes = bytesPerSecond * elapsedMs / 1000L
        if (bytesReadInWindow >= allowedBytes) {
            val sleepMs = 1000L - elapsedMs
            if (sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            bytesReadInWindow = 0L
            windowStartMs = System.currentTimeMillis()
        }
    }

    override fun getUri(): Uri? = upstream.uri

    override fun close() {
        try {
            upstream.close()
        } finally {
            transferEnded()
        }
    }

    /** 动态更新限速值 */
    fun setBytesPerSecond(bytesPerSecond: Long) {
        this.bytesPerSecond = bytesPerSecond
    }
}

/**
 * 限速数据源工厂。
 */
class RateLimitedDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
    private var bytesPerSecond: Long,
    private val transferListener: TransferListener? = null,
) : DataSource.Factory {

    override fun createDataSource(): DataSource {
        val upstream = upstreamFactory.createDataSource()
        if (transferListener != null) {
            upstream.addTransferListener(transferListener)
        }
        return RateLimitedDataSource(upstream, bytesPerSecond)
    }

    /** 动态更新限速值 */
    fun setBytesPerSecond(bytesPerSecond: Long) {
        this.bytesPerSecond = bytesPerSecond
    }
}
