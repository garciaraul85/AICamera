package com.example.myapplication.repo

import com.example.myapplication.db.ImageDescription
import com.example.myapplication.db.ImageDescriptionDao
import com.example.myapplication.network.OpenAiApiService
import com.example.myapplication.network.OpenAiRequest
import javax.inject.Inject

class ImageRepository @Inject constructor(
    private val imageDescriptionDao: ImageDescriptionDao,
    private val openAiApiService: OpenAiApiService
) {

    suspend fun insertDescription(description: ImageDescription) {
        imageDescriptionDao.insertDescription(description)
    }

    suspend fun getLast20Descriptions(): List<ImageDescription> {
        return imageDescriptionDao.getLast20Descriptions()
    }

    suspend fun deleteOldest(count: Int) {
        imageDescriptionDao.deleteOldest(count)
    }

    suspend fun getChatCompletion(request: OpenAiRequest) = openAiApiService.getChatCompletion(request)
}
