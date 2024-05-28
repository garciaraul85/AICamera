package com.example.myapplication.di

import com.example.myapplication.repo.ImageRepository
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
        imageDescriptionDao: ImageRepository
    ): ImageAnalyzer {
        return ImageAnalyzer(imageDescriptionDao, {}, {})
    }
}