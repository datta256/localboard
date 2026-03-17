# ⌨️ Localboard: The Ultimate local LLM AI Keyboard for Android

[![GitHub license](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/yourusername/localboard/blob/master/LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](https://developer.android.com)
[![Engine](https://img.shields.io/badge/engine-llama.cpp-orange.svg)](https://github.com/ggerganov/llama.cpp)

**Localboard** is a production-grade, open-source Android keyboard that runs Large Language Models (LLMs) **100% locally and offline**. Powered by `llama.cpp`, it brings the power of Phi-3, TinyLlama, and SmolLM directly to your fingertips without ever sending data to the cloud.

Inspired by projects like **Picollama** and **OpenClaw**, Localboard is architected for speed, privacy, and seamless integration into any Android app.

---

## 🚀 Key Features

*   **Offline First**: No internet required. Zero latency from cloud servers. Total privacy.
*   **Multi-Process Architecture**: Inference runs in a dedicated background process (`:inference`) to ensure the keyboard UI remains 60FPS responsive even during heavy computation.
*   **AI Rewrite Mode**: Select text in any app (WhatsApp, Gmail, etc.) and use the keyboard to professionally rewrite, summarize, or fix grammar instantly.
*   **Turbo-Charged Performance**: 
    *   Optimized for **ARMv8.2-A** with `dotprod` and `fp16` hardware acceleration.
    *   **4-Thread Performance Core Binding** to prevent thermal throttling.
    *   **Q8_0 KV Cache** and **Flash Attention** for near-instant token streaming.
*   **Model Switcher**: Hot-swap between models (SmolLM-135M for speed, Phi-3-Mini for logic).
*   **Full QWERTY Layout**: Includes symbols, numbers, and shift support—designed to be a viable daily driver keyboard.

---

## 🛠 Tech Stack

*   **Language**: Kotlin (90%), C++ (10%)
*   **UI Framework**: Jetpack Compose
*   **Inference Engine**: [llama.cpp](https://github.com/ggerganov/llama.cpp) via JNI
*   **Inter-Process Communication**: AIDL (Android Interface Definition Language)
*   **Build System**: CMake with advanced ARM SIMD optimizations

---

## 📦 Supported Models (GGUF)

Localboard supports any GGUF-formatted model. Recommended models for mobile:
*   **SmolLM-135M**: ~90MB, 30+ tokens/sec (Ideal for Auto-complete).
*   **TinyLlama-1.1B**: ~640MB, 10-15 tokens/sec (Great for Chat).
*   **Phi-3-Mini-4k**: ~2.2GB, High reasoning quality (Production standard).

---

## 📥 Installation & Setup

1.  **Clone the Repo**:
    ```bash
    git clone https://github.com/yourusername/localboard.git
    cd localboard
    git submodule update --init --recursive
    ```
2.  **Build**: Open in Android Studio, switch build variant to **Release**, and build the APK.
3.  **Setup**:
    *   Open the app and click **"Download Model"**.
    *   Enable **Localboard** in Android Settings.
    *   Switch your active keyboard to **Localboard**.
4.  **Chat**: Tap the "Send" icon in the keyboard toolbar to start talking to your local AI.

---

## 🔧 Performance Tuning

To achieve **Picollama-level speeds**, we use several native optimizations in `native-lib.cpp`:
*   `use_mmap = true` for instant model loading.
*   `n_threads = 4` to lock inference to Performance Cores.
*   `llama_sampler` caching to remove per-token initialization overhead.

---

---

## 🎥 Demo (1m 32s)

<video controls muted style="max-height:640px; min-height: 200px" src="https://github.com/user-attachments/assets/ac5d0945-04e6-4a5f-b4f6-e5f37cfb68f4"></video>   

--- 

## 🤝 Contributing

Contributions are welcome! Whether it's adding emoji support, improving the QWERTY layout, or optimizing the Vulkan backend, feel free to open a PR.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

**Keywords**: *llama.cpp android, local llm android, offline ai keyboard, on-device machine learning, tinyllama android, phi-3 mobile, android ime ai, open source ai keyboard.*
