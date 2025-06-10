package com.fengfeng16.stablediffusionapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

object UnitCompose {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DropdownWithReload(
        label: String,
        options: List<String>,
        dataKey: String,
        getData: (String) -> Any?,
        setData: (String, Any) -> Unit,
        reloadList: (key: String) -> Unit,
        nameMap: Map<String, String> = emptyMap()
    ) {
        var expanded by remember { mutableStateOf(false) }
        val selectedKey = getData(dataKey) as? String ?: ""
        val selectedDisplay = nameMap[selectedKey] ?: selectedKey

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = selectedDisplay,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(label) },
                    trailingIcon = {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.clickable { expanded = true }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        options.forEach { option ->
                            val displayName = nameMap[option] ?: option
                            DropdownMenuItem(
                                text = { Text(displayName) },
                                onClick = {
                                    setData(dataKey, option)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { reloadList(dataKey) },
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(50.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "刷新",
                    modifier = Modifier.fillMaxSize(0.4f),
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TextFieldWithLabel(
        label: String,
        dataKey: String,
        getData: (String) -> Any?,
        setData: (String, Any) -> Unit,
        modifier: Modifier = Modifier
    ) {
        var text by remember { mutableStateOf(getData(dataKey)?.toString() ?: "") }

        // 同步外部状态变化（可选，如果你可能外部更新）
        LaunchedEffect(getData(dataKey)) {
            val newValue = getData(dataKey)?.toString() ?: ""
            if (newValue != text) {
                text = newValue
            }
        }

        TextField(
            value = text,
            onValueChange = {
                text = it
                setData(dataKey, it)
            },
            label = { Text(label) },
            modifier = modifier.fillMaxWidth()
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SliderWithInput(
        modifier: Modifier = Modifier,
        label: String,
        dataKey: String,
        getData: (String) -> Any?,
        setData: (String, Any) -> Unit,
        valueLimit: (Float) -> Float = { it },
        value: Float? = null,
        resetFlag: Boolean? = null,
        min: Float,
        max: Float,
        inputMultiply: Float = 1f,
        isInt: Boolean = false
    ) {
        val focusManager = LocalFocusManager.current

        var internalValue by remember {
            mutableFloatStateOf(
                value ?: (getData(dataKey) as? Number)?.toFloat() ?: min
            )
        }

        var textValue by remember {
            mutableStateOf(
                (internalValue / inputMultiply).toString()
            )
        }
        fun update(newVal: Float, fromInput: Boolean = false) {
            val v = if(fromInput) newVal * inputMultiply else newVal
            val limited = valueLimit(v)
            internalValue = limited

            val showValue = limited / inputMultiply
            textValue = (if(isInt) showValue.toInt() else showValue).toString()

            setData(dataKey, if(isInt) limited.toInt() else limited)
        }

        LaunchedEffect(value, resetFlag) {
            value?.let {
                update(it)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(end = 8.dp)
            )

            Slider(
                value = internalValue,
                onValueChange = { update(it) },
                valueRange = min..max,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )

            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                singleLine = true,
                modifier = Modifier
                    .width(80.dp)
                    .onFocusChanged {
                        if (!it.isFocused) {
                            textValue.toFloatOrNull()?.let { update(it, true) }
                        }
                    },
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Number
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                )
            )
        }
    }

}