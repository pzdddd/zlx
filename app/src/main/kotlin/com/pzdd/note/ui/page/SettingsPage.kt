package com.pzdd.note.ui.page

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pzdd.note.data.ThemeMode
import com.pzdd.note.ui.NoteViewModel
import com.pzdd.note.ui.SettingsViewModel
import com.pzdd.note.ui.collectAsStateSafe
import com.pzdd.note.ui.theme.ThemeColorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsPage(
    vm: SettingsViewModel,
    noteVm: NoteViewModel,
    paddingValues: PaddingValues
) {
    val settings by vm.settings.collectAsStateSafe()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(paddingValues)
            .padding(16.dp)
            // 悬浮底栏不占 Scaffold 布局空间，需额外预留底部高度避免最后一栏被遮挡
            .padding(
                bottom = if (settings.floatingBottomBar) 120.dp else 0.dp
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ===== 主题模式 =====
        SettingsSectionCard(title = "主题模式") {
            ThemeModePickerRow(
                currentMode = settings.themeMode,
                onSelect = { vm.setThemeMode(it) }
            )
        }

        // ===== 主题颜色 =====
        SettingsSectionCard(title = "主题颜色") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(ThemeColorOptions) { option ->
                    SelectableRow(
                        title = option.name,
                        color = option.lightPrimary,
                        selected = settings.themeColorKey == option.key,
                        onClick = { vm.setThemeColorKey(option.key) }
                    )
                }
            }
        }

        // ===== 底栏设置 =====
        SettingsSectionCard(title = "底栏设置") {
            SwitchRow(
                title = "悬浮分页底栏",
                subtitle = "底栏悬浮于内容上方，圆角胶囊样式",
                checked = settings.floatingBottomBar,
                onCheckedChange = { vm.setFloatingBottomBar(it) }
            )
            SwitchRow(
                title = "液态玻璃效果",
                subtitle = "半透明毛玻璃质感的底栏背景",
                checked = settings.liquidGlassBottomBar,
                enabled = settings.floatingBottomBar,
                onCheckedChange = { vm.setLiquidGlassBottomBar(it) }
            )
        }

        // ===== 备份与导入 =====
        BackupSection(noteVm = noteVm)

        // ===== 关于 =====
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "UI 框架：Jetpack Compose Material3",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "一款简洁的记事本应用，支持主题切换与液态玻璃底栏",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}
@Composable
private fun SelectableRow(
    title: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "已选择",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (enabled) 1f else 0.4f
                    )
                )
            }
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ThemeModePickerRow(
    currentMode: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "主题模式", style = MaterialTheme.typography.bodyLarge)
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentMode.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp
                    else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 点击后在卡片内直接展开列表，选中后自动收回
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onSelect(mode)
                                expanded = false
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (mode == currentMode)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                        if (mode == currentMode) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    // 分隔线（最后一项不显示）
                    if (index < ThemeMode.entries.size - 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(0.5.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                )
                        )
                    }
                }
            }
        }
    }
}

// ==================== 备份与导入 ====================

@Composable
private fun BackupSection(noteVm: NoteViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var exportNormal by remember { mutableStateOf(true) }
    var exportDeep by remember { mutableStateOf(true) }
    var exportFavorites by remember { mutableStateOf(true) }
    var exportExpanded by remember { mutableStateOf(false) }
    var importExpanded by remember { mutableStateOf(false) }
    var showImportStrategyDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val scopes = mutableSetOf<NoteViewModel.BackupScope>()
            if (exportNormal) scopes.add(NoteViewModel.BackupScope.NORMAL)
            if (exportDeep) scopes.add(NoteViewModel.BackupScope.DEEP)
            if (exportFavorites) scopes.add(NoteViewModel.BackupScope.FAVORITES)
            scope.launch(Dispatchers.IO) {
                val ok = noteVm.writeBackupToUri(uri, scopes)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, if (ok) "备份导出成功" else "备份导出失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showImportStrategyDialog = true
        }
    }

    SettingsSectionCard(title = "备份与导入") {
        // ---- 导出 ----
        BackupExpandHeader(
            icon = Icons.Filled.Upload,
            title = "备份导出",
            expanded = exportExpanded,
            onClick = { exportExpanded = !exportExpanded }
        )
        AnimatedVisibility(visible = exportExpanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp)) {
                Text("选择备份范围", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp))
                BackupCheckboxRow("普通模式笔记", exportNormal) { exportNormal = it }
                BackupCheckboxRow("多列模式笔记", exportDeep) { exportDeep = it }
                BackupCheckboxRow("收藏列表", exportFavorites) { exportFavorites = it }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End) {
                    val canExport = exportNormal || exportDeep || exportFavorites
                    BackupActionButton(
                        text = "导出",
                        enabled = canExport,
                        onClick = {
                            exportLauncher.launch("pznote_backup_${System.currentTimeMillis()}.json")
                        }
                    )
                }
            }
        }
        // 分隔线
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
        // ---- 导入 ----
        BackupExpandHeader(
            icon = Icons.Filled.Download,
            title = "备份导入",
            expanded = importExpanded,
            onClick = { importExpanded = !importExpanded }
        )
        AnimatedVisibility(visible = importExpanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp)) {
                Text("选择 JSON 备份文件导入，导入时可选择冲突处理方式",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    BackupActionButton(text = "选择文件导入", enabled = true) {
                        importLauncher.launch(arrayOf("application/json", "*/*"))
                    }
                }
            }
        }
    }

    // 导入策略选择弹窗
    if (showImportStrategyDialog && pendingImportUri != null) {
        ImportStrategyDialog(
            onDismiss = { showImportStrategyDialog = false; pendingImportUri = null },
            onStrategy = { strategy ->
                val uri = pendingImportUri
                showImportStrategyDialog = false
                pendingImportUri = null
                if (uri != null) {
                    scope.launch(Dispatchers.IO) {
                        val count = noteVm.importFromUri(uri, strategy)
                        withContext(Dispatchers.Main) {
                            val msg = when {
                                count < 0 -> "导入失败：文件格式错误"
                                strategy == NoteViewModel.ImportStrategy.SKIP -> "导入完成：新增 $count 条（跳过已存在）"
                                strategy == NoteViewModel.ImportStrategy.DUPLICATE -> "导入完成：新增 $count 条（作为副本）"
                                else -> "导入完成：处理 $count 条（覆盖同 ID）"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun BackupExpandHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 8.dp))
        }
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp
            else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun BackupCheckboxRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (checked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
                .clickable { onCheckedChange(!checked) },
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(Icons.Filled.Check, contentDescription = null,
                    tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun BackupActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp)
    ) {
        Text(text, fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun ImportStrategyDialog(
    onDismiss: () -> Unit,
    onStrategy: (NoteViewModel.ImportStrategy) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入冲突处理") },
        text = {
            Column {
                Text("检测到已存在的笔记时，如何处理？",
                    style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = { onStrategy(NoteViewModel.ImportStrategy.SKIP) }) {
                Text("跳过已存在")
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = { onStrategy(NoteViewModel.ImportStrategy.DUPLICATE) }) {
                    Text("全部导入（允许重复）")
                }
                TextButton(onClick = { onStrategy(NoteViewModel.ImportStrategy.OVERWRITE) }) {
                    Text("覆盖同 ID")
                }
            }
        }
    )
}