package com.shiping.app.ui.screen.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import com.shiping.app.BuildConfig
import com.shiping.app.di.AppContainer
import com.shiping.app.ui.theme.BackgroundPresets
import com.shiping.app.util.Constants
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onApiManage: () -> Unit,
    onParserManage: () -> Unit,
    onDownloadManage: () -> Unit,
    onTvSourceManage: () -> Unit,
    onAppLog: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = AppContainer.preferences

    val themeMode by preferences.themeMode.collectAsState(initial = 0)
    val bgType by preferences.backgroundType.collectAsState(initial = 0)
    val customBgUri by preferences.customBgUri.collectAsState(initial = "")
    val bufferMultiplier by preferences.bufferMultiplier.collectAsState(initial = 1)
    val preloadEnabled by preferences.preloadEnabled.collectAsState(initial = false)
    val preloadNextEp by preferences.preloadNextEpisode.collectAsState(initial = false)
    val preloadThreads by preferences.preloadThreads.collectAsState(initial = 3)
    val preloadCapacity by preferences.preloadCapacityMb.collectAsState(initial = 512)
    val preloadTime by preferences.preloadTimeS.collectAsState(initial = 30)
    val downloadWifiOnly by preferences.downloadWifiOnly.collectAsState(initial = false)
    val downloadSpeedLimit by preferences.downloadSpeedLimitKbps.collectAsState(initial = 0)
    val downloadMaxParallel by preferences.downloadMaxParallel.collectAsState(initial = 2)
    val longPressSpeed by preferences.longPressSpeed.collectAsState(initial = Constants.LONG_PRESS_SPEED)
    val adFilterEnabled by preferences.adFilterEnabled.collectAsState(initial = true)
    val pipEnabled by preferences.pipEnabled.collectAsState(initial = true)
    val bufferLineMultiplier by preferences.bufferLineMultiplier.collectAsState(initial = Constants.BUFFER_LINE_MULTIPLIER_DEFAULT)

    val snackbarHostState = remember { SnackbarHostState() }

    var cacheExpanded by remember { mutableStateOf(false) }
    var themeExpanded by remember { mutableStateOf(false) }
    var preloadExpanded by remember { mutableStateOf(false) }
    var downloadExpanded by remember { mutableStateOf(false) }
    var playerExpanded by remember { mutableStateOf(false) }

    // 图片选择器
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // 某些URI不支持持久化权限，忽略
            }
            scope.launch {
                preferences.setCustomBgUri(it.toString())
                preferences.setBackgroundType(6)
                snackbarHostState.showSnackbar("已设置自定义背景")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // region 源与解析
            item { SettingsSectionHeader("源与解析") }

            // region API 管理
            item {
                SettingsCard(
                    icon = { Icon(Icons.Default.Api, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = "API 管理",
                    subtitle = "添加、编辑、导入导出、检测视频源"
                ) { onApiManage() }
            }
            // endregion

            // region 解析管理
            item {
                SettingsCard(
                    icon = { Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = "解析管理",
                    subtitle = "配置视频解析地址，非直链自动拼接"
                ) { onParserManage() }
            }
            // endregion

            // region 电视直播源
            item {
                SettingsCard(
                    icon = { Icon(Icons.Default.LiveTv, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = "电视直播源",
                    subtitle = "添加、编辑、启用直播 M3U 源"
                ) { onTvSourceManage() }
            }
            // endregion

            // region 播放与下载
            item { SettingsSectionHeader("播放与下载") }

            // region 下载设置
            item {
                ExpandableCard(
                    icon = { Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = "下载设置",
                    subtitle = buildString {
                        append("并发 $downloadMaxParallel")
                        if (downloadWifiOnly) append(" · 仅WiFi")
                        val speedIdx = Constants.DOWNLOAD_SPEED_OPTIONS.indexOf(downloadSpeedLimit)
                        if (speedIdx >= 0) append(" · ").append(Constants.DOWNLOAD_SPEED_LABELS[speedIdx])
                    },
                    expanded = downloadExpanded,
                    onToggle = { downloadExpanded = !downloadExpanded }
                ) {
                    // 仅 WiFi 下载
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("仅 WiFi 下载", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(
                                "开启后移动网络下自动暂停下载",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = downloadWifiOnly,
                            onCheckedChange = { scope.launch { preferences.setDownloadWifiOnly(it) } }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    // 下载限速
                    Text("下载限速", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Constants.DOWNLOAD_SPEED_OPTIONS.forEachIndexed { index, kbps ->
                            val selected = downloadSpeedLimit == kbps
                            OutlinedButton(
                                onClick = { scope.launch { preferences.setDownloadSpeedLimitKbps(kbps) } },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    Constants.DOWNLOAD_SPEED_LABELS[index],
                                    fontSize = 11.sp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    // 并发数
                    Text("批量下载并发数: $downloadMaxParallel", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Slider(
                        value = downloadMaxParallel.toFloat(),
                        onValueChange = { scope.launch { preferences.setDownloadMaxParallel(it.toInt()) } },
                        valueRange = Constants.DOWNLOAD_PARALLEL_MIN.toFloat()..Constants.DOWNLOAD_PARALLEL_MAX.toFloat(),
                        steps = Constants.DOWNLOAD_PARALLEL_MAX - Constants.DOWNLOAD_PARALLEL_MIN - 1,
                    )
                }
            }
            // endregion

            // region 离线缓存
            item {
                SettingsCard(
                    icon = { Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = "离线缓存",
                    subtitle = "管理已下载视频、缓存清晰度选择"
                ) { onDownloadManage() }
            }
            // endregion

            // region 播放设置
            item {
                ExpandableCard(
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = "播放设置",
                    subtitle = "长按倍速 ${longPressSpeed}x · 去广告${if (adFilterEnabled) "开" else "关"} · 画中画${if (pipEnabled) "开" else "关"}",
                    expanded = playerExpanded,
                    onToggle = { playerExpanded = !playerExpanded }
                ) {
                    Text("长按倍速", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(
                        "播放时长按屏幕以该倍速快进，松开后恢复原速",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Constants.LONG_PRESS_SPEED_OPTIONS.forEach { speed ->
                            OutlinedButton(
                                onClick = { scope.launch { preferences.setLongPressSpeed(speed) } },
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (speed == longPressSpeed) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                ),
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (speed == longPressSpeed) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    } else {
                                        Color.Transparent
                                    },
                                    contentColor = if (speed == longPressSpeed) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                ),
                            ) {
                                Text("${speed}x", fontSize = 13.sp)
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    // m3u8 去广告开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("m3u8 去广告", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(
                                "自动过滤播放列表中的广告切片",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = adFilterEnabled,
                            onCheckedChange = { scope.launch { preferences.setAdFilterEnabled(it) } }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    // 画中画开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("画中画", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(
                                "播放视频时按 Home 键自动以小窗继续播放（需 Android 8.0 及以上）",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = pipEnabled,
                            onCheckedChange = { scope.launch { preferences.setPipEnabled(it) } }
                        )
                    }
                }
            }
            // endregion

            // region 缓存设置
            item {
                ExpandableCard(
                    icon = { Icon(Icons.Default.Cached, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = "缓存设置",
                    subtitle = "缓冲倍数 ${bufferMultiplier}x · 线路 ${bufferLineMultiplier}x",
                    expanded = cacheExpanded,
                    onToggle = { cacheExpanded = !cacheExpanded }
                ) {
                    // 缓冲时间倍数
                    Text("缓冲时间倍数: ${bufferMultiplier}x", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(
                        "调整播放器缓冲区大小，倍数越大缓冲越多",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = bufferMultiplier.toFloat(),
                        onValueChange = { scope.launch { preferences.setBufferMultiplier(it.toInt()) } },
                        valueRange = Constants.BUFFER_MULTIPLIER_MIN.toFloat()..Constants.BUFFER_MULTIPLIER_MAX.toFloat(),
                        steps = Constants.BUFFER_MULTIPLIER_MAX - Constants.BUFFER_MULTIPLIER_MIN - 1,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    // 缓冲线路倍数（多连接并行加速）
                    Text("缓冲线路倍数: ${bufferLineMultiplier}x", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(
                        "同时使用 N 条连接并行下载切片以加快缓冲，1 为单连接不加速",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = bufferLineMultiplier.toFloat(),
                        onValueChange = { scope.launch { preferences.setBufferLineMultiplier(it.toInt()) } },
                        valueRange = 1f..Constants.BUFFER_LINE_MULTIPLIER_MAX.toFloat(),
                        steps = Constants.BUFFER_LINE_MULTIPLIER_MAX - 2,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    // 清空缓存
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("清空缓存", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(
                                "清除图片缓存和临时数据",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = {
                            scope.launch {
                                val imageLoader = context.imageLoader
                                imageLoader.memoryCache?.clear()
                                imageLoader.diskCache?.clear()
                                snackbarHostState.showSnackbar("缓存已清空")
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("清除")
                        }
                    }
                }
            }
            // endregion

            // region 预载设置
            item {
                ExpandableCard(
                    icon = { Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = "预载设置",
                    subtitle = if (preloadEnabled) "已开启" else "未开启",
                    expanded = preloadExpanded,
                    onToggle = { preloadExpanded = !preloadExpanded }
                ) {
                    // 预载开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("预载开关", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Switch(
                            checked = preloadEnabled,
                            onCheckedChange = { scope.launch { preferences.setPreloadEnabled(it) } }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    // 预载下集
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("预载下集", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Switch(
                            checked = preloadNextEp,
                            onCheckedChange = { scope.launch { preferences.setPreloadNextEpisode(it) } }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    // 预载线程
                    Text("预载线程: ${preloadThreads} 条", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Slider(
                        value = preloadThreads.toFloat(),
                        onValueChange = { scope.launch { preferences.setPreloadThreads(it.toInt()) } },
                        valueRange = Constants.PRELOAD_THREADS_MIN.toFloat()..Constants.PRELOAD_THREADS_MAX.toFloat(),
                        steps = Constants.PRELOAD_THREADS_MAX - Constants.PRELOAD_THREADS_MIN - 1,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    // 预载容量
                    Text(
                        "预载容量: ${formatCapacity(preloadCapacity)}",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Slider(
                        value = preloadCapacity.toFloat(),
                        onValueChange = { scope.launch { preferences.setPreloadCapacityMb(it.toInt()) } },
                        valueRange = Constants.PRELOAD_CAPACITY_MIN_MB.toFloat()..Constants.PRELOAD_CAPACITY_MAX_MB.toFloat(),
                        steps = (Constants.PRELOAD_CAPACITY_MAX_MB - Constants.PRELOAD_CAPACITY_MIN_MB) / 128 - 1,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    // 预载时间
                    Text("预载时间: ${preloadTime}s", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Slider(
                        value = preloadTime.toFloat(),
                        onValueChange = { scope.launch { preferences.setPreloadTimeS(it.toInt()) } },
                        valueRange = Constants.PRELOAD_TIME_MIN_S.toFloat()..Constants.PRELOAD_TIME_MAX_S.toFloat(),
                        steps = (Constants.PRELOAD_TIME_MAX_S - Constants.PRELOAD_TIME_MIN_S) / 6 - 1,
                    )
                }
            }
            // endregion

            // region 通用
            item { SettingsSectionHeader("通用") }

            // region 主题设置
            item {
                ExpandableCard(
                    icon = { Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = "主题设置",
                    subtitle = if (themeMode == 0) "暗色模式" else "亮色模式",
                    expanded = themeExpanded,
                    onToggle = { themeExpanded = !themeExpanded }
                ) {
                    // 暗色/亮色切换
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("暗色模式", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Switch(
                            checked = themeMode == 0,
                            onCheckedChange = { dark ->
                                scope.launch { preferences.setThemeMode(if (dark) 0 else 1) }
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    // 背景预设
                    Text("背景选择", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    // 默认
                    BackgroundChipRow(
                        bgType = bgType,
                        isDark = themeMode == 0,
                        onSelect = { type ->
                            scope.launch { preferences.setBackgroundType(type) }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    // 自定义图片
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("自定义背景图片", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(
                                if (bgType == 6 && customBgUri.isNotEmpty()) "已设置" else "从相册选择图片",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) {
                            Text("选择图片")
                        }
                    }
                    if (bgType == 6) {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(onClick = {
                            scope.launch {
                                preferences.setBackgroundType(0)
                                preferences.setCustomBgUri("")
                                snackbarHostState.showSnackbar("已恢复默认背景")
                            }
                        }) { Text("恢复默认") }
                    }
                }
            }
            // endregion

            // region 其他
            item { SettingsSectionHeader("其他") }

            // region 应用日志
            item {
                SettingsCard(
                    icon = { Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = "应用日志",
                    subtitle = "查看运行日志、播放报错与崩溃堆栈，方便开发调试"
                ) { onAppLog() }
            }
            // endregion

            // region 关于
            item {
                SettingsCard(
                    icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = "关于",
                    subtitle = "视界 v${BuildConfig.VERSION_NAME}"
                ) { }
            }
            // endregion

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

// region 组件

/** 设置页分组标题 */
@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExpandableCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon()
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun BackgroundChipRow(
    bgType: Int,
    isDark: Boolean,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 默认
        BackgroundChip(
            label = "默认",
            brush = null,
            isSelected = bgType == 0,
            color = MaterialTheme.colorScheme.surfaceVariant,
            onClick = { onSelect(0) }
        )
        // 渐变预设
        for (i in 1..BackgroundPresets.PRESET_COUNT) {
            val brush = BackgroundPresets.getGradientBrush(i, isDark)
            BackgroundChip(
                label = BackgroundPresets.getPresetName(i),
                brush = brush,
                isSelected = bgType == i,
                color = Color.Transparent,
                onClick = { onSelect(i) }
            )
        }
        // 自定义图片
        BackgroundChip(
            label = "图片",
            brush = null,
            isSelected = bgType == 6,
            color = MaterialTheme.colorScheme.secondaryContainer,
            onClick = { onSelect(6) }
        )
    }
}

@Composable
private fun BackgroundChip(
    label: String,
    brush: Brush?,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (brush != null) Modifier.background(brush)
                    else Modifier.background(color)
                )
                .then(
                    if (isSelected) Modifier.padding(2.dp)
                    else Modifier
                )
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// endregion

// region 工具

private fun formatCapacity(mb: Int): String {
    return if (mb >= 1024) {
        val gb = mb / 1024.0
        if (gb == gb.toInt().toDouble()) "${gb.toInt()}GB"
        else String.format("%.1fGB", gb)
    } else {
        "${mb}MB"
    }
}

// endregion
