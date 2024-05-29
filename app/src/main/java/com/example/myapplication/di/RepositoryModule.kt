package com.example.myapplication.di

import com.example.myapplication.db.ImageDescriptionDao
import com.example.myapplication.network.OpenAiApiService
import com.example.myapplication.network.TextToSpeechApiService
import com.example.myapplication.repo.ImageRepository
import com.example.myapplication.text2speech.TextToSpeechRepository
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

    @Provides
    @Singleton
    fun provideTextToSpeechRepository(
        textToSpeechApiService: TextToSpeechApiService
    ): TextToSpeechRepository {
        return TextToSpeechRepository(textToSpeechApiService)
    }
}
