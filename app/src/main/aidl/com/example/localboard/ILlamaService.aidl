package com.example.localboard;

import com.example.localboard.ILlamaCallback;

interface ILlamaService {
    void loadModel(String modelPath);
    void unloadModel();
    void generateText(String prompt, ILlamaCallback callback);
    void stopGeneration();
    boolean isModelLoaded();
    String getLoadedModelPath(); // New: Query the active model path
}