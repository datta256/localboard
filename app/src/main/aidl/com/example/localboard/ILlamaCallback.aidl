package com.example.localboard;

interface ILlamaCallback {
    void onTokenGenerated(String token);
    void onStatsUpdated(float tps);
    void onError(String message);
    void onComplete();
}