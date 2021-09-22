package com.example.scopedstorage

import android.net.Uri

data class SharedStoragePicture (
    val id: Long,
    val name: String,
    val width: Int,
    val height: Int,
    val contentUri: Uri
    )