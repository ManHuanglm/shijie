package com.shiping.app.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import com.shiping.app.MainActivity
import com.shiping.app.R
import com.shiping.app.data.local.AppDatabase
import kotlinx.coroutines.runBlocking

/**
 * ExoPlayer 前台下载服务。
 * 下载完成通知点击可跳转至对应视频播放页。
 */
@UnstableApi
class VideoDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_channel_name,
    R.string.download_channel_description,
) {

    override fun getDownloadManager(): DownloadManager {
        return DownloadManagerHolder.getDownloadManager()
    }

    override fun getScheduler(): Scheduler? {
        return if (Util.SDK_INT >= 21) {
            PlatformScheduler(this, JOB_ID)
        } else {
            null
        }
    }

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.download_channel_description)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val total = downloads.size
        val completed = downloads.count { it.state == Download.STATE_COMPLETED }
        val active = downloads.count { it.state == Download.STATE_DOWNLOADING }
        val failed = downloads.count { it.state == Download.STATE_FAILED }

        // 查找最新完成的下载，用于通知点击跳转播放
        val latestCompleted = downloads
            .filter { it.state == Download.STATE_COMPLETED }
            .maxByOrNull { it.updateTimeMs }

        val contentText = when {
            failed > 0 -> "下载失败 $failed 个"
            active > 0 -> "正在下载 $active / $total 个视频"
            latestCompleted != null -> "下载完成：${latestCompleted.request.id}"
            else -> "已完成 $completed / $total 个视频"
        }

        // 构建跳转 Intent：若有完成的下载，传递其 URL 与标题
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (latestCompleted != null) {
                // 从 Room 查询标题
                val entity = runCatching {
                    runBlocking {
                        AppDatabase.getInstance().downloadDao().getById(latestCompleted.request.id)
                    }
                }.getOrNull()
                putExtra(MainActivity.EXTRA_PLAY_URL, entity?.url ?: latestCompleted.request.id)
                putExtra(MainActivity.EXTRA_PLAY_TITLE, entity?.title ?: "已下载视频")
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(active > 0)
            .setProgress(total, completed, total == 0)
            .setAutoCancel(latestCompleted != null)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "video_download_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 1001
        private const val JOB_ID = 1002
    }
}
