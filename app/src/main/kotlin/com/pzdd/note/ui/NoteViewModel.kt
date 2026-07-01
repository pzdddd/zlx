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
     * 重新排列深度模式父笔记顺序（拖拽过程中调用，不立即持久化，避免频繁写盘卡顿）。
     * 将 fromId 移动到 toId 的位置。
     */
    fun reorderDeepParents(fromId: Long, toId: Long) {
        val list = _notes.value.toMutableList()
        val fromIndex = list.indexOfFirst { it.id == fromId }
        val toIndex = list.indexOfFirst { it.id == toId }
        if (fromIndex == -1 || toIndex == -1 || fromIndex == toIndex) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _notes.value = list
        // 拖拽过程中不 persist()，松手时由 persistDeepOrder() 统一保存
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
        list.add(toIndex, item)
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