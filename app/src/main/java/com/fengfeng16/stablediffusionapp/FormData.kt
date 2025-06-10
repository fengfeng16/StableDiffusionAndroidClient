package com.fengfeng16.stablediffusionapp

data class FormData(
    val prompt: String = "",
    val negativePrompt: String = "",
    val promptPreset: String = "",
    val sampler: String = "DPM++ 2M",
    val schedule: String = "automatic",
    val steps: Int = 20,
    val width: Int = 512,
    val height: Int = 512,
    val batchCount: Int = 1,
    val cfgScale: Int = 70,
    val seed: Long = -1,
    val clipSkip: Int = 1,
    val model: String = "",
    val lora: List<LoraLoaded> = emptyList(),
    val vae: String = ""
)