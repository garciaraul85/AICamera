package com.example.myapplication.di

import com.example.myapplication.network.AuthInterceptor
import com.example.myapplication.network.OpenAiApiService
import com.example.myapplication.network.TextToSpeechApiService
import com.example.myapplication.util.Constants.API_KEY
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        val logging = HttpLoggingInterceptor()
        logging.setLevel(HttpLoggingInterceptor.Level.BODY)
        return logging
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(): AuthInterceptor {
        return AuthInterceptor(API_KEY)
    }

    @Provides
    @Singleton
    fun provideEndpointLoggingInterceptor(): EndpointLoggingInterceptor {
        return EndpointLoggingInterceptor(HttpLoggingInterceptor.Logger.DEFAULT)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        authInterceptor: AuthInterceptor,
        endpointLoggingInterceptor: EndpointLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(endpointLoggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.openai.com/v1/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenAiApiService(retrofit: Retrofit): OpenAiApiService {
        return retrofit.create(OpenAiApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideTextToSpeechApiService(retrofit: Retrofit): TextToSpeechApiService {
        return retrofit.create(TextToSpeechApiService::class.java)
    }
}
