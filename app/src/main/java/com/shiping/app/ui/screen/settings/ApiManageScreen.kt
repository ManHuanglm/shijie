package com.shiping.app.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiping.app.data.model.ApiSourceEntity
import com.shiping.app.data.repository.ApiSourceRepository
import com.shiping.app.ui.component.EmptyView
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiManageScreen(
    viewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onBack: () -> Unit
) {
    val sources by viewModel.sources.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    var showAddEdit by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<ApiSourceEntity?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf("") }
    var showTvBoxImport by remember { mutableStateOf(false) }
    // 左滑删除确认
    var sourceToDelete by remember { mutableStateOf<ApiSourceEntity?>(null) }

    // 消息提示
    uiState.message?.let { msg ->
        LaunchedEffect(msg) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API 管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (uiState.selectedIds.isNotEmpty()) {
                        IconButton(onClick = { viewModel.testSelected() }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "批量检测")
                        }
                        IconButton(onClick = { viewModel.deleteSelected() }) {
                            Icon(Icons.Default.Delete, contentDescription = "批量删除")
                        }
                        TextButton(onClick = viewModel::clearSelection) { Text("取消") }
                    } else {
                        IconButton(onClick = { viewModel.testAll() }, enabled = !uiState.isTesting) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "一键检测全部")
                        }
                        IconButton(onClick = { showTvBoxImport = true }) {
                            Icon(Icons.Default.CloudDownload, contentDescription = "TVBox 配置导入")
                        }
                        IconButton(onClick = { showImport = true }) {
                            Icon(Icons.Default.FileUpload, contentDescription = "导入")
                        }
                        IconButton(onClick = {
                            scope.launch {
                                exportText = viewModel.exportToText()
                                showExport = true
                            }
                        }) {
                            Icon(Icons.Default.FileDownload, contentDescription = "导出")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (uiState.selectedIds.isEmpty()) {
                FloatingActionButton(onClick = { editingSource = null; showAddEdit = true }) {
                    Icon(Icons.Default.Add, contentDescription = "添加")
                }
            }
        }
    ) { padding ->
        if (sources.isEmpty()) {
            EmptyView(
                message = "暂无 API 源，点击右下角 + 添加",
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "共 ${sources.size} 个源",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::selectAll) {
                            Text(if (uiState.selectedIds.containsAll(sources.map { it.id }) && sources.isNotEmpty()) "取消全选" else "全选")
                        }
                    }
                }
                items(sources, key = { it.id }) { source ->
                    ApiSourceItem(
                        source = source,
                        selected = uiState.selectedIds.contains(source.id),
                        testResult = uiState.testResults[source.id],
                        isTesting = uiState.isTesting,
                        onToggleSelect = { viewModel.toggleSelection(source.id) },
                        onToggleEnabled = { viewModel.toggleEnabled(source) },
                        onTest = { viewModel.testApi(source) },
                        onEdit = { editingSource = source; showAddEdit = true },
                        onRequestDelete = { sourceToDelete = source }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    // 添加/编辑弹窗
    if (showAddEdit) {
        ApiEditDialog(
            source = editingSource,
            onDismiss = { showAddEdit = false },
            onSave = { name, url, note ->
                viewModel.saveSource(
                    (editingSource ?: ApiSourceEntity(name = "", url = "")).copy(
                        name = name, url = url, note = note
                    )
                )
                showAddEdit = false
            },
            onTest = { url ->
                viewModel.testApiUrl(url)
            }
        )
    }

    // 批量导入弹窗
    if (showImport) {
        ApiImportDialog(
            onDismiss = { showImport = false },
            onImport = { text, isSingle ->
                if (isSingle) {
                    text.lines().firstOrNull()?.let { line ->
                        val parts = line.split(",", "，").map { it.trim() }
                        if (parts.size >= 2) {
                            viewModel.singleImport(parts[0], parts[1], parts.getOrElse(2) { "" })
                        } else {
                            scope.launch { snackbarHostState.showSnackbar("格式错误：名称,URL") }
                        }
                    }
                } else {
                    viewModel.batchImport(text)
                }
                showImport = false
            }
        )
    }

    // 导出弹窗
    if (showExport) {
        ApiExportDialog(
            text = exportText,
            onDismiss = { showExport = false },
            onCopy = {
                clipboard.setText(AnnotatedString(exportText))
                scope.launch { snackbarHostState.showSnackbar("已复制到剪贴板") }
                showExport = false
            }
        )
    }

    // TVBox 配置导入弹窗
    if (showTvBoxImport) {
        TvBoxImportDialog(
            isLoading = uiState.tvBoxLoading,
            onDismiss = { if (!uiState.tvBoxLoading) showTvBoxImport = false },
            onImport = { url -> viewModel.importTvBoxConfig(url) },
        )
    }

    // 多仓子配置选择弹窗
    if (uiState.tvBoxWarehouses.isNotEmpty()) {
        TvBoxWarehouseDialog(
            warehouses = uiState.tvBoxWarehouses,
            onDismiss = viewModel::dismissTvBoxWarehouses,
            onPick = { name, url ->
                showTvBoxImport = false
                viewModel.importTvBoxWarehouse(url, name)
            },
        )
    }

    // 左滑删除确认
    sourceToDelete?.let { source ->
        AlertDialog(
            onDismissRequest = { sourceToDelete = null },
            title = { Text("删除 API 源") },
            text = { Text("确定删除「${source.name}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSource(source)
                        sourceToDelete = null
                    }
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { sourceToDelete = null }) { Text("取消") }
            }
        )
    }
}

