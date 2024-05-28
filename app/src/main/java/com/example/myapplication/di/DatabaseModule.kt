package com.example.myapplication.di

import android.content.Context
import androidx.room.Room
import com.example.myapplication.db.AppDatabase
import com.example.myapplication.db.ImageDescriptionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "my_database"
        ).build()
    }

    @Provides
    fun provideImageDescriptionDao(appDatabase: AppDatabase): ImageDescriptionDao {
        return appDatabase.imageDescriptionDao()
    }
}