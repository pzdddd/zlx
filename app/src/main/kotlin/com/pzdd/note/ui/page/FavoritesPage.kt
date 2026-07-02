package com.pzdd.note.ui.page

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pzdd.note.data.Note
import com.pzdd.note.data.NoteMode
import com.pzdd.note.ui.NoteViewModel
import com.pzdd.note.ui.copyToClipboard

@Composable
fun FavoritesPage(
    vm: NoteViewModel,
    paddingValues: PaddingValues,
    onScrollDirectionChanged: (Boolean) -> Unit = {}
) {
    val allNotes by vm.notes.collectAsState()
    val context = LocalContext.current

    // 顶部 Tab：0 = 普通模式收藏，1 = 深度模式收藏
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("普通模式", "深度模式")

    // 搜索关键词
    var searchQuery by remember { mutableStateOf("") }

    // 按模式过滤收藏笔记，并应用搜索
    val favorites by remember(allNotes, selectedTab, searchQuery) {
        derivedStateOf {
            val modeValue = if (selectedTab == 0) NoteMode.NORMAL.value else NoteMode.DEEP.value
            val modeFav = allNotes.filter { it.isFavorite && it.mode == modeValue }
            if (searchQuery.isBlank()) modeFav
            else modeFav.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.content.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    var editingNote by remember { mutableStateOf<Note?>(null) }
    var actionNote by remember { mutableStateOf<Note?>(null) }

    // 滚动状态：检测滚动方向以驱动悬浮底栏的显示/隐藏
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(listState) {
        var prevIndex = listState.firstVisibleItemIndex
        var prevOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            if (favorites.isNotEmpty()) {
                val isScrollingUp = when {
                    index > prevIndex -> true
                    index < prevIndex -> false
                    else -> offset > prevOffset
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
        FavModeTabRow(
            tabs = tabs,
            selectedIndex = selectedTab,
            onTabSelected = { selectedTab = it }
        )

        // 搜索框
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    if (selectedTab == 0) "搜索收藏笔记..." else "搜索收藏父笔记...",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "清除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Box(modifier = Modifier.fillMaxSize()) {
            if (favorites.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "还没有收藏",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "在${tabs[selectedTab]}中收藏的笔记会显示在这里",
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
                    items(favorites, key = { it.id }) { note ->
                        if (selectedTab == 0) {
                            FavCard(
                                note = note,
                                onClick = { editingNote = note },
                                onLongClick = { actionNote = note }
                            )
                        } else {
                            FavDeepCard(
                                note = note,
                                children = allNotes.filter { it.parentId == note.id },
                                onToggleFavorite = { vm.toggleFavorite(note) },
                                onDelete = {
                                    vm.deleteNote(note)
                                    Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                                },
                                onRename = { newTitle -> vm.updateNoteTitle(note, newTitle) },
                                onAddChild = { t, c ->
                                    vm.addNote(t, c, NoteMode.DEEP, parentId = note.id)
                                },
                                onUpdateChild = { child, t, c -> vm.updateNote(child, t, c) },
                                onDeleteChild = { child -> vm.deleteNote(child) },
                                onCopyChild = { text ->
                                    copyToClipboard(context, "内容", text)
                                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
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
            onTogglePin = {
                vm.togglePin(note)
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
private fun FavCard(
    note: Note,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = note.title.ifBlank { "(无标题)" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
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
        }
    }
}

/**
 * 深度模式收藏卡片（可折叠，展开后显示子笔记）
 */
@Composable
private fun FavDeepCard(
    note: Note,
    children: List<Note>,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onAddChild: (String, String) -> Unit,
    onUpdateChild: (Note, String, String) -> Unit,
    onDeleteChild: (Note) -> Unit,
    onCopyChild: (String) -> Unit
) {
    var expanded by rememberSaveable(note.id) { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showAddChild by remember { mutableStateOf(false) }
    var editingChild by remember { mutableStateOf<Note?>(null) }

    val dateFormatter = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = { expanded = !expanded })
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowDown
                                  else Icons.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) "折叠" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = note.title.ifBlank { "(无标题)" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (children.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = "${children.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "取消收藏",
                        tint = MaterialTheme.colorScheme.primary,
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

            if (expanded) {
                Spacer(Modifier.size(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.size(8.dp))

                if (children.isEmpty()) {
                    Text(
                        text = "暂无子笔记，点击下方添加",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    children.forEachIndexed { index, child ->
                        FavDeepChildRow(
                            index = index,
                            child = child,
                            onEdit = { editingChild = child },
                            onCopy = { onCopyChild(child.content) },
                            onDelete = { onDeleteChild(child) }
                        )
                        if (index < children.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.size(8.dp))

                OutlinedButton(
                    onClick = { showAddChild = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("添加子笔记", style = MaterialTheme.typography.labelLarge)
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

    if (showAddChild) {
        NoteEditDialog(
            title = "新建子笔记",
            initialTitle = "",
            initialContent = "",
            onDismiss = { showAddChild = false },
            onConfirm = { t, c ->
                onAddChild(t, c)
                showAddChild = false
            }
        )
    }

    editingChild?.let { child ->
        NoteEditDialog(
            title = "编辑子笔记",
            initialTitle = child.title,
            initialContent = child.content,
            onDismiss = { editingChild = null },
            onConfirm = { t, c ->
                onUpdateChild(child, t, c)
                editingChild = null
            }
        )
    }
}

@Composable
private fun FavDeepChildRow(
    index: Int,
    child: Note,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
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
        Column(
            modifier = Modifier
                .weight(1f)
                .combinedClickable(onClick = onEdit, onLongClick = onEdit)
                .padding(top = 2.dp, end = 4.dp)
        ) {
            if (child.title.isNotBlank()) {
                Text(
                    text = child.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (child.content.isNotBlank()) {
                Text(
                    text = child.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "编辑",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
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

/**
 * 顶部横向双文字 Tab：普通模式 / 深度模式
 */
@Composable
private fun FavModeTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val tabCount = tabs.size
    var dragOffset by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .pointerInput(tabCount) {
                    val tabWidthPx = size.width.toFloat() / tabCount
                    detectHorizontalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            val threshold = tabWidthPx / 2f
                            when {
                                dragOffset > threshold && selectedIndex > 0 ->
                                    onTabSelected(selectedIndex - 1)
                                dragOffset < -threshold && selectedIndex < tabCount - 1 ->
                                    onTabSelected(selectedIndex + 1)
                            }
                            dragOffset = 0f
                        },
                        onDragCancel = {
                            isDragging = false
                            dragOffset = 0f
                        }
                    ) { _, dragAmount ->
                        dragOffset += dragAmount
                    }
                }
        ) {
            val tabWidth = maxWidth / tabCount
            val indicatorOffset by animateDpAsState(
                targetValue = tabWidth * selectedIndex +
                        if (isDragging) with(density) { dragOffset.toDp() } else 0.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "favIndicatorOffset"
            )

            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .offset(x = indicatorOffset + 4.dp, y = 4.dp)
                    .size(width = tabWidth - 8.dp, height = 36.dp)
            ) {}

            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = index == selectedIndex
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "favTabTextColor$index"
                    )
                    val textScale by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0.92f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "favTabTextScale$index"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .combinedClickable(onClick = { onTabSelected(index) }),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = textColor,
                            modifier = Modifier.graphicsLayer {
                                scaleX = textScale
                                scaleY = textScale
                            }
                        )
                    }
                }
            }
        }
    }
}