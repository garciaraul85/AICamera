package com.example.myapplication.di

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException

class EndpointLoggingInterceptor(private val logger: HttpLoggingInterceptor.Logger) : Interceptor {

    private val httpLoggingInterceptor = HttpLoggingInterceptor(logger).apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Check if the request URL matches the specific endpoint
        val response: Response = if (request.url.encodedPath.endsWith("audio/speech")) {
            // Log request and response for the specific endpoint
            httpLoggingInterceptor.intercept(chain)
        } else {
            // Proceed without logging for other endpoints
            chain.proceed(request)
        }

        return response
    }
}
