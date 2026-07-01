package com.pzdd.note.data

import kotlinx.serialization.Serializable

@Serializable
data class Note(
    val id: Long = System.currentTimeMillis(),
    val title: String = "",
    val content: String = "",
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val mode: Int = NoteMode.NORMAL.value,
    val parentId: Long = -1L,  // -1 = 顶层笔记（父笔记）；否则为子笔记，指向父笔记 id
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class NoteMode(val value: Int) {
    NORMAL(0),   // 普通模式
    DEEP(1);     // 深度模式

    companion object {
        fun fromValue(v: Int): NoteMode = entries.firstOrNull { it.value == v } ?: NORMAL
    }
}