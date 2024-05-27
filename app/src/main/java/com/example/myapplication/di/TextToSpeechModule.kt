package com.example.myapplication.di

import android.content.Context
import com.example.myapplication.text2speech.TextToSpeechManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object TextToSpeechModule {

    @Provides
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    @Provides
    fun provideTextToSpeechManager(
        @ApplicationContext
        context: Context,
        client: OkHttpClient
    ): TextToSpeechManager {
        return TextToSpeechManager(context, client)
    }
}
