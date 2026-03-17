package com.example.localboard

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.io.File

class LlamaInferenceService : Service() {

    private var currentModelPath: String? = null

    companion object {
        init {
            System.loadLibrary("localboard")
        }
    }

    private external fun nativeLoadModel(modelPath: String): Boolean
    private external fun nativeGenerate(prompt: String, callback: ILlamaCallback)
    private external fun nativeStopGeneration()
    private external fun nativeIsModelLoaded(): Boolean
    private external fun nativeUnloadModel()

    private val binder = object : ILlamaService.Stub() {
        override fun loadModel(modelPath: String?) {
            val path = if (modelPath.isNullOrEmpty()) {
                val dir = getExternalFilesDir(null)
                val files = dir?.listFiles { file -> file.extension == "gguf" }
                files?.firstOrNull()?.absolutePath
            } else {
                modelPath
            }

            if (path != null) {
                Log.d("LlamaService", "Loading model: $path")
                if (nativeIsModelLoaded()) {
                    nativeUnloadModel()
                }
                
                val success = nativeLoadModel(path)
                if (success) {
                    currentModelPath = path
                    Log.d("LlamaService", "Model loaded successfully: $path")
                } else {
                    currentModelPath = null
                    Log.e("LlamaService", "Failed to load model natively: $path")
                }
            }
        }

        override fun unloadModel() {
            Log.d("LlamaService", "Unloading model")
            nativeUnloadModel()
            currentModelPath = null
        }

        override fun generateText(prompt: String?, callback: ILlamaCallback?) {
            if (prompt != null && callback != null) {
                if (!nativeIsModelLoaded()) {
                    val dir = getExternalFilesDir(null)
                    val files = dir?.listFiles { file -> file.extension == "gguf" }
                    val autoPath = files?.firstOrNull()?.absolutePath
                    
                    if (autoPath != null) {
                        if (nativeLoadModel(autoPath)) {
                            currentModelPath = autoPath
                        }
                    } else {
                        callback.onError("No model loaded. Please download one in the app.")
                        return
                    }
                }

                Log.d("LlamaService", "Generating...")
                Thread {
                    try {
                        nativeGenerate(prompt, callback)
                    } catch (e: Exception) {
                        Log.e("LlamaService", "Crash in native generation", e)
                        callback.onError("Engine crash: ${e.message}")
                    }
                }.start()
            }
        }

        override fun stopGeneration() {
            nativeStopGeneration()
        }

        override fun isModelLoaded(): Boolean {
            return nativeIsModelLoaded()
        }

        override fun getLoadedModelPath(): String? {
            return if (nativeIsModelLoaded()) currentModelPath else null
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("LlamaService", "Inference Service Created")
    }
}