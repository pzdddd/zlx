package com.pzdd.note.ui.page

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.pzdd.note.data.Note
import com.pzdd.note.data.NoteMode
import com.pzdd.note.ui.NoteViewModel
import com.pzdd.note.ui.copyToClipboard
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
@Composable
fun FavoritesPage(
    vm: NoteViewModel,
    paddingValues: PaddingValues,
    onScrollDirectionChanged: (Boolean) -> Unit = {},
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null,
    onHideBottomBar: (Boolean) -> Unit = {}
) {
    val allNotes by vm.notes.collectAsState()
    val context = LocalContext.current

    // 顶部 Tab：0 = 普通模式收藏，1 = 多列模式收藏
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("普通模式", "多列模式")

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
    // 预计算子笔记映射，避免每张卡片 filter 一遍
    val childrenMap by remember(allNotes) {
        derivedStateOf {
            allNotes.filter { it.parentId != -1L }.groupBy { it.parentId }
        }
    }

    var editingNote by remember { mutableStateOf<Note?>(null) }
    var actionNote by remember { mutableStateOf<Note?>(null) }
    // 多列模式：添加/编辑子笔记（提升到顶层，NoteEditDialog 在顶层渲染确保全屏居中）
    var favAddChildParent by remember { mutableStateOf<Note?>(null) }
    var favEditingChild by remember { mutableStateOf<Note?>(null) }

    // 滚动状态：检测滚动方向以驱动悬浮底栏的显示/隐藏
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(listState) {
        var prevIndex = listState.firstVisibleItemIndex
        var prevOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            val isScrollingUp = when {
                index > prevIndex -> true
                index < prevIndex -> false
                else -> offset > prevOffset
            }
            onScrollDirectionChanged(isScrollingUp)
            prevIndex = index
            prevOffset = offset
        }
    }

    // 多列模式也使用 LazyColumn
    val deepListState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(deepListState) {
        var prevIndex = deepListState.firstVisibleItemIndex
        var prevOffset = deepListState.firstVisibleItemScrollOffset
        snapshotFlow {
            deepListState.firstVisibleItemIndex to deepListState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            val isScrollingUp = when {
                index > prevIndex -> true
                index < prevIndex -> false
                else -> offset > prevOffset
            }
            onScrollDirectionChanged(isScrollingUp)
            prevIndex = index
            prevOffset = offset
        }
    }

    // 背景模糊（长按菜单或编辑面板弹出时触发，即时开关无渐变）
    val anyOverlay = actionNote != null || editingNote != null || favAddChildParent != null || favEditingChild != null
    val blurRenderEffect = remember(anyOverlay) {
        if (anyOverlay) {
            android.graphics.RenderEffect.createBlurEffect(
                15f, 15f, android.graphics.Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        } else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .graphicsLayer {
                if (anyOverlay) {
                    renderEffect = blurRenderEffect
                }
            }
    ) {
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
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    // 纯滑动过渡，不做交叉淡入淡出，避免新旧页面重叠产生残影
                    val direction = if (targetState > initialState) 1 else -1
                    slideInHorizontally(
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        initialOffsetX = { w -> direction * w }
                    ) togetherWith slideOutHorizontally(
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        targetOffsetX = { w -> -direction * w }
                    )
                },
                contentAlignment = Alignment.TopStart,
                label = "favModeSwitch"
            ) { tab ->
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
                        text = "在${tabs[tab]}中收藏的笔记会显示在这里",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (tab == 0) {
                // 普通模式：LazyColumn
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(favorites, key = { it.id }) { note ->
                        FavCard(
                            note = note,
                            onClick = { editingNote = note },
                            onLongClick = { actionNote = note }
                        )
                    }
                }
            } else {
                // 多列模式：LazyColumn，只渲染可见项，性能远优于 Column+verticalScroll
                LazyColumn(
                    state = deepListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(favorites, key = { it.id }) { note ->
                        FavDeepCard(
                            note = note,
                            children = childrenMap[note.id] ?: emptyList(),
                            onToggleFavorite = { vm.toggleFavorite(note) },
                            onDelete = {
                                vm.deleteNote(note)
                                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                            },
                            onRename = { newTitle -> vm.updateNoteTitle(note, newTitle) },
                            onRequestAddChild = { favAddChildParent = note },
                            onRequestEditChild = { child -> favEditingChild = child },
                            onDeleteChild = { child -> vm.deleteNote(child) },
                            onCopyChild = { text ->
                                copyToClipboard(context, "内容", text)
                                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
            } // AnimatedContent
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
            },
            onHideBottomBar = onHideBottomBar
        )
    }

    // 多列模式：新建子笔记（顶层渲染，确保全屏居中）
    favAddChildParent?.let { parent ->
        NoteEditDialog(
            title = "新建子笔记",
            initialTitle = "",
            initialContent = "",
            onDismiss = { favAddChildParent = null },
            onConfirm = { t, c ->
                if (t.isNotBlank() || c.isNotBlank()) {
                    vm.addNote(t, c, NoteMode.DEEP, parentId = parent.id)
                    Toast.makeText(context, "已添加", Toast.LENGTH_SHORT).show()
                }
                favAddChildParent = null
            },
            onHideBottomBar = onHideBottomBar
        )
    }

    // 多列模式：编辑子笔记（顶层渲染，确保全屏居中）
    favEditingChild?.let { child ->
        NoteEditDialog(
            title = "编辑子笔记",
            initialTitle = child.title,
            initialContent = child.content,
            onDismiss = { favEditingChild = null },
            onConfirm = { t, c ->
                vm.updateNote(child, t, c)
                favEditingChild = null
            },
            onHideBottomBar = onHideBottomBar
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
            },
            backdrop = backdrop
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
 * 多列模式收藏卡片（可折叠，展开后显示子笔记）
 */
@Composable
private fun FavDeepCard(
    note: Note,
    children: List<Note>,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onRequestAddChild: () -> Unit,
    onRequestEditChild: (Note) -> Unit,
    onDeleteChild: (Note) -> Unit,
    onCopyChild: (String) -> Unit
) {
    var expanded by rememberSaveable(note.id) { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    val dateFormatter = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
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

            // 展开内容：AnimatedVisibility + expandVertically，与设置页主题模式展开完全一致
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
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
                                onEdit = { onRequestEditChild(child) },
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
                        onClick = onRequestAddChild,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加子笔记", style = MaterialTheme.typography.labelLarge)
                    }
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
 * 顶部横向双文字 Tab：普通模式 / 多列模式
 */
@Composable
private fun FavModeTabRow(
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
        label = "favIndicatorScale"
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
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "favTabTextScale$index"
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