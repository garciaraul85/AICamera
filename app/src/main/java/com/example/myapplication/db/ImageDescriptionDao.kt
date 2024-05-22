package com.example.myapplication.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ImageDescriptionDao {
    @Query("SELECT * FROM ImageDescription ORDER BY timestamp DESC LIMIT 20")
    fun getLast20Descriptions(): List<ImageDescription>

    @Insert
    fun insertDescription(description: ImageDescription)

    @Query("DELETE FROM ImageDescription WHERE id IN (SELECT id FROM ImageDescription ORDER BY timestamp ASC LIMIT :count)")
    fun deleteOldest(count: Int)
}