/**
 * API 源条目：只显示名称与检测结果，点击进入编辑；
 * 删除按钮隐藏在最右侧，向左滑动露出，松手后弹确认。
 * 检测不可用或被禁用的源整体置灰。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiSourceItem(
    source: ApiSourceEntity,
    selected: Boolean,
    testResult: ApiSourceRepository.TestResult?,
    isTesting: Boolean,
    onToggleSelect: () -> Unit,
    onToggleEnabled: () -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            // 不直接移除：弹出确认框，条目回弹
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRequestDelete()
            }
            false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            // 红底仅在滑动时显示，并裁剪成卡片圆角，避免漏色
            val swiping = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (swiping) MaterialTheme.colorScheme.errorContainer else Color.Transparent
                    ),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (swiping) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(end = 24.dp),
                    )
                }
            }
        }
    ) {
        // 不可用（检测失败）或禁用的源置灰
        val unavailable = testResult != null && !testResult.success
        val contentAlpha = if (source.enabled && !unavailable) 1f else 0.45f
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (source.enabled) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .alpha(contentAlpha)
                .clickable(onClick = onEdit),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
                Text(
                    text = source.name,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                // 可用打钩标识
                if (testResult?.success == true) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "可用",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                // 单条检测
                IconButton(
                    onClick = onTest,
                    enabled = !isTesting,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "检测",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Switch(checked = source.enabled, onCheckedChange = { onToggleEnabled() })
            }
        }
    }
}

@Composable
private fun ApiEditDialog(
    source: ApiSourceEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String, note: String) -> Unit,
    onTest: (url: String) -> Unit
) {
    var name by remember { mutableStateOf(source?.name ?: "") }
    var url by remember { mutableStateOf(source?.url ?: "") }
    var note by remember { mutableStateOf(source?.note ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (source == null) "添加 API" else "编辑 API") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("名称") }, modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("接口地址") }, modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("备注") }, modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { onTest(url) }) { Text("检测此接口") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, url, note) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ApiImportDialog(
    onDismiss: () -> Unit,
    onImport: (text: String, isSingle: Boolean) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var isSingle by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isSingle) "单个导入" else "批量导入") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isSingle, onCheckedChange = { isSingle = it })
                    Text("单个导入模式（仅解析第一行）")
                }
                Text(
                    text = if (isSingle) "格式: 名称,URL" else "每行一条：名称,URL[,备注]；或粘贴 JSON 数组",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    label = { Text("内容") }
                )
            }
        },
        confirmButton = { TextButton(onClick = { onImport(text, isSingle) }) { Text("导入") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ApiExportDialog(
    text: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出 API 列表") },
        text = {
            Column {
                Text(
                    text = "每行格式：名称,URL,备注",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = text, onValueChange = {},
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    readOnly = true
                )
            }
        },
        confirmButton = { TextButton(onClick = onCopy) { Text("复制") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

/** 常用 TVBox 配置仓快捷入口 */
private val TVBOX_PRESETS = listOf(
    "匿名单仓" to "https://12586.kstore.space/123.txt",
    "游魂单仓" to "https://www.iyouhun.com/tv/dc",
    "无名单仓" to "https://github.catvod.com/raw.githubusercontent.com/tushen6/Tomorrow/master/lmw.json",
    "欧歌单仓" to "https://双龙.v.nxog.top/nxog/oua.php",
    "匿名多仓" to "https://12586.kstore.space/123.json",
    "饭太硬" to "http://www.饭太硬.net/tv",
    "饭太硬备用1" to "http://www.饭太硬.art/tv",
    "饭太硬备用2" to "http://fty.xxooo.cf/tv",
    "饭太硬备用3" to "http://fty.888484.xyz/tv",
    "饭太硬备用4" to "http://fty.333232.xyz/tv",
    "小米" to "https://gh-proxy.org/raw.githubusercontent.com/ggrrttyyiii/CatVodSpider/refs/heads/main/json/demo.json",
    "肥猫" to "http://肥猫.net/tv",
    "王二小" to "https://9280.kstore.vip/newwex.json",
    "嗷呜" to "https://9763.kstore.vip/aowu.json",
    "摸鱼儿" to "http://我不是.摸鱼儿.top",
    "集多" to "http://rihou.cc:88/demo.php",
    "潇洒" to "https://9877.kstore.space/one.json",
    "欧歌" to "https://xn--jory77o.v.nxog.top/m",
    "小虎斑" to "http://hb.小虎斑.site:25252/仅供测试",
    "南风" to "https://gh-proxy.com/raw.githubusercontent.com/yoursmile66/TVBox/refs/heads/main/XC.json",
    "少儿频道" to "https://gh-proxy.com/raw.githubusercontent.com/lubin776/0/refs/heads/main/tvbox/b2.json",
    "戏曲音乐" to "https://z.qiqiv.cn/666",
)

