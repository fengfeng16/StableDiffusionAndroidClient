package com.fengfeng16.stablediffusionapp

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import java.io.ByteArrayOutputStream

object Util {
    fun getEmptyImage(width: Int, height: Int, color: Int): GeneratedImage{
        val bitmap = createBitmap(width, height)
        bitmap.eraseColor(color) // 全透明

        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val pngBytes = byteArrayOutputStream.toByteArray()
        val base64 = Base64.encodeToString(pngBytes, Base64.NO_WRAP)
        val placeholder = bitmap.asImageBitmap()
        return GeneratedImage(image = placeholder, base64 = base64);
    }
}