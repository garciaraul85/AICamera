package com.example.myapplication.util

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ImageAnalyzer @Inject constructor(
    private val imageProcessor: ImageProcessor,
    private val imageApiHandler: ImageApiHandler,
    var onImageEncoded: (Bitmap, String) -> Unit
) : ImageAnalysis.Analyzer {
    private var lastCallTimestamp = 0L

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()

        if (currentTimestamp - lastCallTimestamp >= TimeUnit.SECONDS.toMillis(5)) {
            lastCallTimestamp = currentTimestamp
            val mediaImage = image.image
            if (mediaImage != null) {
                val imageRotationDegrees = image.imageInfo.rotationDegrees
                val bitmap = imageProcessor.imageProxyToBitmap(image, imageRotationDegrees)
                processImage(bitmap)
                image.close()
            }
        } else {
            image.close()
        }
    }

    private fun processImage(bitmap: Bitmap) {
//        onImageEncoded(bitmap)
        imageProcessor.encodeImageToBase64(bitmap) { base64Image ->
            base64Image?.let {
                onImageEncoded(bitmap, it)
                CoroutineScope(Dispatchers.IO).launch {
                    imageApiHandler.streamImagesAndDescribe(it)
                }
            } ?: run {
                Log.e(TAG, "Failed to encode image to Base64")
            }
        }
    }

    companion object {
        const val TAG = "ImageAnalyzer"
    }
}