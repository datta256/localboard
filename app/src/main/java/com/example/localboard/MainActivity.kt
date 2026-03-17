package com.example.localboard

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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

    private var downloadManager: DownloadManager? = null
    private var downloadIdMap = mutableStateMapOf<String, Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        
        setContent {
            LocalboardTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var refreshing by remember { mutableStateOf(0) }
                    
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
                        
                        key(refreshing) {
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(modelOptions) { model ->
                                    ModelItem(model) { id -> 
                                        downloadIdMap[model.filename] = id
                                        refreshing++ 
                                    }
                                }
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

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(onDownloadComplete, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(onDownloadComplete, filter)
        }
    }

    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Trigger a UI refresh when any download finishes
            recreate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(onDownloadComplete)
    }

    @Composable
    fun ModelItem(model: ModelOption, onDownloadStarted: (Long) -> Unit) {
        val file = File(getExternalFilesDir(null), model.filename)
        var exists by remember { mutableStateOf(file.exists()) }
        val activeDownloadId = downloadIdMap[model.filename] ?: -1L
        var downloadProgress by remember { mutableStateOf(0f) }
        var isDownloading by remember { mutableStateOf(activeDownloadId != -1L) }

        if (isDownloading) {
            LaunchedEffect(activeDownloadId) {
                while (isDownloading) {
                    val query = DownloadManager.Query().setFilterById(activeDownloadId)
                    val cursor = downloadManager?.query(query)
                    if (cursor != null && cursor.moveToFirst()) {
                        val downloaded = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        if (total > 0) {
                            downloadProgress = downloaded.toFloat() / total.toFloat()
                        }
                        
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            isDownloading = false
                            exists = true
                            downloadIdMap.remove(model.filename)
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            isDownloading = false
                            downloadIdMap.remove(model.filename)
                        }
                    } else {
                        isDownloading = false
                    }
                    cursor?.close()
                    kotlinx.coroutines.delay(1000)
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (exists) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(model.name, style = MaterialTheme.typography.bodyLarge)
                        Text("Size: ${model.size}", style = MaterialTheme.typography.bodySmall)
                    }
                    
                    if (!exists) {
                        Button(
                            onClick = { 
                                val id = downloadModel(model)
                                onDownloadStarted(id)
                            },
                            enabled = !isDownloading
                        ) {
                            Text(if (isDownloading) "Starting..." else "Download")
                        }
                    } else {
                        Icon(Icons.Default.CheckCircle, "Done", tint = Color(0xFF2E7D32))
                    }
                }
                
                if (isDownloading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Progress: ${(downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }

    private fun downloadModel(model: ModelOption): Long {
        // Updated URL to point to the raw GGUF file directly
        val rawUrl = "https://huggingface.co/bartowski/SmolLM-135M-Instruct-v0.2-GGUF/resolve/main/SmolLM-135M-Instruct-v0.2-Q8_0.gguf?download=true"
        val request = DownloadManager.Request(Uri.parse(rawUrl))
            .setTitle("Localboard AI: ${model.name}")
            .setDescription("Downloading high-performance LLM model")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(this, null, model.filename)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        return downloadManager?.enqueue(request) ?: -1L
    }
}