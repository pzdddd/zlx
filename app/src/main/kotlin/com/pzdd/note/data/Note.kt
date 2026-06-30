package com.pzdd.note.data

import kotlinx.serialization.Serializable

@Serializable
data class Note(
    val id: Long = System.currentTimeMillis(),
    val title: String = "",
    val content: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)