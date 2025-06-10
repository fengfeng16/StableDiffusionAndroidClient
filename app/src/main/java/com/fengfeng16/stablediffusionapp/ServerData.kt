package com.fengfeng16.stablediffusionapp

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.serialization.encodeToString
import java.util.UUID

data class ServerEntry(
    val id: String = UUID.randomUUID().toString(),  // 用于识别、编辑
    val protocol: String,
    val host: String,
    val port: Int,
    val remark: String
)

object ServerStorage {
    private const val FILE_NAME = "servers.json"
    private val gson = Gson()

    fun save(context: Context, servers: List<ServerEntry>) {
        runCatching {
            val json = gson.toJson(servers)
            context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use {
                it.write(json.toByteArray())
            }
        }.onFailure {
            it.printStackTrace()
        }
    }

    fun load(context: Context): List<ServerEntry> {
        return try {
            val json = context.openFileInput(FILE_NAME).bufferedReader().readText()
            val type = object : TypeToken<List<ServerEntry>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
}