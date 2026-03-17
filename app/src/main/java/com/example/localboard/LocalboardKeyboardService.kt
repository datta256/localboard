package com.example.localboard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.inputmethodservice.InputMethodService
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.*
import java.io.File

enum class KeyboardMode { NORMAL, ASK, REWRITE }
enum class KeySet { LETTERS, SYMBOLS, NUMBERS }

class LocalboardKeyboardService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private var llamaService: ILlamaService? = null
    private var generatedText by mutableStateOf("")
    private var isGenerating by mutableStateOf(false)
    private var userPrompt by mutableStateOf("")
    private var currentTps by mutableStateOf(0f)
    private var currentModelPath by mutableStateOf("")
    
    private var mode by mutableStateOf(KeyboardMode.NORMAL)
    private var keySet by mutableStateOf(KeySet.LETTERS)
    private var isShifted by mutableStateOf(false)
    
    private var showModelPicker by mutableStateOf(false)
    private var availableModels = mutableStateListOf<File>()

    private val callback = object : ILlamaCallback.Stub() {
        override fun onTokenGenerated(token: String?) { token?.let { generatedText += it } }
        override fun onStatsUpdated(tps: Float) { currentTps = tps }
        override fun onError(message: String?) { isGenerating = false }
        override fun onComplete() { isGenerating = false }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            llamaService = ILlamaService.Stub.asInterface(service)
            refreshAvailableModels()
            try {
                val path = llamaService?.getLoadedModelPath()
                if (path != null) {
                    currentModelPath = path
                } else if (availableModels.isNotEmpty()) {
                    loadModel(availableModels[0])
                }
            } catch (e: Exception) { Log.e("Keyboard", "Sync error", e) }
        }
        override fun onServiceDisconnected(name: ComponentName?) { llamaService = null }
    }

    private fun refreshAvailableModels() {
        val dir = getExternalFilesDir(null)
        availableModels.clear()
        dir?.listFiles { file -> file.extension == "gguf" }?.let { availableModels.addAll(it) }
    }

    private fun loadModel(file: File) {
        currentModelPath = file.absolutePath
        llamaService?.loadModel(file.absolutePath)
    }

    private fun handleKeyPress(text: String) {
        if (mode == KeyboardMode.NORMAL) {
            currentInputConnection?.commitText(text, 1)
        } else {
            userPrompt += text
        }
    }

    private fun handleBackspace() {
        if (mode == KeyboardMode.NORMAL) {
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        } else if (userPrompt.isNotEmpty()) {
            userPrompt = userPrompt.dropLast(1)
        }
    }

    override fun onCreate() {
        setTheme(androidx.appcompat.R.style.Theme_AppCompat_NoActionBar)
        super.onCreate()
        window.window?.decorView?.let {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeViewModelStoreOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        bindService(Intent(this, LlamaInferenceService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onEvaluateInputViewShown(): Boolean {
        return true
    }

    override fun onCreateInputView(): View {
        val root = FrameLayout(this)
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LocalboardKeyboardService)
            setViewTreeViewModelStoreOwner(this@LocalboardKeyboardService)
            setViewTreeSavedStateRegistryOwner(this@LocalboardKeyboardService)
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    KeyboardRoot()
                }
            }
        }
        root.addView(composeView)
        return root
    }

    @Composable
    fun KeyboardRoot() {
        Box(modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(Color(0xFF121212))
            .padding(bottom = 24.dp) // Added 24dp of space at the very bottom
        ) {
            Column {
                // Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolbarIcon(Icons.Default.Settings, mode == KeyboardMode.NORMAL) { mode = KeyboardMode.NORMAL }
                    ToolbarIcon(Icons.AutoMirrored.Filled.Send, mode == KeyboardMode.ASK) { mode = KeyboardMode.ASK }
                    ToolbarIcon(Icons.Default.Edit, mode == KeyboardMode.REWRITE) { 
                        mode = KeyboardMode.REWRITE 
                        val selected = currentInputConnection?.getSelectedText(0)
                        if (!selected.isNullOrEmpty()) userPrompt = "Rewrite this: $selected"
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    if (currentModelPath.isNotEmpty()) {
                        Text(
                            File(currentModelPath).name.take(12) + "...",
                            color = Color.Cyan,
                            fontSize = 10.sp,
                            modifier = Modifier.clickable { showModelPicker = true }
                        )
                    }
                }

                // AI Panel
                if (mode != KeyboardMode.NORMAL) {
                    Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                        AIPanelView()
                    }
                }

                // Keys Area
                KeyboardKeys()
            }

            if (showModelPicker) {
                ModelPicker(availableModels, currentModelPath, { loadModel(it); showModelPicker = false }, { showModelPicker = false })
            }
        }
    }

    @Composable
    fun AIPanelView() {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(60.dp).background(Color(0xFF222222), RoundedCornerShape(8.dp)).padding(8.dp)) {
                Text(if (userPrompt.isEmpty()) "Type prompt here..." else userPrompt, color = Color.White, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Column(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.White, RoundedCornerShape(4.dp)).padding(4.dp).verticalScroll(rememberScrollState())) {
                Text(generatedText, color = Color.Black, fontSize = 12.sp)
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Button(
                    onClick = { generatedText = ""; isGenerating = true; llamaService?.generateText(userPrompt, callback) },
                    enabled = !isGenerating && userPrompt.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("Go", fontSize = 10.sp) }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = { currentInputConnection?.commitText(generatedText, 1) },
                    enabled = generatedText.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("Insert", fontSize = 10.sp) }
            }
        }
    }

    @Composable
    fun KeyboardKeys() {
        val rows = when (keySet) {
            KeySet.LETTERS -> listOf(
                listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                listOf("⇧", "z", "x", "c", "v", "b", "n", "m", "⌫")
            )
            KeySet.SYMBOLS -> listOf(
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                listOf("@", "#", "$", "%", "&", "-", "+", "(", ")"),
                listOf("⇧", "*", "\"", "'", ":", ";", "!", "?", "⌫")
            )
            KeySet.NUMBERS -> listOf(
                listOf("~", "`", "|", "•", "√", "π", "÷", "×", "§", "∆"),
                listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}"),
                listOf("ABC", "\\", "[", "]", "<", ">", "«", "»", "⌫")
            )
        }

        Column(modifier = Modifier.padding(horizontal = 2.dp)) {
            rows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { key ->
                        Key(key, Modifier.weight(if (key == "⌫" || key == "⇧" || key == "ABC") 1.5f else 1f))
                    }
                }
            }
            
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Key(if (keySet == KeySet.LETTERS) "?123" else "ABC", modifier = Modifier.weight(1.5f))
                Key(" ", modifier = Modifier.weight(4f))
                Key(".", modifier = Modifier.weight(1f))
                Key("Enter", modifier = Modifier.weight(1.5f))
            }
        }
    }

    @Composable
    fun Key(label: String, modifier: Modifier = Modifier) {
        val finalLabel = if (isShifted && label.length == 1 && keySet == KeySet.LETTERS) label.uppercase() else label
        
        Box(
            modifier = modifier
                .padding(2.dp)
                .height(52.dp)
                .background(if (label == " " || label.length > 1) Color(0xFF4A4A4A) else Color(0xFF333333), RoundedCornerShape(6.dp))
                .clickable {
                    when (label) {
                        "⇧" -> {
                            if (keySet == KeySet.SYMBOLS) keySet = KeySet.NUMBERS
                            else if (keySet == KeySet.NUMBERS) keySet = KeySet.SYMBOLS
                            else isShifted = !isShifted
                        }
                        "⌫" -> handleBackspace()
                        " " -> handleKeyPress(" ")
                        "?123" -> keySet = KeySet.SYMBOLS
                        "ABC" -> keySet = KeySet.LETTERS
                        "Enter" -> currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                        else -> handleKeyPress(finalLabel)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(finalLabel, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
    }

    @Composable
    fun ToolbarIcon(icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = null, tint = if (selected) Color.Cyan else Color.Gray)
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        refreshAvailableModels()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    private val _viewModelStore = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = _viewModelStore
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        _viewModelStore.clear()
        unbindService(connection)
    }
}

@Composable
fun ModelPicker(models: List<File>, selectedPath: String, onModelSelected: (File) -> Unit, onDismiss: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black.copy(alpha = 0.95f)) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Switch Model", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                models.forEach { file ->
                    val isSelected = file.absolutePath == selectedPath
                    Button(
                        onClick = { onModelSelected(file) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isSelected) Color(0xFF2E7D32) else Color(0xFF444444))
                    ) { Row { Text(file.name, modifier = Modifier.weight(1f)); if (isSelected) Icon(Icons.Default.Check, null) } }
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.padding(top = 16.dp)) { Text("Close") }
        }
    }
}