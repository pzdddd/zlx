package com.pzdd.note.ui.page
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
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

    // 搜索关键词（普通模式和深度模式共用）
    var searchQuery by remember { mutableStateOf("") }

    // 按模式过滤：普通模式和深度模式相互独立，互不同步
    val modeNotes by remember(allNotes) {
        derivedStateOf {
            if (selectedTab == 0) allNotes.filter { it.mode == NoteMode.NORMAL.value }
            else allNotes.filter { it.mode == NoteMode.DEEP.value }
        }
    }
    // 普通模式：应用搜索过滤
    // 深度模式：只显示父笔记（parentId == -1），并应用搜索过滤
    val notes = if (selectedTab == 0) {
        val filtered = if (searchQuery.isBlank()) modeNotes
        else modeNotes.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.content.contains(searchQuery, ignoreCase = true)
        }
        // 置顶笔记排在前面
        filtered.sortedByDescending { it.isPinned }
    } else {
        val parents = modeNotes.filter { it.parentId == -1L }
        if (searchQuery.isBlank()) parents
        else parents.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.content.contains(searchQuery, ignoreCase = true)
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
    // 深度模式列表也有独立的滚动状态
    val deepListState = rememberLazyListState()

    // 将滚动方向检测逻辑提取为可复用的函数
    suspend fun trackScrollDirection(
        state: androidx.compose.foundation.lazy.LazyListState
    ) {
        var prevIndex = state.firstVisibleItemIndex
        var prevOffset = state.firstVisibleItemScrollOffset
        snapshotFlow {
            state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
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

    // 普通模式滚动检测
    LaunchedEffect(listState) {
        trackScrollDirection(listState)
    }
    // 深度模式滚动检测
    LaunchedEffect(deepListState) {
        trackScrollDirection(deepListState)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        // 软件名称
        Text(
            text = "PZ-NOTE",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)
        )

        // 顶部双文字 Tab
        ModeTabRow(
            tabs = tabs,
            selectedIndex = selectedTab,
            onTabSelected = { selectedTab = it }
        )

        // 搜索框（普通模式和深度模式共用）
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    if (selectedTab == 0) "搜索笔记..." else "搜索父笔记...",
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
            // 深度模式：可折叠父笔记列表
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
                // ================================================================
                // 深度模式：可拖动排序的父笔记列表
                // ================================================================
                // 拖拽状态：当前被拖拽条目在列表中的索引，-1 表示无拖拽
                var deepDragIndex by remember { mutableIntStateOf(-1) }
                // 被拖拽条目的累计 Y 轴偏移量（像素），仅纵向，横向已锁定
                var deepDragOffset by remember { mutableStateOf(0f) }
                // 每个卡片大致高度（含间距），用于计算交换阈值
                val cardHeightPx = with(LocalDensity.current) { (90.dp).toPx() }
                // 条目交换阈值：拖拽超过半张卡片高度时与相邻条目换位
                val swapThreshold = cardHeightPx * 0.5f
                // 边缘自动滚动触发区域宽度（像素）
                val edgeScrollZone = with(LocalDensity.current) { (80.dp).toPx() }
                val coroutineScope = rememberCoroutineScope()
                // 边缘自动滚动的协程任务，null 表示未在滚动
                var autoScrollJob by remember { mutableStateOf<Job?>(null) }

                /**
                 * 启动或更新边缘自动滚动。
                 * 根据 draggedItemTop / draggedItemBottom 相对视口的位置，
                 * 动态计算滚动速度：越靠近边缘速度越快，离开边缘则停止。
                 */
                fun updateAutoScroll(
                    draggedItemTop: Float,
                    draggedItemBottom: Float,
                    viewportStart: Float,
                    viewportEnd: Float,
                    currentIndex: Int,
                    lastIndex: Int
                ) {
                    when {
                        // 拖拽条目顶部接近视口上边缘 → 向上滚动
                        draggedItemTop < viewportStart + edgeScrollZone && currentIndex > 0 -> {
                            // 距离边缘越近，速度越快（最大 cardHeightPx * 0.2f / 帧）
                            val distance = (draggedItemTop - viewportStart).coerceAtLeast(0f)
                            val speed = (edgeScrollZone - distance).coerceIn(0f, edgeScrollZone)
                            val scrollPerFrame = (speed / edgeScrollZone) * cardHeightPx * 0.2f
                            if (autoScrollJob == null) {
                                autoScrollJob = coroutineScope.launch {
                                    while (true) {
                                        deepListState.scrollBy(-scrollPerFrame)
                                        delay(16)
                                    }
                                }
                            }
                        }
                        // 拖拽条目底部接近视口下边缘 → 向下滚动
                        draggedItemBottom > viewportEnd - edgeScrollZone && currentIndex < lastIndex -> {
                            val distance = (viewportEnd - draggedItemBottom).coerceAtLeast(0f)
                            val speed = (edgeScrollZone - distance).coerceIn(0f, edgeScrollZone)
                            val scrollPerFrame = (speed / edgeScrollZone) * cardHeightPx * 0.2f
                            if (autoScrollJob == null) {
                                autoScrollJob = coroutineScope.launch {
                                    while (true) {
                                        deepListState.scrollBy(scrollPerFrame)
                                        delay(16)
                                    }
                                }
                            }
                        }
                        // 不在边缘区域 → 停止自动滚动
                        else -> {
                            autoScrollJob?.cancel()
                            autoScrollJob = null
                        }
                    }
                }

                LazyColumn(
                    state = deepListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(notes, key = { _, n -> n.id }) { index, parentNote ->
                        val isDragging = deepDragIndex == index
                        DeepParentCard(
                            parentNote = parentNote,
                            children = modeNotes.filter { it.parentId == parentNote.id },
                            isDragging = isDragging,
                            dragOffset = if (isDragging) deepDragOffset else 0f,
                            // 所有条目都加 animateItem()，实现 200ms 平滑换位过渡动画
                            // 拖拽中的条目除外（它由 graphicsLayer.translationY 手动控制位置）
                            modifier = if (!isDragging) Modifier.animateItem() else Modifier,
                            onDragStart = {
                                deepDragIndex = index
                                deepDragOffset = 0f
                            },
                            onDragMove = { dragAmount ->
                                // 严格锁定横向：仅累加 Y 轴偏移，X 轴完全忽略
                                deepDragOffset += dragAmount

                                // —— 边缘自动滚动检测 ——
                                val layoutInfo = deepListState.layoutInfo
                                val vi = layoutInfo.visibleItemsInfo.find { it.index == index }
                                if (vi != null) {
                                    val itemTopOnScreen = vi.offset.toFloat() + deepDragOffset
                                    val itemBottomOnScreen = (vi.offset + vi.size).toFloat() + deepDragOffset
                                    updateAutoScroll(
                                        draggedItemTop = itemTopOnScreen,
                                        draggedItemBottom = itemBottomOnScreen,
                                        viewportStart = layoutInfo.viewportStartOffset.toFloat(),
                                        viewportEnd = layoutInfo.viewportEndOffset.toFloat(),
                                        currentIndex = index,
                                        lastIndex = notes.lastIndex
                                    )
                                }

                                // —— 实时交换逻辑（穿插换位）——
                                // 向下拖拽：累计偏移超过半张卡片高度 → 与下方条目交换
                                if (deepDragOffset > swapThreshold && index < notes.lastIndex) {
                                    vm.reorderDeepParents(parentNote.id, notes[index + 1].id)
                                    deepDragIndex = index + 1
                                    // 交换后扣减阈值，保留残余偏移避免视觉跳跃
                                    deepDragOffset -= swapThreshold
                                }
                                // 向上拖拽：累计偏移超过半张卡片高度 → 与上方条目交换
                                else if (deepDragOffset < -swapThreshold && index > 0) {
                                    vm.reorderDeepParents(parentNote.id, notes[index - 1].id)
                                    deepDragIndex = index - 1
                                    deepDragOffset += swapThreshold
                                }
                            },
                            onDragEnd = {
                                // 松手：停止边缘滚动
                                autoScrollJob?.cancel()
                                autoScrollJob = null
                                // 松手：重置拖拽状态，取消阴影上浮效果
                                deepDragIndex = -1
                                deepDragOffset = 0f
                                // 松手：统一持久化排序到本地存储（拖拽过程中不写盘，避免卡顿）
                                vm.persistDeepOrder()
                            },
                            onToggleFavorite = { vm.toggleFavorite(parentNote) },
                            onDelete = {
                                vm.deleteNote(parentNote)
                                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                            },
                            onRename = { newTitle -> vm.updateNoteTitle(parentNote, newTitle) },
                            onAddChild = { title, content ->
                                vm.addNote(title, content, NoteMode.DEEP, parentId = parentNote.id)
                            },
                            onUpdateChild = { child, title, content ->
                                vm.updateNote(child, title, content)
                            },
                            onDeleteChild = { child -> vm.deleteNote(child) },
                            onCopyChild = { text ->
                                copyToClipboard(context, "内容", text)
                                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                            },
                            onReorderChild = { fromId, toId ->
                                vm.reorderChildren(parentNote.id, fromId, toId)
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
            onTogglePin = {
                vm.togglePin(note)
                actionNote = null
                Toast.makeText(context, if (!note.isPinned) "已置顶" else "已取消置顶", Toast.LENGTH_SHORT).show()
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
                if (note.isPinned) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "置顶",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
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

// ===== 深度模式：可折叠父笔记卡片 =====

@Composable
private fun DeepParentCard(
    parentNote: Note,
    children: List<Note>,
    isDragging: Boolean = false,
    dragOffset: Float = 0f,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit = {},
    onDragMove: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onAddChild: (String, String) -> Unit,
    onUpdateChild: (Note, String, String) -> Unit,
    onDeleteChild: (Note) -> Unit,
    onCopyChild: (String) -> Unit,
    onReorderChild: (Long, Long) -> Unit
) {
    var expanded by rememberSaveable(parentNote.id) { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showAddChild by remember { mutableStateOf(false) }
    var editingChild by remember { mutableStateOf<Note?>(null) }

    val dateFormatter = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    }
    // 拖拽时的视觉反馈：缩放动画（弹簧效果，松手回弹自然）
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "dragScale"
    )
    // 拖拽时的阴影高度动画
    val shadowElevation by animateFloatAsState(
        targetValue = if (isDragging) 16f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "dragShadow"
    )
    // 拖拽时的透明度动画
    val dragAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
        label = "dragAlpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 999f else 0f)
            // 拖拽手势绑定在 Card 外层，与内层按钮点击完全隔离，杜绝手势冲突
            .pointerInput(parentNote.id) {
                detectDragGesturesAfterLongPress(
                    // onDragStart 在长按检测通过后触发（系统默认约 500ms 长按）
                    onDragStart = { onDragStart() },
                    onDragEnd = {
                        // 松手立刻终止拖拽浮动状态
                        onDragEnd()
                    },
                    onDragCancel = {
                        // 取消时也终止拖拽
                        onDragEnd()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        // 严格锁定横向：仅传递 Y 轴偏移，X 轴完全忽略
                        onDragMove(dragAmount.y)
                    }
                )
            }
            .graphicsLayer {
                // 纵向位移（仅 Y 轴，横向已锁定）
                translationY = dragOffset
                this.shadowElevation = shadowElevation
                scaleX = scale
                scaleY = scale
                alpha = dragAlpha
            }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 标题行：点击展开/折叠 + 操作按钮
            // combinedClickable 单独在此层，不与拖拽手势冲突
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = { expanded = !expanded })
            ) {
                // 展开/折叠箭头
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowDown
                                  else Icons.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) "折叠" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = parentNote.title.ifBlank { "(无标题)" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // 子笔记数量徽标
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
                        if (parentNote.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = "收藏",
                        tint = if (parentNote.isFavorite) MaterialTheme.colorScheme.primary
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
                text = "更新: ${dateFormatter.format(java.util.Date(parentNote.updatedAt))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 展开内容：子笔记列表 + 添加子笔记按钮
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
                        DeepChildRow(
                            index = index,
                            child = child,
                            onEdit = { editingChild = child },
                            onCopy = { onCopyChild(child.content) },
                            onDelete = { onDeleteChild(child) },
                            onMoveUp = {
                                if (index > 0) {
                                    onReorderChild(child.id, children[index - 1].id)
                                }
                            },
                            onMoveDown = {
                                if (index < children.lastIndex) {
                                    onReorderChild(child.id, children[index + 1].id)
                                }
                            }
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

                Spacer(Modifier.size(8.dp))

                // 添加子笔记按钮
                OutlinedButton(
                    onClick = { showAddChild = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("添加子笔记", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    // 重命名对话框
    if (showRenameDialog) {
        RenameDialog(
            initialTitle = parentNote.title,
            onDismiss = { showRenameDialog = false },
            onConfirm = {
                onRename(it)
                showRenameDialog = false
            }
        )
    }

    // 添加子笔记对话框
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

    // 编辑子笔记对话框
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
private fun DeepChildRow(
    index: Int,
    child: Note,
    onEdit: () -> Unit,
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
        // 拖动排序按钮：上箭头 + 下箭头
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 2.dp)
        ) {
            IconButton(onClick = onMoveUp, modifier = Modifier.size(20.dp)) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = "上移",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(onClick = onMoveDown, modifier = Modifier.size(20.dp)) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "下移",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
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

@Composable
internal fun RenameDialog(
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
    val tabCount = tabs.size
    val scope = rememberCoroutineScope()

    val indicatorPos = remember { Animatable(selectedIndex.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }

    val currentIndex by rememberUpdatedState(selectedIndex)
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)

    LaunchedEffect(selectedIndex) {
        if (!isDragging) {
            indicatorPos.animateTo(
                targetValue = selectedIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    // 按压/拖拽时滑块缩小（凹进去的反馈），松手弹回
    val indicatorScale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "indicatorScale"
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(24.dp))
                .pointerInput(Unit) {
                    val tabWidthPx = size.width.toFloat() / tabCount
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        var totalDrag = 0f
                        var dragStarted = false
                        // 只有按下点在蓝色滑块区域内才触发按压动画
                        val indicatorLeft = currentIndex * tabWidthPx
                        val indicatorRight = indicatorLeft + tabWidthPx
                        val onIndicator = startX in indicatorLeft..indicatorRight
                        if (onIndicator) isPressed = true

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            val dragAmount = change.positionChange().x
                            if (dragAmount != 0f) {
                                totalDrag += dragAmount
                                if (!dragStarted && kotlin.math.abs(totalDrag) > viewConfiguration.touchSlop) {
                                    dragStarted = true
                                    isDragging = true
                                    scope.launch { indicatorPos.stop() }
                                }
                                if (dragStarted) {
                                    change.consume()
                                    val base = currentIndex.toFloat()
                                    val target = (base + totalDrag / tabWidthPx)
                                        .coerceIn(0f, (tabCount - 1).toFloat())
                                    scope.launch { indicatorPos.snapTo(target) }
                                }
                            }
                            if (!change.pressed) break
                        }

                        isPressed = false

                        if (dragStarted) {
                            val targetIndex = indicatorPos.value.roundToInt()
                                .coerceIn(0, tabCount - 1)
                            isDragging = false
                            if (targetIndex != currentIndex) {
                                currentOnTabSelected(targetIndex)
                            } else {
                                scope.launch {
                                    indicatorPos.animateTo(
                                        targetValue = currentIndex.toFloat(),
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            }
                        } else {
                            val tappedIndex = (startX / tabWidthPx).toInt().coerceIn(0, tabCount - 1)
                            currentOnTabSelected(tappedIndex)
                        }
                    }
                }
        ) {
            val tabWidth = maxWidth / tabCount

            // 滑动指示器：按压时缩小产生"凹进去"效果
            Box(
                modifier = Modifier
                    .offset(x = tabWidth * indicatorPos.value + 4.dp, y = 4.dp)
                    .size(width = tabWidth - 8.dp, height = 36.dp)
                    .graphicsLayer {
                        scaleX = indicatorScale
                        scaleY = indicatorScale
                    }
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary)
            ) {}

            // 文字层（不拦截触摸事件，点击和拖拽统一由父级处理）
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = index == selectedIndex
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "tabTextColor$index"
                    )
                    val textScale by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0.92f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "tabTextScale$index"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
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