/**
 * TVBox 配置导入弹窗：输入配置仓地址（单仓/多仓均可），
 * 自动拉取并提取其中的 CMS 点播源、解析源与直播 M3U 源。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TvBoxImportDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onImport: (url: String) -> Unit,
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("TVBox 配置导入") },
        text = {
            Column {
                Text(
                    text = "填入 TVBox 单仓/多仓配置链接，自动提取其中的点播 API、" +
                        "解析接口与直播源；需要爬虫（spider）运行的源无法导入。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("配置链接") },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("常用配置", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TVBOX_PRESETS.forEach { (name, presetUrl) ->
                        Text(
                            text = name,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { url = presetUrl }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                TextButton(
                    onClick = { onImport(url) },
                    enabled = url.isNotBlank(),
                ) { Text("拉取导入") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 多仓子配置选择弹窗 */
@Composable
private fun TvBoxWarehouseDialog(
    warehouses: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onPick: (name: String, url: String) -> Unit,
) {
    var selected by remember { mutableStateOf(warehouses.firstOrNull()?.second.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择子配置") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                warehouses.forEach { (name, url) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selected = url }
                            .padding(vertical = 2.dp),
                    ) {
                        RadioButton(
                            selected = selected == url,
                            onClick = { selected = url },
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = name,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                            )
                            Text(
                                text = url,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    warehouses.firstOrNull { it.second == selected }?.let { (name, url) ->
                        onPick(name, url)
                    }
                },
                enabled = selected.isNotBlank(),
            ) { Text("导入该仓") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
