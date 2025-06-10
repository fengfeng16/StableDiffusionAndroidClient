package com.fengfeng16.stablediffusionapp

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

object ConfigStorage {
    private const val FILE_NAME = "config.json"
    private val gson = GsonBuilder()
        .setLenient()
        .create()

    fun save(context: Context, data: FormData) {
        runCatching {
            val json = gson.toJson(data)
            context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use {
                it.write(json.toByteArray())
            }
        }.onFailure {
            it.printStackTrace()
        }
    }

    fun load(context: Context): FormData {
        return try {
            val json = context.openFileInput(FILE_NAME).bufferedReader().readText()
            val type = object : TypeToken<FormData>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            FormData()
        }
    }
}