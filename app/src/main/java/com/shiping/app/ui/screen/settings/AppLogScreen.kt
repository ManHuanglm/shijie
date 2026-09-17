package com.shiping.app.ui.screen.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.shiping.app.util.AppLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用日志查看页：实时滚动、级别过滤、清空、分享日志文件，便于开发调试与问题反馈。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val revision by AppLog.revision.collectAsState()
    var minLevel by remember { mutableStateOf(AppLog.Level.V) }
    var autoScroll by remember { mutableStateOf(true) }

    val listState = rememberLazyListState()
    val entries = remember(revision, minLevel) { AppLog.snapshot(minLevel) }

    LaunchedEffect(entries.size, autoScroll) {
        if (autoScroll && entries.isNotEmpty()) {
            listState.scrollToItem(entries.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("应用日志（${entries.size}）") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val file = AppLog.exportFile()
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file,
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "分享应用日志"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "分享日志")
                    }
                    IconButton(onClick = { AppLog.clear() }) {
                        Icon(Icons.Default.Delete, contentDescription = "清空日志")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 过滤栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AppLog.Level.entries.forEach { level ->
                    FilterChip(
                        selected = minLevel == level,
                        onClick = { minLevel = level },
                        label = { Text(level.label, fontSize = 12.sp) },
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text("跟随", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(checked = autoScroll, onCheckedChange = { autoScroll = it })
            }

            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "暂无日志",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF101014)),
                ) {
                    items(entries) { entry ->
                        LogLine(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogLine(entry: AppLog.Entry) {
    val color = when (entry.level) {
        AppLog.Level.V -> Color(0xFF9E9E9E)
        AppLog.Level.D -> Color(0xFF7FA8FF)
        AppLog.Level.I -> Color(0xFF8BC785)
        AppLog.Level.W -> Color(0xFFFFC861)
        AppLog.Level.E -> Color(0xFFFF6B6B)
    }
    val time = remember(entry.timeMs) {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(entry.timeMs))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = "$time ${entry.level.label}/${entry.tag}: ${entry.message}",
            color = color,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 14.sp,
        )
        if (entry.throwableText != null) {
            Text(
                text = entry.throwableText,
                color = color.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 13.sp,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}
