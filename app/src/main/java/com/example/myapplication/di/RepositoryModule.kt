package com.example.myapplication.di

import com.example.myapplication.db.ImageDescriptionDao
import com.example.myapplication.network.OpenAiApiService
import com.example.myapplication.repo.ImageRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideImageRepository(
        imageDescriptionDao: ImageDescriptionDao,
        openAiApiService: OpenAiApiService
    ): ImageRepository {
        return ImageRepository(imageDescriptionDao, openAiApiService)
    }
}
