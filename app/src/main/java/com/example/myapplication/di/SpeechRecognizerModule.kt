package com.example.myapplication.di

import android.content.Context
import com.example.myapplication.SpeechRecognizerManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ApplicationContext

@Module
@InstallIn(ActivityComponent::class)
object SpeechRecognizerModule {

    @Provides
    fun provideSpeechRecognizerManager(
        @ApplicationContext
        context: Context
    ): SpeechRecognizerManager {
        return SpeechRecognizerManager(context, {}, {})
    }
}
