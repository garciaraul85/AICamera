package com.example.myapplication.di

import com.example.myapplication.db.ImageDescriptionDao
import com.example.myapplication.network.OpenAiApiService
import com.example.myapplication.util.ImageAnalyzer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ImageAnalyzerModule {
    @Provides
    @Singleton
    fun provideImageAnalyzer(
        imageDescriptionDao: ImageDescriptionDao,
        openAiApiService: OpenAiApiService
    ): ImageAnalyzer {
        return ImageAnalyzer(imageDescriptionDao, openAiApiService, {}, {})
    }
}