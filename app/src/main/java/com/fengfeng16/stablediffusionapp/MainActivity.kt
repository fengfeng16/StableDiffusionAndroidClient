package com.fengfeng16.stablediffusionapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.fengfeng16.stablediffusionapp.ui.theme.AppTheme
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                ServerManagerScreen(context = this)
            }
        }
    }
}



@Composable
fun ServerManagerScreen(context: Context) {
    var servers by remember { mutableStateOf(ServerStorage.load(context)) }
    var editingServer by remember { mutableStateOf<ServerEntry?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var errorDialog by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(16.dp)
    ) {
        Button(
            onClick = {
                editingServer = null
                showForm = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.add_server))
        }

        if (showForm) {
            ServerFormDialog(
                initial = editingServer,
                onDismiss = { showForm = false },
                onSubmit = { newEntry ->
                    servers = servers.toMutableList().apply {
                        removeAll { it.id == newEntry.id }
                        add(newEntry)
                    }
                    ServerStorage.save(context, servers)
                    showForm = false
                }
            )
        }

        errorDialog?.let { message ->
            ErrorDialog(message) { errorDialog = null }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn {
            items(servers) { server ->
                ServerCard(
                    entry = server,
                    onDelete = {
                        servers = servers.filterNot { it.id == server.id }
                        ServerStorage.save(context, servers)
                    },
                    onEdit = {
                        editingServer = server
                        showForm = true
                    },
                    onClick = {
                        loading = true
                        fetchServerData(server, onSuccess = {
                            loading = false
                            val intent = Intent(context, ClientActivity::class.java).apply {
                                putExtra("server_url", "${server.protocol}${server.host}:${server.port}")
                            }
                            context.startActivity(intent)
                        }, onError = {
                            loading = false
                            errorDialog = it
                        })
                    }
                )
            }
        }
    }

    if (loading) {
        LoadingOverlay()
    }
}

fun fetchServerData(
    server: ServerEntry,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    val client = OkHttpClient()
    val url = "${server.protocol}${server.host}:${server.port}/sdapi/v1/sd-models"
    val request = Request.Builder().url(url).build()

    CoroutineScope(Dispatchers.IO).launch {
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    onError("Server return error code: ${response.code}")
                } else {
                    onSuccess(response.body?.string() ?: "")
                }
            }
        } catch (e: Exception) {
            onError("Connect failed: ${e.message}")
        }
    }
}

@Composable
fun ServerFormDialog(initial: ServerEntry?, onDismiss: () -> Unit, onSubmit: (ServerEntry) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        title = { Text(if (initial == null) stringResource(R.string.add_server) else stringResource(R.string.edit_server)) },
        text = {
            AddServerForm(initial = initial, onSubmit = onSubmit)
        }
    )
}

@Composable
fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        },
        title = { Text(stringResource(R.string.connect_failed)) },
        text = { Text(message) }
    )
}

@Composable
fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) {},
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServerForm(initial: ServerEntry? = null, onSubmit: (ServerEntry) -> Unit) {
    val protocolOptions = listOf("http://", "https://")
    var protocol by remember { mutableStateOf(initial?.protocol ?: protocolOptions[0]) }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var portText by remember { mutableStateOf(initial?.port?.toString() ?: "7860") }
    var remark by remember { mutableStateOf(initial?.remark ?: "") }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DropdownMenuBox(
                selectedOption = protocol,
                options = protocolOptions,
                onOptionSelected = { protocol = it },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text(stringResource(R.string.address)) },
                modifier = Modifier.weight(1.4f)
            )
        }

        OutlinedTextField(
            value = portText,
            onValueChange = {
                if (it.all { c -> c.isDigit() }) {
                    val v = it.toIntOrNull()
                    if (v == null || v in 1..65535) portText = it
                }
            },
            label = { Text(stringResource(R.string.port)) },
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = remark,
            onValueChange = { remark = it },
            label = { Text(stringResource(R.string.title)) },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val port = portText.toIntOrNull() ?: return@Button
                onSubmit(
                    ServerEntry(
                        id = initial?.id ?: UUID.randomUUID().toString(),
                        protocol = protocol,
                        host = host,
                        port = port,
                        remark = remark
                    )
                )
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text(stringResource(R.string.save))
        }
    }
}

@Composable
fun ServerCard(entry: ServerEntry, onDelete: () -> Unit, onEdit: () -> Unit, onClick: () -> Unit) {
    var showReal by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(stringResource(R.string.title) + ": ${entry.remark}")
            val displayed = if (showReal) "${entry.protocol}${entry.host}:${entry.port}" else entry.protocol + entry.host.replace(Regex("(?<=.{3})."), "*") + ":${entry.port}"
            Text(stringResource(R.string.address) + ": $displayed")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    showReal = true
                                    tryAwaitRelease()
                                    showReal = false
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = stringResource(R.string.show))
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownMenuBox(
    selectedOption: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.protocol)) },
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}