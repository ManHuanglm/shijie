package com.shiping.app.ui.screen.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiping.app.data.model.ApiSourceEntity
import com.shiping.app.data.repository.ApiSourceRepository
import com.shiping.app.di.AppContainer
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
                        onDelete = { viewModel.deleteSource(source) }
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
                scope.launch {
                    val result = AppContainer.apiSourceRepository.testApi(url)
                    viewModel.showTempMessage(if (result.success) "检测成功" else result.message)
                }
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
                            viewModel.showTempMessage("格式错误：名称,URL")
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
                viewModel.showTempMessage("已复制到剪贴板")
                showExport = false
            }
        )
    }
}

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
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = source.name,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = source.url,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(checked = source.enabled, onCheckedChange = { onToggleEnabled() })
            }
            if (source.note.isNotBlank()) {
                Text(
                    text = "备注: ${source.note}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
            }
            testResult?.let { result ->
                Text(
                    text = result.message,
                    fontSize = 11.sp,
                    color = if (result.success) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onTest, enabled = !isTesting) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.width(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("检测", fontSize = 12.sp)
                }
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.width(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("编辑", fontSize = 12.sp)
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.width(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("删除", fontSize = 12.sp)
                }
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
