package com.example.myapplication

import android.app.Application
import androidx.room.Room
import com.example.myapplication.db.AppDatabase

class App : Application() {
    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        // Initialize the Room database
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "image-database"
        ).build()
    }
}