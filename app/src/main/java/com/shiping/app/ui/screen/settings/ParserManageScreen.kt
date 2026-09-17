package com.shiping.app.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiping.app.data.model.ParseSourceEntity
import com.shiping.app.ui.component.EmptyView
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParserManageScreen(
    viewModel: ParserManageViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onBack: () -> Unit,
) {
    val sources by viewModel.sources.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var showAddEdit by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<ParseSourceEntity?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf("") }

    uiState.message?.let { msg ->
        LaunchedEffect(msg) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("解析管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showImport = true }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "导入")
                    }
                    IconButton(onClick = {
                        scope.launch {
                            exportText = viewModel.exportText()
                            showExport = true
                        }
                    }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "导出")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editingSource = null; showAddEdit = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加")
            }
        },
    ) { padding ->
        if (sources.isEmpty()) {
            EmptyView(
                message = "暂无解析源，点击右下角 + 添加",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        text = "解析地址格式: 解析URL + 视频URL，非直链播放时自动拼接",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                items(sources, key = { it.id }) { source ->
                    ParserSourceItem(
                        source = source,
                        isSelected = source.id == uiState.selectedParserId,
                        onSetCurrent = { viewModel.setCurrentParser(source.id) },
                        onToggleEnabled = { viewModel.toggleEnabled(source) },
                        onEdit = { editingSource = source; showAddEdit = true },
                        onDelete = { viewModel.deleteSource(source) },
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    if (showAddEdit) {
        ParserEditDialog(
            source = editingSource,
            onDismiss = { showAddEdit = false },
            onSave = { name, url, note ->
                viewModel.saveSource(
                    (editingSource ?: ParseSourceEntity(name = "", url = "")).copy(
                        name = name, url = url, note = note,
                    ),
                )
                showAddEdit = false
            },
        )
    }

    if (showImport) {
        ParserImportDialog(
            onDismiss = { showImport = false },
            onImport = { text ->
                viewModel.importSources(text)
                showImport = false
            },
        )
    }

    if (showExport) {
        ParserExportDialog(
            text = exportText,
            onDismiss = { showExport = false },
            onCopy = {
                clipboard.setText(AnnotatedString(exportText))
                scope.launch { snackbarHostState.showSnackbar("已复制到剪贴板") }
                showExport = false
            },
        )
    }
}

@Composable
private fun ParserSourceItem(
    source: ParseSourceEntity,
    isSelected: Boolean,
    onSetCurrent: () -> Unit,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = source.name,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(14.dp).height(14.dp),
                            )
                        }
                    }
                    Text(
                        text = source.url,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = source.enabled, onCheckedChange = { onToggleEnabled() })
            }
            if (source.note.isNotBlank()) {
                Text(
                    text = "备注: ${source.note}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onSetCurrent) {
                    Text(if (isSelected) "当前使用" else "设为默认", fontSize = 12.sp)
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
private fun ParserEditDialog(
    source: ParseSourceEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String, note: String) -> Unit,
) {
    var name by remember { mutableStateOf(source?.name ?: "") }
    var url by remember { mutableStateOf(source?.url ?: "") }
    var note by remember { mutableStateOf(source?.note ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (source == null) "添加解析源" else "编辑解析源") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("名称") }, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("解析地址") },
                    placeholder = { Text("https://yparse.ik9.cc/index.php?url=") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("备注") }, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "播放时，非直链地址将自动拼接此URL",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, url, note) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ParserImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量导入解析源") },
        text = {
            Column {
                Text(
                    text = "每行一条：名称,URL[,备注]；或粘贴 JSON 数组",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    label = { Text("内容") },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onImport(text) }) { Text("导入") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ParserExportDialog(
    text: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出解析源") },
        text = {
            Column {
                Text(
                    text = "每行格式：名称,URL,备注",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = text, onValueChange = {},
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    readOnly = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = onCopy) { Text("复制") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
