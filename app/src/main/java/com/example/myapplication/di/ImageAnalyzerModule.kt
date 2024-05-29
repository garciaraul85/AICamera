package com.example.myapplication.di

import com.example.myapplication.repo.ImageRepository
import com.example.myapplication.util.ImageAnalyzer
import com.example.myapplication.util.ImageApiHandler
import com.example.myapplication.util.ImageProcessor
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
    fun provideImageProcessor(): ImageProcessor {
        return ImageProcessor()
    }

    @Provides
    @Singleton
    fun provideImageApiHandler(
        imageRepository: ImageRepository
    ): ImageApiHandler {
        return ImageApiHandler(imageRepository) {}
    }

    @Provides
    @Singleton
    fun provideImageAnalyzer(
        imageProcessor: ImageProcessor,
        imageApiHandler: ImageApiHandler
    ): ImageAnalyzer {
        return ImageAnalyzer(imageProcessor, imageApiHandler) { _, _ ->}
    }
}