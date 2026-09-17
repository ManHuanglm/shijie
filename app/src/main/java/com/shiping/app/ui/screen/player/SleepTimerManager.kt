package com.shiping.app.ui.screen.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 睡眠定时器：倒计时结束后触发回调（暂停播放或退出）。
 */
class SleepTimerManager(private val scope: CoroutineScope) {

    private val _remainingMs = MutableStateFlow(0L)
    val remainingMs: StateFlow<Long> = _remainingMs.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var timerJob: Job? = null
    private var onTimeout: (() -> Unit)? = null

    /**
     * 启动定时器
     * @param minutes 定时分钟数
     * @param onTimeout 倒计时结束回调
     */
    fun start(minutes: Int, onTimeout: () -> Unit) {
        cancel()
        this.onTimeout = onTimeout
        _isRunning.value = true
        val totalMs = minutes * 60_000L
        _remainingMs.value = totalMs
        timerJob = scope.launch {
            val startMs = System.currentTimeMillis()
            while (_remainingMs.value > 0) {
                delay(1_000L)
                val elapsed = System.currentTimeMillis() - startMs
                _remainingMs.value = (totalMs - elapsed).coerceAtLeast(0L)
            }
            _isRunning.value = false
            onTimeout()
        }
    }

    /** 取消定时器 */
    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        _isRunning.value = false
        _remainingMs.value = 0L
        onTimeout = null
    }

    /** 剩余时间格式化文本 mm:ss */
    val remainingText: String
        get() {
            val totalSeconds = _remainingMs.value / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }
}
