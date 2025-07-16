package com.fengfeng16.stablediffusionapp

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.fengfeng16.stablediffusionapp.UnitCompose.DropdownWithReload
import com.fengfeng16.stablediffusionapp.UnitCompose.SliderWithInput
import com.fengfeng16.stablediffusionapp.UnitCompose.TextFieldWithLabel
import com.fengfeng16.stablediffusionapp.ui.theme.AppTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.round
import kotlin.random.Random

data class ConfigListBundle(
    val models: List<ModelSchema> = emptyList(),
    val samplers: List<SamplerSchema> = emptyList(),
    val schedules: List<ScheduleSchema> = emptyList(),
    val presets: List<PresetSchema> = emptyList(),
    val vaes: List<VAESchema> = emptyList(),
    val loras: List<LoraSchema> = emptyList(),
)

class ClientActivity : ComponentActivity() {
    private lateinit var serverUrl: String
    private val GSON = Gson()

    private var serviceBound = false
    private var service: GenerateService? = null
    var serviceState by mutableStateOf<GenerateService?>(null)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = (binder as GenerateService.LocalBinder).getService()
            service = svc
            serviceBound = true
            serviceState = svc
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            serviceBound = false
            serviceState = null
        }
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, GenerateService::class.java))
        } else {
            startService(Intent(this, GenerateService::class.java))
        }

        Intent(this, GenerateService::class.java).also {
            bindService(it, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (serviceBound) {
            unbindService(connection)
            serviceBound = false
        }

        stopService(Intent(this, GenerateService::class.java))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serverUrl = intent.getStringExtra("server_url").toString()
        setContent {
            AppTheme {
                val scope = rememberCoroutineScope()

                var models by remember { mutableStateOf<List<ModelSchema>>(emptyList()) }
                var samplers by remember { mutableStateOf<List<SamplerSchema>>(emptyList()) }
                var schedules by remember { mutableStateOf<List<ScheduleSchema>>(emptyList()) }
                var presets by remember { mutableStateOf<List<PresetSchema>>(emptyList()) }
                var vaes by remember { mutableStateOf<List<VAESchema>>(emptyList()) }
                var loras by remember { mutableStateOf<List<LoraSchema>>(emptyList()) }

                LaunchedEffect(serviceState) {
                    models = loadModelList()
                    samplers = loadSamplerList()
                    schedules = loadScheduleList()
                    presets = loadPresetList()
                    vaes = loadVAEList()
                    loras = loadLoraList()
                }
                fun reloadList(key: String) {
                    scope.launch {
                        when (key) {
                            "model" -> models = loadModelList()
                            "sampler" -> samplers = loadSamplerList()
                            "schedule" -> schedules = loadScheduleList()
                            "promptPreset" -> presets = loadPresetList()
                            "vae" -> vaes = loadVAEList()
                            "lora" -> loras = loadLoraList()
                        }
                    }
                }

                val lb = ConfigListBundle(
                    models = models,
                    samplers = samplers,
                    schedules = schedules,
                    presets = presets,
                    vaes = vaes,
                    loras = loras
                )
                val genImg = service?.let { it::generateImage }
                    ?: { _: String, _: String, _: (List<GeneratedImage>) -> Unit -> }

                val startPolling = service?.let { it::startPolling }
                    ?: { _: String, _: (Float, GeneratedImage?, String?, Int?) -> Unit -> }

                val endPolling = service?.let { it::endPolling }
                    ?: { }
                ClientScreen(
                    url = serverUrl ?: "",
                    listBundle = lb,
                    reloadList = ::reloadList,
                    genImg = genImg,
                    startPolling = startPolling,
                    endPolling = endPolling
                )
            }
        }
    }

    private suspend fun loadModelList(): List<ModelSchema> {
        val result = HttpHelper.request(url = "$serverUrl/sdapi/v1/sd-models")
            ?: return emptyList()

        return try {
            val type = object : TypeToken<List<ModelSchema>>() {}.type
            GSON.fromJson(result, type)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private suspend fun loadSamplerList(): List<SamplerSchema> {
        val result = HttpHelper.request(url = "$serverUrl/sdapi/v1/samplers")
            ?: return emptyList()

        return try {
            val type = object : TypeToken<List<SamplerSchema>>() {}.type
            GSON.fromJson(result, type)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private suspend fun loadScheduleList(): List<ScheduleSchema> {
        val result = HttpHelper.request(url = "$serverUrl/sdapi/v1/schedulers")
            ?: return emptyList()

        return try {
            val type = object : TypeToken<List<ScheduleSchema>>() {}.type
            GSON.fromJson(result, type)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private suspend fun loadPresetList(): List<PresetSchema> {
        val result = HttpHelper.request(url = "$serverUrl/sdapi/v1/prompt-styles")
            ?: return emptyList()

        return try {
            val type = object : TypeToken<List<PresetSchema>>() {}.type
            GSON.fromJson(result, type)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private suspend fun loadVAEList(): List<VAESchema> {
        val result = HttpHelper.request(url = "$serverUrl/sdapi/v1/sd-vae")
            ?: return emptyList()

        return try {
            val type = object : TypeToken<List<VAESchema>>() {}.type
            GSON.fromJson(result, type)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private suspend fun loadLoraList(): List<LoraSchema> {
        val result = HttpHelper.request(url = "$serverUrl/sdapi/v1/loras")
            ?: return emptyList()

        return try {
            val type = object : TypeToken<List<LoraSchema>>() {}.type
            GSON.fromJson(result, type)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}


@Composable
fun ClientScreen(
    url: String,
    listBundle: ConfigListBundle,
    reloadList: (key: String) -> Unit,
    genImg: (
        (url: String, body: String, onDone: (List<GeneratedImage>) -> Unit) -> Unit
    )? = null,
    startPolling: (
        (url: String, onResult: (Float, GeneratedImage?, String?, Int?) -> Unit) -> Unit
    )? = null,
    endPolling: (
        () -> Unit
    )? = null
) {
    val context = LocalContext.current

    var formData: FormData by remember {
        mutableStateOf(ConfigStorage.load(context))
    }
    var configPageState by remember {
        mutableStateOf(false)
    }

    val imageList = remember { mutableStateListOf<GeneratedImage>() }
    val loadingImageList = remember { mutableStateListOf<GeneratedImage>() }

    var isLoading by remember {
        mutableStateOf(false)
    }

    var progress by remember {
        mutableFloatStateOf(0f)
    }

    var selectedImg by remember {
        mutableIntStateOf(0)
    }

    fun getData(key: String): Any? {
        return when (key) {
            "prompt" -> formData.prompt
            "negativePrompt" -> formData.negativePrompt
            "promptPreset" -> formData.promptPreset
            "sampler" -> formData.sampler
            "schedule" -> formData.schedule
            "steps" -> formData.steps
            "width" -> formData.width
            "height" -> formData.height
            "batchCount" -> formData.batchCount
            "cfgScale" -> formData.cfgScale
            "seed" -> formData.seed
            "clipSkip" -> formData.clipSkip
            "model" -> formData.model
            "lora" -> formData.lora
            "vae" -> formData.vae
            else -> null
        }
    }

    fun getSetDataFunc(): (String, Any) -> Unit {
        val debounceDelay = 1000L // 1 秒
        val handler = Handler(Looper.getMainLooper())
        var saveRunnable: Runnable? = null
        return fun(key: String, value: Any) {
            formData = when (key) {
                "prompt" -> formData.copy(prompt = value as String)
                "negativePrompt" -> formData.copy(negativePrompt = value as String)
                "promptPreset" -> formData.copy(promptPreset = value as String)
                "sampler" -> formData.copy(sampler = value as String)
                "schedule" -> formData.copy(schedule = value as String)
                "steps" -> formData.copy(steps = (value as Number).toInt())
                "width" -> formData.copy(width = (value as Number).toInt())
                "height" -> formData.copy(height = (value as Number).toInt())
                "batchCount" -> formData.copy(batchCount = (value as Number).toInt())
                "cfgScale" -> formData.copy(cfgScale = (value as Number).toInt())
                "seed" -> formData.copy(seed = (value as Number).toLong())
                "clipSkip" -> formData.copy(clipSkip = (value as Number).toInt())
                "model" -> formData.copy(model = value as String)
                "lora" -> formData.copy(lora = value as List<LoraLoaded>)
                "vae" -> formData.copy(vae = value as String)
                else -> formData
            }
            saveRunnable?.let { handler.removeCallbacks(it) }
            saveRunnable = Runnable {
                ConfigStorage.save(context, formData)
            }
            handler.postDelayed(saveRunnable!!, debounceDelay)
        }
    }

    fun changeConfigPageState(state: Boolean) {
        configPageState = state;
    }

    fun generateImage(form: FormData) {
        var prompt = form.prompt.trim()
        if (form.lora.isNotEmpty()) {
            val loraText =
                form.lora.joinToString(", ") { "<lora:${it.name}:${it.radio.toFloat() / 10f}>" }

            if (prompt.isNotEmpty() && !prompt.endsWith(",")) {
                prompt += ","
            }

            prompt = if (prompt.isEmpty()) {
                loraText
            } else {
                "$prompt $loraText"
            }
        }

        val requestBody = mapOf(
            "prompt" to prompt,
            "negative_prompt" to form.negativePrompt,
            "styles" to listOf(form.promptPreset),
            "sampler_name" to form.sampler,
            "scheduler" to form.schedule,
            "steps" to form.steps,
            "width" to form.width,
            "height" to form.height,
            "n_iter" to form.batchCount,
            "cfg_scale" to form.cfgScale / 10.0,
            "seed" to form.seed,
            "override_settings" to mapOf(
                "CLIP_stop_at_last_layers" to form.clipSkip,
                "sd_model_checkpoint" to form.model,
                "sd_vae" to form.vae
            ),
        )

        val json = Gson().toJson(requestBody)

        if (genImg != null) {
            genImg(url, json, {
                loadingImageList.clear()
                imageList.addAll(it)
                selectedImg = imageList.lastIndex
                isLoading = false;
                if (endPolling != null) {
                    endPolling()
                }
            })
            progress = 0f;
            isLoading = true

            var loadingImageIndex = 1
            if (startPolling != null) {
                startPolling(
                    url,
                    fun(prog: Float, image: GeneratedImage?, job: String?, jobCount: Int?) {
                        progress = prog
                        if (image != null) {
                            if (job != null && jobCount != null && jobCount > 1) {
                                val regex = Regex("""Batch (\d+) out of (\d+)""")
                                val match = regex.find(job)
                                val batchIndex = match?.groups?.get(1)?.value?.toIntOrNull() ?: 0

                                if (batchIndex != loadingImageIndex) {
                                    val missing = batchIndex - loadingImageIndex - 1
                                    if (missing > 0) {
                                        repeat(missing) {
                                            loadingImageList.add(
                                                Util.getEmptyImage(
                                                    512,
                                                    512,
                                                    0x00000000
                                                )
                                            )
                                        }
                                    }
                                    loadingImageIndex = batchIndex
                                    loadingImageList.add(image)
                                    selectedImg = (imageList.size + loadingImageList.size) - 1
                                }
                            } else {
                                if (loadingImageList.isNotEmpty()) {
                                    loadingImageList.removeAt(loadingImageList.lastIndex)
                                }
                                loadingImageList.add(image)
                            }

                        }
                    })
            }



            loadingImageList.add(Util.getEmptyImage(512, 512, 0x00000000))
            selectedImg = (imageList.size + loadingImageList.size) - 1
        }
    }

    fun deleteImg(index: Int) {
        if (index < selectedImg) {
            selectedImg--
        } else if (index == selectedImg) {
            val lastIndex = (imageList.size + loadingImageList.size) - 1
            if (selectedImg == lastIndex) {
                selectedImg--
            }
        }
        if (index >= imageList.size) {
            loadingImageList.removeAt(index - imageList.size)
        } else {
            imageList.removeAt(index)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        BottomButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .align(Alignment.BottomCenter),
            changeConfigPageState = ::changeConfigPageState,
            onGenerate = {
                generateImage(formData)
            }
        )
        ImageShower(
            images = imageList + loadingImageList,
            onDelete = ::deleteImg,
            selectedImg = selectedImg,
            changeSelectedImg = {
                selectedImg = it
            }
        )
        ConfigPage(
            getData = ::getData,
            setData = getSetDataFunc(),
            pageState = configPageState,
            modifier = Modifier.fillMaxSize(),
            changeConfigPageState = ::changeConfigPageState,
            reloadList = reloadList,
            listBundle = listBundle,
        )
        LoadingProgressBar(
            isLoading = isLoading,
            progress = progress
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ConfigHeader(changeConfigPageState: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(10.dp)
    ) {
        val keyboardController = LocalSoftwareKeyboardController.current

        Text(
            text = "编辑参数",
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .align(Alignment.CenterStart),
        )

        IconButton(
            onClick = {
                changeConfigPageState(false)
                keyboardController?.hide()
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
fun ConfigPage(
    getData: (String) -> Any?,
    setData: (String, Any) -> Unit,
    pageState: Boolean,
    modifier: Modifier = Modifier,
    changeConfigPageState: (Boolean) -> Unit,
    reloadList: (key: String) -> Unit,
    listBundle: ConfigListBundle
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val density = LocalDensity.current
    AnimatedVisibility(
        visible = pageState,
        enter = slideInVertically { fullHeight -> fullHeight },
        exit = slideOutVertically {
            with(density) { screenHeight.roundToPx() }
        },
        modifier = modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                    }
                }
            }
    ) {
        Column(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surface)
        ) {
            ConfigHeader(changeConfigPageState = changeConfigPageState)

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModelSelector(
                    list = listBundle.models,
                    getData = getData,
                    setData = setData,
                    reloadList = reloadList
                )
                PromptInput(getData = getData, setData = setData)
                PresetSelector(
                    list = listBundle.presets,
                    getData = getData,
                    setData = setData,
                    reloadList = reloadList
                )
                SamplerSelector(
                    list = listBundle.samplers,
                    getData = getData,
                    setData = setData,
                    reloadList = reloadList
                )
                ScheduleSelector(
                    list = listBundle.schedules,
                    getData = getData,
                    setData = setData,
                    reloadList = reloadList
                )
                StepSlider(
                    getData = getData,
                    setData = setData
                )
                CFGSlider(
                    getData = getData,
                    setData = setData
                )
                SizeSlider(
                    getData = getData,
                    setData = setData
                )
                SeedInputField(
                    getData = getData,
                    setData = setData
                )
                BatchCountSlider(
                    getData = getData,
                    setData = setData
                )
                VAESelector(
                    list = listBundle.vaes,
                    getData = getData,
                    setData = setData,
                    reloadList = reloadList
                )
                ClipSlider(
                    getData = getData,
                    setData = setData
                )
                LoraComponent(
                    list = listBundle.loras,
                    getData = getData,
                    setData = setData,
                    reloadList = reloadList,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                )
            }
        }
    }
}

@Composable
fun LoraComponent(
    list: List<LoraSchema>,
    getData: (String) -> Any?,
    setData: (String, Any) -> Unit,
    reloadList: (key: String) -> Unit,
    modifier: Modifier
) {
    val min = 0f
    val max = 50f
    fun vl(it: Float): Float {
        var v = it
        v = round(v)
        return v
    }

    val loraKey = "lora"
    val loraList = (getData(loraKey) as? List<LoraLoaded>)?.toMutableStateList()
        ?: remember { mutableStateListOf() }

    var currentPath by remember { mutableStateOf("") }

    fun relativePath(schema: LoraSchema): String =
        schema.path.substringAfter("models\\Lora\\").replace("\\", "/")

    val visibleEntries = list.mapNotNull {
        val fullPath = relativePath(it)
        if (!fullPath.startsWith(currentPath)) return@mapNotNull null
        val remaining = fullPath.removePrefix(currentPath).trimStart('/')
        when {
            '/' !in remaining -> it to false // file
            else -> remaining.substringBefore("/") to true // folder
        }
    }.distinctBy { it.first }.sortedBy { it.first.toString() }

    Column(modifier = modifier) {
        Text(
            text = "Lora设置",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Column {
            loraList.forEachIndexed { index, lora ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(text = lora.name)
                        SliderWithInput(
                            label = "权重",
                            getData = { _ -> lora.radio },
                            setData = { _, value ->
                                val newRadio = (value as Number).toInt()
                                val updated = lora.copy(radio = newRadio)
                                loraList[index] = updated
                                setData(loraKey, loraList.toList())
                            },
                            dataKey = "lora_$index",
                            valueLimit = ::vl,
                            min = min,
                            max = max,
                            inputMultiply = 10f
                        )
                        IconButton(onClick = {
                            loraList.removeAt(index)
                            setData(loraKey, loraList.toList())
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
                        }
                    }
                }
            }
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            onClick = {
                reloadList("lora")
            }
        ) {
            Text(text = "Reload Lora List")
        }
        Text(
            color = MaterialTheme.colorScheme.onBackground,
            text = "/${currentPath.trim('/')}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(8.dp)
        )
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            if (currentPath.isNotEmpty()) {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    onClick = {
                        currentPath = currentPath.trim('/').split('/')
                            .dropLast(1).joinToString("/")
                    }
                ) {
                    Text(text = "../")
                }
            }

            visibleEntries.forEach { (entry, isFolder) ->
                if (isFolder) {
                    val folderName = entry as String
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        onClick = {
                            currentPath = listOf(currentPath.trimEnd('/'), folderName)
                                .filter { it.isNotEmpty() }.joinToString("/")
                        }
                    ) {
                        Text(text = "$folderName/")
                    }
                } else {
                    val schema = entry as LoraSchema
                    Button(
                        onClick = {
                            if (loraList.none { it.name == schema.name }) {
                                loraList.add(LoraLoaded(schema.name, 10))
                                setData(loraKey, loraList.toList())
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(text = relativePath(schema).substringAfterLast("/"))
                    }
                }
            }
        }
    }
}


@Composable
fun ModelSelector(
    list: List<ModelSchema>,
    getData: (String) -> Any?,
    setData: (String, Any) -> Unit,
    reloadList: (key: String) -> Unit
) {
    DropdownWithReload(
        label = "模型",
        options = list.map { it.model_name },
        dataKey = "model",
        getData = getData,
        setData = setData,
        reloadList = reloadList
    )
}

@Composable
fun PresetSelector(
    list: List<PresetSchema>,
    getData: (String) -> Any?,
    setData: (String, Any) -> Unit,
    reloadList: (key: String) -> Unit
) {
    val options = listOf("") + list.map { it.name }

    val nameMap = options.associate { option ->
        option to if (option.isBlank()) "无" else option
    }
    DropdownWithReload(
        label = "提示词预设",
        options = options,
        dataKey = "promptPreset",
        getData = getData,
        setData = setData,
        reloadList = reloadList,
        nameMap = nameMap
    )
}

@Composable
fun SamplerSelector(
    list: List<SamplerSchema>,
    getData: (String) -> Any?,
    setData: (String, Any) -> Unit,
    reloadList: (key: String) -> Unit
) {
    DropdownWithReload(
        label = "采样器",
        options = list.map { it.name },
        dataKey = "sampler",
        getData = getData,
        setData = setData,
        reloadList = reloadList
    )
}

@Composable
fun ScheduleSelector(
    list: List<ScheduleSchema>,
    getData: (String) -> Any?,
    setData: (String, Any) -> Unit,
    reloadList: (key: String) -> Unit
) {
    DropdownWithReload(
        label = "调度器",
        options = list.map { it.name },
        dataKey = "schedule",
        getData = getData,
        setData = setData,
        reloadList = reloadList,
        nameMap = list.associate { it.name to it.label }
    )
}

@Composable
fun VAESelector(
    list: List<VAESchema>,
    getData: (String) -> Any?,
    setData: (String, Any) -> Unit,
    reloadList: (key: String) -> Unit
) {
    DropdownWithReload(
        label = "VAE",
        options = list.map { it.model_name },
        dataKey = "vae",
        getData = getData,
        setData = setData,
        reloadList = reloadList,
    )
}

@Composable
fun PromptInput(
    getData: (String) -> Any?,
    setData: (String, Any) -> Unit,
) {
    TextFieldWithLabel(
        label = "正向提示词",
        dataKey = "prompt",
        getData = getData,
        setData = setData,
        modifier = Modifier
            .height(150.dp)
    )

    TextFieldWithLabel(
        label = "负面提示词",
        dataKey = "negativePrompt",
        getData = getData,
        setData = setData,
        modifier = Modifier
            .height(150.dp)
    )
}

@Composable
fun SizeSlider(
    getData: (String) -> Any?,
    setData: (String, Any) -> Unit,
) {
    var width by remember {
        mutableFloatStateOf((getData("width") as? Number)?.toFloat() ?: 0f)
    }

    var height by remember {
        mutableFloatStateOf((getData("height") as? Number)?.toFloat() ?: 0f)
    }

    var resetFlag by remember { mutableStateOf(false) }

    val min = 64f
    val max = 2048f
    fun vl(it: Float): Float {
        var v = it
        v = v.coerceIn(min, max)
        v = (v - min) / 8f
        v = round(v)
        v = v * 8f + min
        return v
    }

    fun changeSize(w: Float, h: Float) {
        width = w
        height = h
        resetFlag = !resetFlag
    }

    SliderWithInput(
        label = "宽度",
        setData = setData,
        getData = getData,
        dataKey = "width",
        valueLimit = ::vl,
        value = width,
        resetFlag = resetFlag,
        min = min,
        max = max,
        modifier = Modifier.fillMaxWidth(),
        isInt = true
    )

    SliderWithInput(
        label = "高度",
        setData = setData,
        getData = getData,
        dataKey = "height",
        valueLimit = ::vl,
        value = height,
        resetFlag = resetFlag,
        min = min,
        max = max,
        modifier = Modifier.fillMaxWidth(),
        isInt = true
    )
    Row(Modifier.fillMaxWidth()) {
        Button(
            onClick = {
                changeSize(1216f, 832f)
            },
            modifier = Modifier
                .weight(1f)
        ) {
            Text("横向 1216x832")
        }
        Spacer(Modifier.weight(0.1f))
        Button(
            onClick = {
                changeSize(832f, 1216f)
            },
            modifier = Modifier
                .weight(1f)
        ) {
            Text("纵向 832x1216")
        }
    }

    Button(
        onClick = {
            changeSize(1024f, 1024f)
        },
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Text("方形 1024x1024")
    }
}

@Composable
fun StepSlider(
    getData: (String) -> Any?,
    setData: (String, Any) -> Unit,
) {
    val min = 1f
    val max = 150f
    fun vl(it: Float): Float {
        var v = it
        v = v.coerceIn(min, max)
        v = round(v)
        return v
    }

    SliderWithInput(
        label = "迭代步数",
        getData = getData,
        setData = setData,
        dataKey = "steps",
        valueLimit = ::vl,
        min = min,
        max = max,
        isInt = true
    )
}

@Composable
fun CFGSlider(
    getData: (String) -> Any?,
    setData: (String, Any) -> Unit,
) {
    val min = 10f
    val max = 300f
    fun vl(it: Float): Float {
        var v = it
        v = v.coerceIn(min, max)
        v = round(v)
        return v
    }

    SliderWithInput(
        label = "CFG Scale",
        getData = getData,
        setData = setData,
        dataKey = "cfgScale",
        valueLimit = ::vl,
        min = min,
        max = max,
        inputMultiply = 10f,
    )
}

@Composable
fun ClipSlider(
    getData: (String) -> Any?,
    setData: (String, Any) -> Unit,
) {
    val min = 1f
    val max = 12f
    fun vl(it: Float): Float {
        var v = it
        v = v.coerceIn(min, max)
        v = round(v)
        return v
    }

    SliderWithInput(
        label = "Clip（负值）",
        getData = getData,
        setData = setData,
        dataKey = "clipSkip",
        valueLimit = ::vl,
        min = min,
        max = max,
        isInt = true
    )
}

@Composable
fun BatchCountSlider(
    getData: (String) -> Any?,
    setData: (String, Any) -> Unit,
) {
    val min = 1f
    val max = 12f
    fun vl(it: Float): Float {
        var v = it
        v = v.coerceIn(min, max)
        v = round(v)
        return v
    }

    SliderWithInput(
        label = "生成批次",
        getData = getData,
        setData = setData,
        dataKey = "batchCount",
        valueLimit = ::vl,
        min = min,
        max = max,
        isInt = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeedInputField(
    getData: (String) -> Any?,
    setData: (String, Any) -> Unit,
) {
    val dataKey = "seed"

    val focusManager = LocalFocusManager.current
    var text by remember {
        mutableStateOf((getData(dataKey) as? Number)?.toLong()?.toString() ?: "-1")
    }

    fun updateSeed(raw: Long) {
        val clamped = raw.coerceIn(-1, Long.MAX_VALUE)
        text = clamped.toString()
        setData(dataKey, clamped)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                if (it.matches(Regex("^-?\\d{0,19}$"))) {
                    text = it
                }
            },
            label = { Text("种子") },
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Number
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        val longValue = text.toLongOrNull()
                        if (longValue != null) updateSeed(longValue)
                    }
                }
        )

        Button(
            onClick = {
                val seed = Random.nextLong(0, Long.MAX_VALUE)
                updateSeed(seed)
            },
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(0.2f)
        ) {
            Icon(Icons.Default.Casino,
                contentDescription = "随机刷新种子",
                modifier = Modifier.size(22.dp)
            )
        }
        Button(
            onClick = {
                val seed = -1L
                updateSeed(seed)
            },
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(0.2f)
        ) {
            Icon(Icons.Default.Clear,
                contentDescription = "随机",
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun BottomButton(
    modifier: Modifier = Modifier,
    changeConfigPageState: (Boolean) -> Unit,
    onGenerate: () -> Unit
) {
    Row(
        modifier = modifier
    ) {
        Button(
            onClick = { changeConfigPageState(true) },
            modifier = Modifier
                .height(50.dp)
                .aspectRatio(1f)
                .align(Alignment.CenterVertically),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                modifier = Modifier.fillMaxSize(0.4f),
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit"
            )
        }
        Spacer(modifier = Modifier.weight(0.2f))
        Button(
            onClick = onGenerate,
            modifier = Modifier
                .weight(4f)
                .height(48.dp)
                .align(Alignment.CenterVertically)
        ) {
            Text(
                text = "生成",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
fun ImageShower(
    images: List<GeneratedImage>,
    onDelete: (index: Int) -> Unit,
    selectedImg: Int,
    changeSelectedImg: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 当前选中的图
    if (images.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("暂无图片", color = Color.White)
        }
        return
    }

    val currentImage = images[selectedImg]

    // 缩放与偏移控制
    val scale = remember { mutableStateOf(1f) }
    val offset = remember { mutableStateOf(Offset.Zero) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .background(Color.Black)
    ) {
        // 主图片显示区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .pointerInput(selectedImg) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale.value *= zoom
                        offset.value += pan
                    }
                }
        ) {
            Image(
                bitmap = currentImage.image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .graphicsLayer(
                        scaleX = scale.value,
                        scaleY = scale.value,
                        translationX = offset.value.x,
                        translationY = offset.value.y
                    )
                    .align(Alignment.Center)
            )

            // 下载按钮
            IconButton(
                onClick = {
                    scope.launch {
                        saveImageToDownloads(context, currentImage.base64)
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.5f), shape = CircleShape)
            ) {
                Icon(Icons.Default.Download, contentDescription = "Download")
            }
        }

        // 缩略图列表
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(images) { index, image ->
                Box {
                    Image(
                        bitmap = image.image,
                        contentDescription = "Thumbnail $index",
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (index == selectedImg) 3.dp else 1.dp,
                                color = if (index == selectedImg) Color.Cyan else Color.Gray,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                changeSelectedImg(index)
                                scale.value = 1f
                                offset.value = Offset.Zero
                            }
                    )
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .background(Color.Red, shape = CircleShape)
                            .clickable { onDelete(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

suspend fun saveImageToDownloads(context: Context, base64: String) {
    val bytes = try {
        Base64.decode(base64, Base64.DEFAULT)
    } catch (e: IllegalArgumentException) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "无效的 Base64 数据", Toast.LENGTH_SHORT).show()
        }
        return
    }

    val filename = "generated_${System.currentTimeMillis()}.png"

    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/StableDiffusion")
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "图片已保存至相册", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "保存失败，无法创建文件", Toast.LENGTH_SHORT).show()
            }
        }
    }
}


@Composable
fun LoadingProgressBar(
    isLoading: Boolean,
    progress: Float
) {
    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Color.LightGray)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(Color.Blue)
            )
        }
    }
}