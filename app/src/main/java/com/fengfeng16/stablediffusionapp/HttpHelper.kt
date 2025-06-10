package com.fengfeng16.stablediffusionapp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object HttpHelper {
    private val httpClient = OkHttpClient()

    private val longHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.MINUTES)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(2, TimeUnit.MINUTES)
            .build()
    }

    suspend fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder().url(url)

            // 添加 headers
            headers.forEach { (k, v) -> builder.addHeader(k, v) }

            // 设置方法和请求体
            val request = when (method.uppercase()) {
                "GET" -> builder.get().build()
                "POST" -> builder.post(
                    body?.toRequestBody("application/json".toMediaType()) ?: "".toRequestBody()
                ).build()

                else -> throw IllegalArgumentException("Unsupported method: $method")
            }

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    println("Request failed: ${response.code}")
                    return@withContext null
                }

                return@withContext response.body?.string()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun longRequest(
        url: String,
        method: String = "Get",
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder().url(url)

            // 添加 headers
            headers.forEach { (k, v) -> builder.addHeader(k, v) }

            // 设置方法和请求体
            val request = when (method.uppercase()) {
                "GET" -> builder.get().build()
                "POST" -> builder.post(
                    body?.toRequestBody("application/json".toMediaType()) ?: "".toRequestBody()
                ).build()

                else -> throw IllegalArgumentException("Unsupported method: $method")
            }

            longHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.e(
                        "HttpHelper", """
                            Long request failed
                            → URL: $url
                            → METHOD: ${request.method}
                            → HEADERS: ${request.headers}
                            → REQUEST BODY: $body
                            → RESPONSE CODE: ${response.code}
                            → RESPONSE BODY: $responseBody
                        """.trimIndent()
                    )
                    return@withContext null
                }

                return@withContext response.body?.string()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return@withContext null
        }
    }
}