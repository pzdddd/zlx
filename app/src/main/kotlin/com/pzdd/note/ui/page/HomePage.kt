package com.pzdd.note.ui.page
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.pzdd.note.data.Note
import com.pzdd.note.data.NoteMode
import com.pzdd.note.ui.NoteViewModel
import com.pzdd.note.ui.copyToClipboard

@Composable
fun HomePage(
    vm: NoteViewModel,
    paddingValues: PaddingValues,
    floatingBottomBar: Boolean = false,
    bottomBarVisible: Boolean = false,
    onScrollDirectionChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val allNotes by vm.notes.collectAsState()

    // 顶部 Tab：0 = 普通模式，1 = 深度模式
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("普通模式", "深度模式")

    // 按模式过滤：普通模式和深度模式相互独立，互不同步
    val notes by remember(allNotes) {
        derivedStateOf {
            if (selectedTab == 0) allNotes.filter { it.mode == NoteMode.NORMAL.value }
            else allNotes.filter { it.mode == NoteMode.DEEP.value }
        }
    }
    var showAdd by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<Note?>(null) }
    var actionNote by remember { mutableStateOf<Note?>(null) }
    // 深度模式专用状态
    var deepAddNote by remember { mutableStateOf(false) }
    var deepEditingNote by remember { mutableStateOf<Note?>(null) }

    // 滚动状态：检测滚动方向以驱动悬浮底栏的显示/隐藏
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        var prevIndex = listState.firstVisibleItemIndex
        var prevOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            // 只在有笔记时才检测滚动方向
            if (notes.isNotEmpty()) {
                val isScrollingUp = when {
                    index > prevIndex -> true
                    index < prevIndex -> false
                    else -> offset > prevOffset  // 同一 item 内，offset 增大 = 向上滚动
                }
                onScrollDirectionChanged(isScrollingUp)
            }
            prevIndex = index
            prevOffset = offset
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        // 顶部双文字 Tab
        ModeTabRow(
            tabs = tabs,
            selectedIndex = selectedTab,
            onTabSelected = { selectedTab = it }
        )

        Box(modifier = Modifier.fillMaxSize()) {
        if (selectedTab == 0) {
            // 普通模式：笔记列表
            if (notes.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "还没有笔记",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "点击右下角按钮添加",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onClick = { editingNote = note },
                            onLongClick = { actionNote = note },
                            onCopyContent = {
                                copyToClipboard(context, "内容", note.content)
                                Toast.makeText(context, "已复制内容", Toast.LENGTH_SHORT).show()
                            },
                            onCopyTitle = {
                                copyToClipboard(context, "标题", note.title)
                                Toast.makeText(context, "已复制标题", Toast.LENGTH_SHORT).show()
                            },
                            onToggleFavorite = {
                                vm.toggleFavorite(note)
                            },
                            onDelete = {
                                vm.deleteNote(note)
                                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        } else {
            // 深度模式：命令笔记列表
            if (notes.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "还没有命令笔记",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "点击右下角按钮添加",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        DeepNoteCard(
                            note = note,
                            onToggleFavorite = { vm.toggleFavorite(note) },
                            onDelete = {
                                vm.deleteNote(note)
                                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                            },
                            onRename = { newTitle -> vm.updateNoteTitle(note, newTitle) },
                            onAddCommand = { cmd -> vm.addCommand(note, cmd) },
                            onUpdateCommand = { idx, cmd -> vm.updateCommand(note, idx, cmd) },
                            onDeleteCommand = { idx -> vm.deleteCommand(note, idx) },
                            onMoveCommand = { idx, up -> vm.moveCommand(note, idx, up) },
                            onCopyCommand = { text ->
                                copyToClipboard(context, "命令", text)
                                Toast.makeText(context, "已复制命令", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        // FAB 固定位置，不随底栏显示/隐藏而移动
        FloatingActionButton(
            onClick = { if (selectedTab == 0) showAdd = true else deepAddNote = true },
            shape = androidx.compose.foundation.shape.CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 140.dp)
                .size(56.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "添加")
        }
        } // Box
    } // Column

    if (showAdd) {
        NoteEditDialog(
            title = "新建笔记",
            initialTitle = "",
            initialContent = "",
            onDismiss = { showAdd = false },
            onConfirm = { t, c ->
                vm.addNote(t, c, NoteMode.NORMAL)
                showAdd = false
                Toast.makeText(context, "已添加", Toast.LENGTH_SHORT).show()
            }
        )
    }

    editingNote?.let { note ->
        NoteEditDialog(
            title = "编辑笔记",
            initialTitle = note.title,
            initialContent = note.content,
            onDismiss = { editingNote = null },
            onConfirm = { t, c ->
                vm.updateNote(note, t, c)
                editingNote = null
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 深度模式：新建命令笔记
    if (deepAddNote) {
        NoteEditDialog(
            title = "新建命令笔记",
            initialTitle = "",
            initialContent = "",
            onDismiss = { deepAddNote = false },
            onConfirm = { t, c ->
                vm.addNote(t, c, NoteMode.DEEP)
                deepAddNote = false
                Toast.makeText(context, "已添加", Toast.LENGTH_SHORT).show()
            }
        )
    }

    actionNote?.let { note ->
        NoteActionSheet(
            note = note,
            onDismiss = { actionNote = null },
            onCopyTitle = {
                copyToClipboard(context, "标题", note.title)
                Toast.makeText(context, "已复制标题", Toast.LENGTH_SHORT).show()
                actionNote = null
            },
            onCopyContent = {
                copyToClipboard(context, "内容", note.content)
                Toast.makeText(context, "已复制内容", Toast.LENGTH_SHORT).show()
                actionNote = null
            },
            onToggleFavorite = {
                vm.toggleFavorite(note)
                actionNote = null
            },
            onDelete = {
                vm.deleteNote(note)
                actionNote = null
                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCopyContent: () -> Unit,
    onCopyTitle: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.title.isNotBlank()) {
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = "(无标题)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (note.isFavorite) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (note.content.isNotBlank()) {
                Spacer(Modifier.size(6.dp))
                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 分隔线
            Spacer(Modifier.size(8.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(Modifier.size(6.dp))

            // 操作按钮行：复制（左）、收藏（中）、删除（右）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(48.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 复制按钮：单击复制内容，长按复制标题
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.combinedClickable(
                        onClick = onCopyContent,
                        onLongClick = onCopyTitle
                    )
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "复制",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "复制",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 收藏按钮
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.combinedClickable(onClick = onToggleFavorite)
                ) {
                    Icon(
                        if (note.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = "收藏",
                        tint = if (note.isFavorite) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "收藏",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (note.isFavorite) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 删除按钮
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.combinedClickable(onClick = onDelete)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "删除",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ===== 深度模式：命令笔记卡片 =====

@Composable
private fun DeepNoteCard(
    note: Note,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onAddCommand: (String) -> Unit,
    onUpdateCommand: (Int, String) -> Unit,
    onDeleteCommand: (Int) -> Unit,
    onMoveCommand: (Int, Boolean) -> Unit,
    onCopyCommand: (String) -> Unit
) {
    val commands = remember(note.content) {
        if (note.content.isBlank()) emptyList() else note.content.split("\n")
    }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newCommandText by remember { mutableStateOf("") }
    var editingIndex by remember { mutableStateOf(-1) }
    var editingText by remember { mutableStateOf("") }

    val dateFormatter = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 标题行 + 操作按钮
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = note.title.ifBlank { "(无标题)" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (note.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = "收藏",
                        tint = if (note.isFavorite) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = { showRenameDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "重命名",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = "更新: ${dateFormatter.format(java.util.Date(note.updatedAt))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.size(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.size(8.dp))

            // 命令行列表（可滚动）
            if (commands.isEmpty()) {
                Text(
                    text = "暂无命令，在下方添加",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    commands.forEachIndexed { index, cmd ->
                        CommandRow(
                            index = index,
                            text = cmd,
                            total = commands.size,
                            isEditing = editingIndex == index,
                            editingText = editingText,
                            onStartEdit = {
                                editingIndex = index
                                editingText = cmd
                            },
                            onTextChange = { editingText = it },
                            onDoneEdit = {
                                onUpdateCommand(index, editingText)
                                editingIndex = -1
                            },
                            onCancelEdit = { editingIndex = -1 },
                            onCopy = { onCopyCommand(cmd) },
                            onDelete = { onDeleteCommand(index) },
                            onMoveUp = { onMoveCommand(index, true) },
                            onMoveDown = { onMoveCommand(index, false) }
                        )
                        if (index < commands.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.size(8.dp))

            // 添加新命令行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = newCommandText,
                    onValueChange = { newCommandText = it },
                    placeholder = { Text("输入命令...", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.weight(1f),
                    singleLine = false,
                    maxLines = 3,
                    textStyle = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (newCommandText.isNotBlank()) {
                            onAddCommand(newCommandText.trim())
                            newCommandText = ""
                        }
                    }
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "添加命令",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        RenameDialog(
            initialTitle = note.title,
            onDismiss = { showRenameDialog = false },
            onConfirm = {
                onRename(it)
                showRenameDialog = false
            }
        )
    }
}

@Composable
private fun CommandRow(
    index: Int,
    text: String,
    total: Int,
    isEditing: Boolean,
    editingText: String,
    onStartEdit: () -> Unit,
    onTextChange: (String) -> Unit,
    onDoneEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "${index + 1}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, end = 4.dp)
        )
        if (isEditing) {
            OutlinedTextField(
                value = editingText,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                singleLine = false,
                maxLines = 5,
                textStyle = MaterialTheme.typography.bodySmall
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(onClick = onStartEdit, onLongClick = onStartEdit)
                    .padding(top = 4.dp, end = 4.dp)
            )
        }
        if (isEditing) {
            IconButton(onClick = onDoneEdit, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Check, contentDescription = "完成", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onCancelEdit, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "取消", modifier = Modifier.size(16.dp))
            }
        } else {
            Column {
                IconButton(
                    onClick = onMoveUp,
                    enabled = index > 0,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移", modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = index < total - 1,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移", modifier = Modifier.size(16.dp))
                }
            }
            IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "复制",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名", style = MaterialTheme.typography.titleMedium) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 顶部横向双文字 Tab：普通模式 / 深度模式
 * 使用药丸形背景指示当前选中项，点击切换。
 */
@Composable
private fun ModeTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = index == selectedIndex
                // 选中项背景宽度动画
                val indicatorOffset by animateDpAsState(
                    targetValue = if (isSelected) 0.dp else 0.dp,
                    label = "tabIndicator"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .combinedClickable(onClick = { onTabSelected(index) }),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {}
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}