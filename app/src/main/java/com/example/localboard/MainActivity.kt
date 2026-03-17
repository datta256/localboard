package com.example.localboard

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.localboard.ui.theme.LocalboardTheme
import java.io.File

data class ModelOption(
    val name: String,
    val size: String,
    val url: String,
    val filename: String
)

class MainActivity : ComponentActivity() {
    private val modelOptions = listOf(
        ModelOption("SmolLM-135M (Ultra Fast)", "91MB", "https://huggingface.co/bartowski/SmolLM-135M-Instruct-v0.2-GGUF/resolve/main/SmolLM-135M-Instruct-v0.2-Q8_0.gguf", "smollm_135m.gguf"),
        ModelOption("TinyLlama-1.1B (Good Balance)", "640MB", "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf", "tinyllama_1.1b.gguf"),
        ModelOption("Phi-3-Mini (High Quality)", "2.2GB", "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf", "phi3_3.8b.gguf")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LocalboardTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(16.dp)
                            .fillMaxSize()
                    ) {
                        Text(
                            text = "Localboard AI Setup",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text("Available Models:", style = MaterialTheme.typography.titleMedium)
                        
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(modelOptions) { model ->
                                ModelItem(model)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("1. Enable Keyboard in Settings")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Button(
                            onClick = {
                                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.showInputMethodPicker()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("2. Switch to Localboard")
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        
                        var testText by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = testText,
                            onValueChange = { testText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Tap here to test keyboard...") }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun ModelItem(model: ModelOption) {
        val file = File(getExternalFilesDir(null), model.filename)
        var exists by remember { mutableStateOf(file.exists()) }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (exists) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.name, style = MaterialTheme.typography.bodyLarge)
                    Text("Size: ${model.size}", style = MaterialTheme.typography.bodySmall)
                }
                
                if (!exists) {
                    Button(onClick = { downloadModel(model) }) {
                        Text("Download")
                    }
                } else {
                    Text("Downloaded", color = Color(0xFF2E7D32), modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }

    private fun downloadModel(model: ModelOption) {
        val request = DownloadManager.Request(Uri.parse(model.url))
            .setTitle("Downloading ${model.name}")
            .setDescription("GGUF model for Localboard")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(this, null, model.filename)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
    }
}