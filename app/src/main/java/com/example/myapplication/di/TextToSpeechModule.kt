package com.example.myapplication.di

import android.content.Context
import com.example.myapplication.text2speech.TextToSpeechManager
import com.example.myapplication.text2speech.TextToSpeechRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TextToSpeechModule {

    @Provides
    @Singleton
    fun provideTextToSpeechManager(
        @ApplicationContext context: Context,
        textToSpeechRepository: TextToSpeechRepository
    ): TextToSpeechManager {
        return TextToSpeechManager(context, textToSpeechRepository)
    }
}
