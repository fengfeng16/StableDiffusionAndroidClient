package com.fengfeng16.stablediffusionapp

import androidx.compose.ui.graphics.ImageBitmap

data class ModelSchema(
    val title: String,
    val model_name: String,
)

data class SamplerSchema(
    val name: String
)

data class ScheduleSchema(
    val name: String,
    val label: String,
)

data class PresetSchema(
    val name: String,
)

data class VAESchema(
    val model_name: String,
)

data class LoraSchema(
    val name: String,
    val path: String
)

data class LoraLoaded(
    val name: String,
    val radio: Int
)

data class GeneratedImage(
    val image: ImageBitmap,
    val base64: String
)