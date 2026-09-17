package com.shiping.app.ui.screen.settings

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiping.app.data.model.TvSourceEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvSourceManageScreen(
    onBack: () -> Unit,
    viewModel: TvSourceManageViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val sources by viewModel.sources.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    var editingSource by remember { mutableStateOf<TvSourceEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deletingSource by remember { mutableStateOf<TvSourceEntity?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf("") }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("电视直播源") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.testAll() },
                        enabled = !uiState.isTesting,
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "一键检测全部")
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加直播源")
            }
        },
    ) { padding ->
        if (sources.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.LiveTv,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "暂无直播源，点击右下角添加",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sources, key = { it.id }) { source ->
                    SourceCard(
                        source = source,
                        testOk = uiState.testResults[source.id],
                        isTesting = uiState.isTesting,
                        onEdit = { editingSource = source },
                        onRequestDelete = { deletingSource = source },
                        onToggleEnabled = { viewModel.toggleEnabled(source) },
                        onTest = { viewModel.testSource(source) },
                    )
                }
            }
        }
    }

    // 添加 / 编辑弹窗
    if (showAddDialog || editingSource != null) {
        val target = editingSource
        SourceEditDialog(
            initial = target,
            onDismiss = {
                showAddDialog = false
                editingSource = null
            },
            onConfirm = { name, url, note ->
                viewModel.saveSource(target?.id ?: 0L, name, url, note)
                showAddDialog = false
                editingSource = null
            },
        )
    }

    // 删除确认
    deletingSource?.let { source ->
        AlertDialog(
            onDismissRequest = { deletingSource = null },
            title = { Text("删除直播源") },
            text = { Text("确定删除「${source.name}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSource(source)
                    deletingSource = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deletingSource = null }) { Text("取消") }
            },
        )
    }

    // 批量导入
    if (showImport) {
        var importText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("批量导入直播源") },
            text = {
                Column {
                    Text(
                        text = "每行一条：名称,URL[,备注]；或粘贴 JSON 数组",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        label = { Text("内容") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.batchImport(importText)
                    showImport = false
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showImport = false }) { Text("取消") }
            },
        )
    }

    // 导出（文本格式，可切换 JSON）
    if (showExport) {
        var asJson by remember { mutableStateOf(false) }
        LaunchedEffect(asJson) {
            exportText = if (asJson) viewModel.exportToJson() else viewModel.exportToText()
        }
        AlertDialog(
            onDismissRequest = { showExport = false },
            title = { Text("导出直播源") },
            text = {
                Column {
                    Row {
                        TextButton(onClick = { asJson = false }) {
                            Text("文本", fontWeight = if (!asJson) FontWeight.Bold else FontWeight.Normal)
                        }
                        TextButton(onClick = { asJson = true }) {
                            Text("JSON", fontWeight = if (asJson) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = exportText,
                        onValueChange = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        readOnly = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(exportText))
                    scope.launch { snackbarHostState.showSnackbar("已复制到剪贴板") }
                    showExport = false
                }) { Text("复制") }
            },
            dismissButton = {
                TextButton(onClick = { showExport = false }) { Text("关闭") }
            },
        )
    }
}

/**
 * 直播源条目：只显示名称与检测结果，点击进入编辑；
 * 删除按钮隐藏在最右侧，向左滑动露出，松手后弹确认。
 * 检测不可用或被禁用的源整体置灰。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceCard(
    source: TvSourceEntity,
    testOk: Boolean?,
    isTesting: Boolean,
    onEdit: () -> Unit,
    onRequestDelete: () -> Unit,
    onToggleEnabled: () -> Unit,
    onTest: () -> Unit,
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
        val unavailable = testOk == false
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (source.enabled && !unavailable) 1f else 0.45f)
                .clickable(onClick = onEdit),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (source.enabled) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                },
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // 可用打钩标识
                if (testOk == true) {
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
                Switch(
                    checked = source.enabled,
                    onCheckedChange = { onToggleEnabled() },
                )
            }
        }
    }
}

@Composable
private fun SourceEditDialog(
    initial: TvSourceEntity?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String, note: String) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加直播源" else "编辑直播源") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("M3U 播放列表地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, url, note) }) {
                Text(if (initial == null) "添加" else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
