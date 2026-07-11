package com.pzdd.note.ui.page
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.pzdd.note.data.Note
import com.pzdd.note.data.NoteMode
import com.pzdd.note.data.SortOrder
import com.pzdd.note.data.ViewMode
import com.pzdd.note.ui.NoteViewModel
import com.pzdd.note.ui.SettingsViewModel
import com.pzdd.note.ui.copyToClipboard

@Composable
fun HomePage(
    vm: NoteViewModel,
    settingsVm: SettingsViewModel,
    paddingValues: PaddingValues,
    floatingBottomBar: Boolean = false,
    bottomBarVisible: Boolean = false,
    onScrollDirectionChanged: (Boolean) -> Unit = {},
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null,
    onHideBottomBar: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val allNotes by vm.notes.collectAsState()
    val appSettings by settingsVm.settings.collectAsState()
    val sortOrder = appSettings.sortOrder
    val viewMode = appSettings.viewMode
    // 菜单弹出状态
    var showMenu by remember { mutableStateOf(false) }
    // 顶部 Tab：0 = 普通模式，1 = 多列模式
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("普通模式", "多列模式")

    // 搜索关键词（普通模式和多列模式共用）
    var searchQuery by remember { mutableStateOf("") }
    // 按模式过滤：普通模式和多列模式相互独立，互不同步
    val modeNotes by remember(allNotes) {
        derivedStateOf {
            if (selectedTab == 0) allNotes.filter { it.mode == NoteMode.NORMAL.value }
            else allNotes.filter { it.mode == NoteMode.DEEP.value }
        }
    }
    // 预计算子笔记映射（parentId -> children），避免每张卡片都 filter 一遍（O(n²) → O(n)）
    val childrenMap by remember(modeNotes) {
        derivedStateOf {
            modeNotes.filter { it.parentId != -1L }
                .groupBy { it.parentId }
        }
    }
    // 普通模式：应用搜索过滤，按修改时间排序（置顶笔记始终在前，不参与排序方向）
    // 多列模式：只显示父笔记（parentId == -1），并应用搜索过滤
    val notes = if (selectedTab == 0) {
        val filtered = if (searchQuery.isBlank()) modeNotes
        else modeNotes.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.content.contains(searchQuery, ignoreCase = true)
        }
        // 置顶笔记和非置顶笔记分离，各自按修改时间排序后再合并
        val pinned = filtered.filter { it.isPinned }
        val unpinned = filtered.filter { !it.isPinned }
        val sortedPinned = if (sortOrder == SortOrder.DESCENDING)
            pinned.sortedByDescending { it.updatedAt }
        else pinned.sortedBy { it.updatedAt }
        val sortedUnpinned = if (sortOrder == SortOrder.DESCENDING)
            unpinned.sortedByDescending { it.updatedAt }
        else unpinned.sortedBy { it.updatedAt }
        sortedPinned + sortedUnpinned
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
    // 多列模式专用状态
    var deepAddNote by remember { mutableStateOf(false) }
    var deepEditingNote by remember { mutableStateOf<Note?>(null) }
    // 多列模式：添加/编辑子笔记（提升到顶层，NoteEditDialog 在顶层渲染确保全屏居中）
    var deepAddChildParent by remember { mutableStateOf<Note?>(null) }
    var deepEditingChild by remember { mutableStateOf<Note?>(null) }

    // 搜索栏展开/收起状态：向上滚动收起，向下滚动展开
    var searchBarExpanded by remember { mutableStateOf(true) }

    // 滚动状态：检测滚动方向以驱动悬浮底栏的显示/隐藏
    val listState = rememberLazyListState()
    // 宫格视图滚动状态
    val gridState = rememberLazyGridState()
    // 多列模式也使用 LazyColumn，性能远优于 Column+verticalScroll
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
            val isScrollingUp = when {
                index > prevIndex -> true
                index < prevIndex -> false
                else -> offset > prevOffset  // 同一 item 内，offset 增大 = 向上滚动
            }
            onScrollDirectionChanged(isScrollingUp)
            searchBarExpanded = !isScrollingUp
            prevIndex = index
            prevOffset = offset
        }
    }

    // 普通模式（列表视图）滚动检测
    LaunchedEffect(listState) {
        trackScrollDirection(listState)
    }
    // 普通模式（宫格视图）滚动检测
    LaunchedEffect(gridState) {
        var prevIndex = gridState.firstVisibleItemIndex
        var prevOffset = gridState.firstVisibleItemScrollOffset
        snapshotFlow {
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            val isScrollingUp = when {
                index > prevIndex -> true
                index < prevIndex -> false
                else -> offset > prevOffset
            }
            onScrollDirectionChanged(isScrollingUp)
            searchBarExpanded = !isScrollingUp
            prevIndex = index
            prevOffset = offset
        }
    }
    // 多列模式滚动检测
    LaunchedEffect(deepListState) {
        trackScrollDirection(deepListState)
    }
    // 背景模糊（长按菜单或编辑面板弹出时触发，即时开关无渐变）
    val anyOverlay = actionNote != null || editingNote != null || deepEditingNote != null ||
        showAdd || deepAddNote || deepAddChildParent != null || deepEditingChild != null
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
        // 软件名称 + 右上角菜单按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PZ-NOTE",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "菜单",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HomeMenuPopup(
                    expanded = showMenu,
                    onDismiss = { showMenu = false },
                    sortOrder = sortOrder,
                    viewMode = viewMode,
                    onSortOrderSelected = {
                        settingsVm.setSortOrder(it)
                        showMenu = false
                    },
                    onViewModeSelected = {
                        settingsVm.setViewMode(it)
                        showMenu = false
                    }
                )
            }
        }

        // 顶部双文字 Tab
        ModeTabRow(
            tabs = tabs,
            selectedIndex = selectedTab,
            onTabSelected = { selectedTab = it }
        )

        // 搜索框（普通模式和多列模式共用），向上滚动时收起
        AnimatedVisibility(
            visible = searchBarExpanded,
            enter = expandVertically(animationSpec = tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(200))
        ) {
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
                    .padding(horizontal = 20.dp, vertical = 2.dp)
                    .heightIn(min = 10.dp)
        )
        }

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
            label = "modeSwitch"
        ) { tab ->
        if (tab == 0) {
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
            } else if (viewMode == ViewMode.GRID) {
                // 网格视图
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = if (floatingBottomBar)
                        PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp)
                    else PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    gridItems(notes, key = { it.id }) { note ->
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
                            },
                            viewMode = ViewMode.GRID
                        )
                    }
                }
            } else {
                // 列表视图
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = if (floatingBottomBar)
                        PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp)
                    else PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
            // 多列模式：可折叠父笔记列表
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
                // 多列模式：可拖动排序的父笔记列表
                // ================================================================
                var deepDragId by remember { mutableStateOf<Long?>(null) }
                var deepDragOffset by remember { mutableStateOf(0f) }
                var isSettling by remember { mutableStateOf(false) }
                var settlingDragIndex by remember { mutableStateOf(-1) }
                // 松手时计算好的目标 index（在 settle 动画期间冻结使用）
                var settleTargetIndex by remember { mutableStateOf(-1) }
                val cardHeights = remember { mutableMapOf<Long, Float>() }
                val itemSpacingPx = with(LocalDensity.current) { 10.dp.toPx() }

                // 当前拖拽卡片在列表中的 index
                val deepDragIndex = if (isSettling) settlingDragIndex
                                    else notes.indexOfFirst { it.id == deepDragId }

                // 松手后平滑归位动画
                val settleOffset by animateFloatAsState(
                    targetValue = if (isSettling) 0f else deepDragOffset,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "settle",
                    finishedListener = {
                        if (isSettling) {
                            isSettling = false
                            settlingDragIndex = -1
                            settleTargetIndex = -1
                            deepDragId = null
                            deepDragOffset = 0f
                            vm.persistDeepOrder()
                        }
                    }
                )
                val effectiveDragOffset = if (isSettling) settleOffset else deepDragOffset

                LazyColumn(
                    state = deepListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = if (floatingBottomBar)
                        PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp)
                    else PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(notes, key = { _, parentNote -> parentNote.id }) { index, parentNote ->
                        val isDragging = deepDragIndex == index
                        val dragItemHeight = cardHeights[deepDragId] ?: 0f
                        val currentItemHeight = cardHeights[parentNote.id] ?: 0f

                        // ===== 计算非拖拽卡片的让位偏移 =====
                        var displacement = 0f
                        if (deepDragIndex >= 0 && !isDragging && dragItemHeight > 0) {
                            if (index > deepDragIndex) {
                                var distance = 0f
                                for (i in deepDragIndex until index) {
                                    distance += (cardHeights[notes[i].id] ?: 0f) + itemSpacingPx
                                }
                                val threshold = distance - currentItemHeight * 0.5f
                                if (effectiveDragOffset > threshold) {
                                    displacement = -(dragItemHeight + itemSpacingPx)
                                }
                            } else if (index < deepDragIndex) {
                                var distance = 0f
                                for (i in (index + 1)..deepDragIndex) {
                                    distance += (cardHeights[notes[i].id] ?: 0f) + itemSpacingPx
                                }
                                val threshold = -(distance - currentItemHeight * 0.5f)
                                if (effectiveDragOffset < threshold) {
                                    displacement = (dragItemHeight + itemSpacingPx)
                                }
                            }
                        }

                        val animatedDisplacement by animateFloatAsState(
                            targetValue = displacement,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "displacement"
                        )

                        DeepParentCard(
                            parentNote = parentNote,
                            children = childrenMap[parentNote.id] ?: emptyList(),
                            isDragging = isDragging,
                            dragOffset = if (isDragging) effectiveDragOffset else 0f,
                            placementOffset = if (!isDragging) animatedDisplacement else 0f,
                            modifier = Modifier.onGloballyPositioned { coords ->
                                cardHeights[parentNote.id] = coords.size.height.toFloat()
                            },
                            onDragStart = {
                                deepDragId = parentNote.id
                                deepDragOffset = 0f
                            },
                            onDragMove = { dragAmount ->
                                deepDragOffset += dragAmount
                            },
                            onDragEnd = {
                                // ===== 实时从 state 读取所有值（pointerInput 不重启，不能依赖捕获的局部变量）=====
                                val dragId = deepDragId
                                val offset = deepDragOffset
                                val dragH = cardHeights[dragId] ?: 0f
                                val currentIdx = notes.indexOfFirst { it.id == dragId }

                                settlingDragIndex = currentIdx

                                // ===== 计算目标 index =====
                                // 根据拖拽偏移量，逐个累加经过的卡片高度（含 spacing），
                                // 当累计距离超过半张卡片时，认为越过了该卡片
                                var targetIdx = currentIdx
                                if (currentIdx >= 0 && dragH > 0 && notes.isNotEmpty()) {
                                    if (offset > 0) {
                                        // 向下拖
                                        var accumulated = 0f
                                        var probe = currentIdx
                                        while (probe < notes.lastIndex) {
                                            val nextH = cardHeights[notes[probe + 1].id] ?: 0f
                                            // 越过下一张卡片中心：需要移动 (dragH/2 + spacing + nextH/2)
                                            val needed = dragH * 0.5f + itemSpacingPx + nextH * 0.5f
                                            accumulated += needed
                                            if (offset >= accumulated) {
                                                probe++
                                                targetIdx = probe
                                            } else break
                                        }
                                    } else if (offset < 0) {
                                        // 向上拖
                                        var accumulated = 0f
                                        var probe = currentIdx
                                        while (probe > 0) {
                                            val prevH = cardHeights[notes[probe - 1].id] ?: 0f
                                            val needed = dragH * 0.5f + itemSpacingPx + prevH * 0.5f
                                            accumulated += needed
                                            if (-offset >= accumulated) {
                                                probe--
                                                targetIdx = probe
                                            } else break
                                        }
                                    }
                                }

                                settleTargetIndex = targetIdx

                                // ===== 执行重排 =====
                                if (targetIdx != currentIdx && targetIdx >= 0 && targetIdx < notes.size) {
                                    vm.moveDeepParentToIndex(dragId!!, targetIdx)
                                }

                                isSettling = true
                            },
                            onToggleFavorite = { vm.toggleFavorite(parentNote) },
                            onDelete = {
                                vm.deleteNote(parentNote)
                                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                            },
                            onRename = { newTitle -> vm.updateNoteTitle(parentNote, newTitle) },
                            onRequestAddChild = { deepAddChildParent = parentNote },
                            onRequestEditChild = { child -> deepEditingChild = child },
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
        } // AnimatedContent
        // FAB 固定位置，不随底栏显示/隐藏而移动
        // 悬浮底栏：不占 Scaffold 底部空间，需要较大 bottom 避开悬浮底栏
        // 标准底栏：已通过 paddingValues 扣除底栏高度，只需小间距
        val fabBottom = if (floatingBottomBar) 140.dp else 20.dp
        FloatingActionButton(
            onClick = { if (selectedTab == 0) showAdd = true else deepAddNote = true },
            shape = androidx.compose.foundation.shape.CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = fabBottom)
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
                if (t.isNotBlank() || c.isNotBlank()) {
                    vm.addNote(t, c, NoteMode.NORMAL)
                    Toast.makeText(context, "已添加", Toast.LENGTH_SHORT).show()
                }
                showAdd = false
            },
            onHideBottomBar = onHideBottomBar
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
            },
            onHideBottomBar = onHideBottomBar
        )
    }

    // 多列模式：新建笔记（父标题 + 子标题 + 子内容）
    if (deepAddNote) {
        DeepNoteEditDialog(
            title = "新建笔记",
            onDismiss = { deepAddNote = false },
            onConfirm = { parentTitle, childTitle, childContent ->
                if (parentTitle.isNotBlank() || childTitle.isNotBlank() || childContent.isNotBlank()) {
                    vm.addDeepNoteWithChild(parentTitle, childTitle, childContent)
                    Toast.makeText(context, "已添加", Toast.LENGTH_SHORT).show()
                }
                deepAddNote = false
            },
            onHideBottomBar = onHideBottomBar
        )
    }

    // 多列模式：新建子笔记（顶层渲染，确保全屏居中，与新建笔记完全一致）
    deepAddChildParent?.let { parent ->
        NoteEditDialog(
            title = "新建子笔记",
            initialTitle = "",
            initialContent = "",
            onDismiss = { deepAddChildParent = null },
            onConfirm = { t, c ->
                if (t.isNotBlank() || c.isNotBlank()) {
                    vm.addNote(t, c, NoteMode.DEEP, parentId = parent.id)
                    Toast.makeText(context, "已添加", Toast.LENGTH_SHORT).show()
                }
                deepAddChildParent = null
            },
            onHideBottomBar = onHideBottomBar
        )
    }

    // 多列模式：编辑子笔记（顶层渲染，确保全屏居中）
    deepEditingChild?.let { child ->
        NoteEditDialog(
            title = "编辑子笔记",
            initialTitle = child.title,
            initialContent = child.content,
            onDismiss = { deepEditingChild = null },
            onConfirm = { t, c ->
                vm.updateNote(child, t, c)
                deepEditingChild = null
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
                Toast.makeText(context, if (!note.isPinned) "已置顶" else "已取消置顶", Toast.LENGTH_SHORT).show()
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
private fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCopyContent: () -> Unit,
    onCopyTitle: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    viewMode: ViewMode = ViewMode.LIST
) {
    val isGrid = viewMode == ViewMode.GRID
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isGrid) Modifier.height(184.dp) else Modifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier
                .padding(if (isGrid) 10.dp else 14.dp)
                .then(if (isGrid) Modifier.fillMaxSize() else Modifier)
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
                    maxLines = if (isGrid) 4 else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (isGrid) Modifier.weight(1f) else Modifier
                )
            } else if (isGrid) {
                // 网格模式下无内容时也要占位，保持高度统一
                Spacer(Modifier.weight(1f))
            }

            // 分隔线
            Spacer(Modifier.size(8.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(Modifier.size(6.dp))

            // 操作按钮行：复制（左）、收藏（中）、删除（右）
            // 网格模式下用 SpaceBetween 自适应宽度，避免间距过大挤出删除按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isGrid)
                    Arrangement.SpaceBetween
                else
                    Arrangement.spacedBy(48.dp, Alignment.CenterHorizontally),
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

// ===== 多列模式：可折叠父笔记卡片 =====

@Composable
private fun DeepParentCard(
    parentNote: Note,
    children: List<Note>,
    isDragging: Boolean = false,
    dragOffset: Float = 0f,
    placementOffset: Float = 0f,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit = {},
    onDragMove: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onRequestAddChild: () -> Unit,
    onRequestEditChild: (Note) -> Unit,
    onDeleteChild: (Note) -> Unit,
    onCopyChild: (String) -> Unit,
    onReorderChild: (Long, Long) -> Unit
) {
    var expanded by rememberSaveable(parentNote.id) { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    val dateFormatter = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    }
    // 拖拽视觉反馈
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                // 纵向位移 = 拖拽偏移 + 位置交换动画偏移
                translationY = dragOffset + placementOffset
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
            // AnimatedVisibility + expandVertically 与设置页主题模式展开完全一致
            // Column + verticalScroll 结构下，展开时自动平滑推动下方所有内容
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
                            DeepChildRow(
                                index = index,
                                child = child,
                                onEdit = { onRequestEditChild(child) },
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

                    // 添加子笔记按钮
                    OutlinedButton(
                        onClick = onRequestAddChild,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加子笔记", style = MaterialTheme.typography.labelLarge)
                    }
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
 * 顶部横向双文字 Tab：普通模式 / 多列模式
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

/**
 * 首页右上角弹出菜单：排列方式 + 视图模式（白色圆角 iOS 风格）
 */
@Composable
private fun HomeMenuPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    sortOrder: SortOrder,
    viewMode: ViewMode,
    onSortOrderSelected: (SortOrder) -> Unit,
    onViewModeSelected: (ViewMode) -> Unit
) {
    if (!expanded) return
    val density = LocalDensity.current

    // 呼出过渡动画：缩放 + 淡入
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "menuScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(150),
        label = "menuAlpha"
    )

    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(with(density) { (-8.dp).roundToPx() }, with(density) { 48.dp.roundToPx() }),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            modifier = Modifier
                .width(200.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    transformOrigin = TransformOrigin(1f, 0f)  // 从右上角缩放
                }
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp)),
            color = Color.White,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                // ── 分组标题：排列方式 ──
                Text(
                    text = "排列方式",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF999999),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp)
                )
                // 最新修改在前
                IOSMenuRow(
                    label = "最新修改在前",
                    selected = sortOrder == SortOrder.DESCENDING,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = { onSortOrderSelected(SortOrder.DESCENDING) }
                )
                // 组内分隔线
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                    thickness = 0.5.dp,
                    color = Color(0xFFE5E5EA)
                )
                // 最早修改在前
                IOSMenuRow(
                    label = "最早修改在前",
                    selected = sortOrder == SortOrder.ASCENDING,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = { onSortOrderSelected(SortOrder.ASCENDING) }
                )

                // ── 组间分隔线（较粗）──
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 1.dp,
                    color = Color(0xFFE5E5EA)
                )

                // ── 分组标题：视图 ──
                Text(
                    text = "视图",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF999999),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp)
                )
                // 列表视图
                IOSMenuRow(
                    label = "列表视图",
                    selected = viewMode == ViewMode.LIST,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = { onViewModeSelected(ViewMode.LIST) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                    thickness = 0.5.dp,
                    color = Color(0xFFE5E5EA)
                )
                // 网格视图
                IOSMenuRow(
                    label = "网格视图",
                    selected = viewMode == ViewMode.GRID,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = { onViewModeSelected(ViewMode.GRID) }
                )
            }
        }
    }
}

/**
 * iOS 风格菜单行：左对齐文字 + 右侧选中勾
 */
@Composable
private fun IOSMenuRow(
    label: String,
    selected: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) accentColor else Color(0xFF1C1C1E)
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}