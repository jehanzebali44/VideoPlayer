package com.example.data.model

import android.net.Uri

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val duration: Long, // milliseconds
    val size: Long,     // bytes
    val path: String,   // file path
    val resolution: String?,
    val dateAdded: Long,
    val folderName: String
)
