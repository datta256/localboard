#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <chrono>
#include <android/log.h>
#include "llama.cpp/include/llama.h"

#define TAG "LocalboardNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

struct LlamaState {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    bool is_loaded = false;
    bool should_stop = false;
} g_state;

extern "C" JNIEXPORT void JNICALL
Java_com_example_localboard_LlamaInferenceService_nativeUnloadModel(JNIEnv*, jobject) {

    if (g_state.ctx) {
        llama_free(g_state.ctx);
        g_state.ctx = nullptr;
    }

    if (g_state.model) {
        llama_model_free(g_state.model);
        g_state.model = nullptr;
    }

    g_state.is_loaded = false;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_localboard_LlamaInferenceService_nativeLoadModel(
        JNIEnv* env,
        jobject,
        jstring model_path) {

    const char* path = env->GetStringUTFChars(model_path, nullptr);

    static bool backend_init = false;
    if (!backend_init) {
        llama_backend_init();
        backend_init = true;
    }

    llama_model_params model_params = llama_model_default_params();
    model_params.use_mmap = true;

    g_state.model = llama_model_load_from_file(path, model_params);

    if (!g_state.model) {
        env->ReleaseStringUTFChars(model_path, path);
        return false;
    }

    llama_context_params ctx_params = llama_context_default_params();

    ctx_params.n_ctx = 2048;
    ctx_params.n_batch = 1024;
    ctx_params.n_threads = 8;
    ctx_params.n_threads_batch = 8;

    g_state.ctx = llama_init_from_model(g_state.model, ctx_params);

    g_state.is_loaded = true;

    env->ReleaseStringUTFChars(model_path, path);
    return true;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_localboard_LlamaInferenceService_nativeGenerate(
        JNIEnv* env,
        jobject,
        jstring prompt,
        jobject callback) {

    if (!g_state.is_loaded || !g_state.ctx) return;

    llama_memory_clear(llama_get_memory(g_state.ctx), true);

    const char* user_p = env->GetStringUTFChars(prompt, nullptr);

    // CLEAN PROMPT FORMAT (works with Phi-3 + TinyLlama)
    std::string formatted_prompt =
            "<|user|>\n" + std::string(user_p) + "\n<|assistant|>\n";

    jclass callbackClass = env->GetObjectClass(callback);

    jmethodID onTokenGenerated =
            env->GetMethodID(callbackClass, "onTokenGenerated", "(Ljava/lang/String;)V");

    jmethodID onStatsUpdated =
            env->GetMethodID(callbackClass, "onStatsUpdated", "(F)V");

    jmethodID onComplete =
            env->GetMethodID(callbackClass, "onComplete", "()V");

    g_state.should_stop = false;

    const struct llama_vocab * vocab = llama_model_get_vocab(g_state.model);

    std::vector<llama_token> tokens_list(2048);

    int n_tokens = llama_tokenize(
            vocab,
            formatted_prompt.c_str(),
            formatted_prompt.length(),
            tokens_list.data(),
            tokens_list.size(),
            true,
            false);

    tokens_list.resize(n_tokens);

    llama_batch batch = llama_batch_init(1024, 0, 1);

    for (int i = 0; i < n_tokens; ++i) {

        batch.token[i] = tokens_list[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = (i == n_tokens - 1);
    }

    batch.n_tokens = n_tokens;

    if (llama_decode(g_state.ctx, batch) != 0) {
        llama_batch_free(batch);
        env->ReleaseStringUTFChars(prompt, user_p);
        return;
    }

    auto * samplers = llama_sampler_chain_init(llama_sampler_chain_default_params());

    llama_sampler_chain_add(samplers, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(samplers, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(samplers, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(samplers, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    int n_cur = n_tokens;
    int generated = 0;

    char buf[128];

    auto start_time = std::chrono::high_resolution_clock::now();

    while (n_cur < 2048 && generated < 256 && !g_state.should_stop) {

        const llama_token id = llama_sampler_sample(samplers, g_state.ctx, -1);

        llama_sampler_accept(samplers, id);

        if (llama_vocab_is_eog(vocab, id)) break;

        int n = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);

        if (n > 0) {

            std::string piece(buf, n);

            // HARD STOP if model tries to start new role
            if (piece.find("<|") != std::string::npos ||
                piece.find("User:") != std::string::npos ||
                piece.find("Assistant:") != std::string::npos) {
                break;
            }

            jstring jtoken = env->NewStringUTF(piece.c_str());

            env->CallVoidMethod(callback, onTokenGenerated, jtoken);

            env->DeleteLocalRef(jtoken);
        }

        batch.n_tokens = 1;

        batch.token[0] = id;
        batch.pos[0] = n_cur;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = true;

        if (llama_decode(g_state.ctx, batch) != 0) break;

        n_cur++;
        generated++;

        auto current_time = std::chrono::high_resolution_clock::now();

        std::chrono::duration<float> elapsed = current_time - start_time;

        if (elapsed.count() > 0.05f) {

            float tps = generated / elapsed.count();

            env->CallVoidMethod(callback, onStatsUpdated, tps);
        }
    }

    llama_sampler_free(samplers);

    llama_batch_free(batch);

    env->CallVoidMethod(callback, onComplete);

    env->ReleaseStringUTFChars(prompt, user_p);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_localboard_LlamaInferenceService_nativeIsModelLoaded(JNIEnv*, jobject) {
    return (jboolean)g_state.is_loaded;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_localboard_LlamaInferenceService_nativeStopGeneration(JNIEnv*, jobject) {
    g_state.should_stop = true;
}