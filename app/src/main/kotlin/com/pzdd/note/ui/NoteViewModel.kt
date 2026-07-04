package com.pzdd.note.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pzdd.note.data.Note
import com.pzdd.note.data.NoteMode
import com.pzdd.note.data.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NoteViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = NoteRepository(app)

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    init {
        _notes.value = repo.loadAll()
    }

    private fun persist() {
        repo.saveAll(_notes.value)
    }

    fun addNote(title: String, content: String, mode: NoteMode = NoteMode.NORMAL, parentId: Long = -1L) {
        if (title.isBlank() && content.isBlank()) return
        val now = System.currentTimeMillis()
        val note = Note(
            id = now,
            title = title.trim(),
            content = content.trim(),
            mode = mode.value,
            parentId = parentId,
            createdAt = now,
            updatedAt = now
        )
        _notes.value = listOf(note) + _notes.value
        persist()
    }

    /** 删除父笔记及其所有子笔记 */
    fun deleteNote(note: Note) {
        _notes.value = _notes.value.filterNot { it.id == note.id || it.parentId == note.id }
        persist()
    }

    fun updateNote(note: Note, title: String, content: String) {
        _notes.value = _notes.value.map {
            if (it.id == note.id) it.copy(
                title = title.trim(),
                content = content.trim(),
                updatedAt = System.currentTimeMillis()
            ) else it
        }
        persist()
    }

    fun updateNoteTitle(note: Note, title: String) {
        _notes.value = _notes.value.map {
            if (it.id == note.id) it.copy(
                title = title,
                updatedAt = System.currentTimeMillis()
            ) else it
        }
        persist()
    }

    fun updateNoteContent(note: Note, content: String) {
        _notes.value = _notes.value.map {
            if (it.id == note.id) it.copy(
                content = content,
                updatedAt = System.currentTimeMillis()
            ) else it
        }
        persist()
    }

    fun addCommand(note: Note, command: String) {
        if (command.isBlank()) return
        val lines = if (note.content.isEmpty()) listOf(command)
                     else note.content.split("\n") + command
        updateNoteContent(note, lines.joinToString("\n"))
    }

    fun updateCommand(note: Note, index: Int, command: String) {
        val lines = note.content.split("\n").toMutableList()
        if (index !in lines.indices) return
        lines[index] = command
        updateNoteContent(note, lines.joinToString("\n"))
    }

    fun deleteCommand(note: Note, index: Int) {
        val lines = note.content.split("\n").toMutableList()
        if (index !in lines.indices) return
        lines.removeAt(index)
        updateNoteContent(note, lines.joinToString("\n"))
    }

    fun toggleFavorite(note: Note) {
        _notes.value = _notes.value.map {
            if (it.id == note.id) it.copy(
                isFavorite = !it.isFavorite,
                updatedAt = System.currentTimeMillis()
            ) else it
        }
        persist()
    }

    fun togglePin(note: Note) {
        _notes.value = _notes.value.map {
            if (it.id == note.id) it.copy(
                isPinned = !it.isPinned,
                updatedAt = System.currentTimeMillis()
            ) else it
        }
        persist()
    }

    /**
     * 交换两个深度模式父笔记的顺序（拖拽过程中实时调用）。
     * 在全局列表中找到 fromId 和 toId，交换它们的位置。
     */
    fun swapDeepParents(fromId: Long, toId: Long) {
        val list = _notes.value.toMutableList()
        val fromIndex = list.indexOfFirst { it.id == fromId }
        val toIndex = list.indexOfFirst { it.id == toId }
        if (fromIndex == -1 || toIndex == -1 || fromIndex == toIndex) return
        val tmp = list[fromIndex]
        list[fromIndex] = list[toIndex]
        list[toIndex] = tmp
        _notes.value = list
    }

    /**
     * 重新排列深度模式父笔记顺序（拖拽过程中调用，不立即持久化，避免频繁写盘卡顿）。
     * 将 fromId 移动到 toId 的位置。
     */
    fun reorderDeepParents(fromId: Long, toId: Long) {
        val list = _notes.value.toMutableList()
        val fromIndex = list.indexOfFirst { it.id == fromId }
        val toIndex = list.indexOfFirst { it.id == toId }
        if (fromIndex == -1 || toIndex == -1 || fromIndex == toIndex) return
        val item = list.removeAt(fromIndex)
        // removeAt 后元素前移，重新查找 toId 的位置
        val newToIndex = list.indexOfFirst { it.id == toId }
        list.add(newToIndex, item)
        _notes.value = list
    }

    /**
     * 将指定深度模式父笔记移动到目标 index 位置。
     * targetIndex 是最终期望在深度父笔记子集中的位置（0-based）。
     * 例如 fromId 在 index 0，targetIndex=3，则移动后 fromId 出现在 index 3。
     */
    fun moveDeepParentToIndex(fromId: Long, targetIndex: Int) {
        val list = _notes.value.toMutableList()
        val deepParents = list.filter { it.mode == NoteMode.DEEP.value && it.parentId == -1L }
        if (targetIndex < 0 || targetIndex >= deepParents.size) return
        val fromDeepIndex = deepParents.indexOfFirst { it.id == fromId }
        if (fromDeepIndex == -1 || fromDeepIndex == targetIndex) return

        // 取出被移动的项
        val fromGlobalIndex = list.indexOfFirst { it.id == fromId }
        if (fromGlobalIndex == -1) return
        val item = list.removeAt(fromGlobalIndex)

        // 移除后重新构建深度父笔记列表，找到目标全局插入位置
        val deepAfter = list.filter { it.mode == NoteMode.DEEP.value && it.parentId == -1L }

        // 计算在移除后列表中的插入深度索引：
        // - 向下移动：移除后 fromDeepIndex 之前的元素不变，fromDeepIndex 之后的元素前移一位
        //   要让 item 最终在 targetIndex，需要在 deepAfter 的 targetIndex 位置插入
        //   但 deepAfter 只有 size-1 个元素，如果 targetIndex == deepAfter.size 则追加到末尾
        // - 向上移动：移除不影响 targetIndex 之前的元素
        //   在 deepAfter 的 targetIndex 位置插入即可
        val insertDeepIdx = targetIndex.coerceIn(0, deepAfter.size)

        if (insertDeepIdx >= deepAfter.size) {
            // 插入到最后一个深度父笔记的后面
            val lastDeepId = deepAfter.last().id
            val lastDeepGlobal = list.indexOfFirst { it.id == lastDeepId }
            list.add(lastDeepGlobal + 1, item)
        } else {
            // 插入到目标深度父笔记的前面
            val targetNoteId = deepAfter[insertDeepIdx].id
            val targetGlobalIndex = list.indexOfFirst { it.id == targetNoteId }
            list.add(targetGlobalIndex, item)
        }
        _notes.value = list
    }

    /** 拖拽松手后调用：持久化当前排序到本地存储 */
    fun persistDeepOrder() {
        persist()
    }
    /** 重新排列子笔记顺序：在 parentId 下将 fromId 移动到 toId 的位置 */
    fun reorderChildren(parentId: Long, fromId: Long, toId: Long) {
        val list = _notes.value.toMutableList()
        val fromIndex = list.indexOfFirst { it.id == fromId }
        val toIndex = list.indexOfFirst { it.id == toId }
        if (fromIndex == -1 || toIndex == -1 || fromIndex == toIndex) return
        val item = list.removeAt(fromIndex)
        val insertIndex = if (fromIndex < toIndex) toIndex - 1 else toIndex
        list.add(insertIndex, item)
        _notes.value = list
        persist()
    }

    fun moveCommand(note: Note, index: Int, up: Boolean) {
        val lines = note.content.split("\n").toMutableList()
        val target = if (up) index - 1 else index + 1
        if (target !in lines.indices) return
        val tmp = lines[index]
        lines[index] = lines[target]
        lines[target] = tmp
        updateNoteContent(note, lines.joinToString("\n"))
    }
}