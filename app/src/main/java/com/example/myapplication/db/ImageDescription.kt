package com.example.myapplication.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ImageDescription(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val description: String,
    val encodedImage: String,
    val timestamp: Long
)