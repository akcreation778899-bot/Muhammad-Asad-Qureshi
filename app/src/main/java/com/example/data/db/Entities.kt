package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String = "GENERAL", // CALL, FILE_COPY, NEWS, WEB, DEVICE, GENERAL
    val actionDetail: String = "",
    val isSuccess: Boolean = true
)

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val url: String,
    val favicon: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "file_logs")
data class FileOperationLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val operation: String, // COPY, MOVE, DELETE, CREATE
    val sourcePath: String,
    val destinationPath: String = "",
    val fileCount: Int = 1,
    val status: String = "SUCCESS",
    val timestamp: Long = System.currentTimeMillis()
)
