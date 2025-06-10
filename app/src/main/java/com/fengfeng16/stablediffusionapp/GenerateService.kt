package com.fengfeng16.stablediffusionapp

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.Image
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class GenerateService : Service() {

    private val binder = LocalBinder()
    private var pollingJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isForegroundStarted = false

    inner class LocalBinder : Binder() {
        fun getService(): GenerateService = this@GenerateService
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceWithNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isForegroundStarted) {
            startForegroundServiceWithNotification()
            isForegroundStarted = true
        }
        return START_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "generate_service_channel"
        val channelName = "Image Generation Service"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, ClientActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("生成服务正在运行")
            .setContentText("休息中")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "generate_service_channel"

        val intent = Intent(this, ClientActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("生成服务正在运行")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(1, notification)
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        serviceScope.cancel()
        pollingJob?.cancel()
        super.onDestroy()
    }

    fun startPolling(url: String, onResult: (progress: Float, image: GeneratedImage?) -> Unit) {
        pollingJob?.cancel()

        pollingJob = serviceScope.launch {
            while (isActive) {
                try {
                    val result = HttpHelper.request("$url/sdapi/v1/progress") ?: continue

                    val json = JSONObject(result)
                    val progress = json.optDouble("progress", 0.0).toFloat()
                    val imageBase64 = json.optString("current_image", null.toString())

                    var image: GeneratedImage? = null

                    if (!imageBase64.isNullOrEmpty() && imageBase64 != "null") {
                        val bytes = Base64.decode(imageBase64, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        val img = bitmap?.asImageBitmap()
                        if (img != null) {
                            image = GeneratedImage(image = img, base64 = imageBase64)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        onResult(progress, image)
                        updateNotification("进度：${(progress * 100).toInt()}%")
                    }
                } catch (e: Exception) {
                    Log.e("GenerateService", "Polling error", e)
                }

                delay(1000)
            }
        }
    }

    fun endPolling() {
        pollingJob?.cancel()
        pollingJob = null
        updateNotification("休息中")
    }

    fun generateImage(url: String, body: String, onDone: (List<GeneratedImage>) -> Unit) {
        serviceScope.launch {
            val response = HttpHelper.longRequest(url = "$url/sdapi/v1/txt2img", body = body, method = "POST")
            if (response == null) return@launch

            val result = mutableListOf<GeneratedImage>()

            try {
                val json = JSONObject(response)
                val jsonArray = json.getJSONArray("images")

                for (i in 0 until jsonArray.length()) {
                    val base64 = jsonArray.getString(i)
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    val image = bitmap?.asImageBitmap()
                    if (image != null) {
                        result.add(GeneratedImage(image = image, base64 = base64))
                    } else {
                        Log.e("GenerateService", "Decode failed for image at index $i")
                    }
                }
            } catch (e: Exception) {
                Log.e("GenerateService", "Invalid JSON response: $response", e)
                return@launch
            }

            withContext(Dispatchers.Main) {
                onDone(result)
            }
        }
    }
}