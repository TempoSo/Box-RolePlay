#include "LLMInference.h"
#include <jni.h>
#include <android/log.h>

extern "C" JNIEXPORT jlong JNICALL
Java_com_jegly_offlineLLM_smollm_SmolLM_loadModel(JNIEnv* env, jobject thiz, jstring modelPath, jfloat minP,
                                            jfloat temperature, jfloat topP, jint topK, jfloat repeatPenalty,
                                            jboolean storeChats, jlong contextSize,
                                            jstring chatTemplate, jint nThreads, jboolean useMmap, jboolean useMlock,
                                            jintArray engineOptions) {
    jboolean    isCopy           = true;
    const char* modelPathCstr    = env->GetStringUTFChars(modelPath, &isCopy);
    auto*       llmInference     = new LLMInference();
    const char* chatTemplateCstr = env->GetStringUTFChars(chatTemplate, &isCopy);

    EngineOptions options;
    if (engineOptions != nullptr) {
        jint* arr = env->GetIntArrayElements(engineOptions, nullptr);
        options.fromArray(arr, env->GetArrayLength(engineOptions));
        env->ReleaseIntArrayElements(engineOptions, arr, JNI_ABORT);
    }

    try {
        llmInference->loadModel(modelPathCstr, minP, temperature, topP, topK, repeatPenalty,
                                storeChats, contextSize, chatTemplateCstr, nThreads, useMmap, useMlock, options);
    } catch (std::exception& error) {
        env->ReleaseStringUTFChars(modelPath, modelPathCstr);
        env->ReleaseStringUTFChars(chatTemplate, chatTemplateCstr);
        delete llmInference;
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
        return 0;
    }

    env->ReleaseStringUTFChars(modelPath, modelPathCstr);
    env->ReleaseStringUTFChars(chatTemplate, chatTemplateCstr);
    return reinterpret_cast<jlong>(llmInference);
}

extern "C" JNIEXPORT void JNICALL
Java_com_jegly_offlineLLM_smollm_SmolLM_addChatMessage(JNIEnv* env, jobject thiz, jlong modelPtr, jstring message,
                                                 jstring role) {
    jboolean    isCopy       = true;
    const char* messageCstr  = env->GetStringUTFChars(message, &isCopy);
    const char* roleCstr     = env->GetStringUTFChars(role, &isCopy);
    auto*       llmInference = reinterpret_cast<LLMInference*>(modelPtr);
    llmInference->addChatMessage(messageCstr, roleCstr);
    env->ReleaseStringUTFChars(message, messageCstr);
    env->ReleaseStringUTFChars(role, roleCstr);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_jegly_offlineLLM_smollm_SmolLM_getResponseGenerationSpeed(JNIEnv* env, jobject thiz, jlong modelPtr) {
    auto* llmInference = reinterpret_cast<LLMInference*>(modelPtr);
    return llmInference->getResponseGenerationTime();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_jegly_offlineLLM_smollm_SmolLM_getContextSizeUsed(JNIEnv* env, jobject thiz, jlong modelPtr) {
    auto* llmInference = reinterpret_cast<LLMInference*>(modelPtr);
    return llmInference->getContextSizeUsed();
}

extern "C" JNIEXPORT void JNICALL
Java_com_jegly_offlineLLM_smollm_SmolLM_close(JNIEnv* env, jobject thiz, jlong modelPtr) {
    auto* llmInference = reinterpret_cast<LLMInference*>(modelPtr);
    delete llmInference;
}

extern "C" JNIEXPORT void JNICALL
Java_com_jegly_offlineLLM_smollm_SmolLM_startCompletion(JNIEnv* env, jobject thiz, jlong modelPtr, jstring prompt) {
    jboolean    isCopy       = true;
    const char* promptCstr   = env->GetStringUTFChars(prompt, &isCopy);
    auto*       llmInference = reinterpret_cast<LLMInference*>(modelPtr);
    try {
        llmInference->startCompletion(promptCstr);
    } catch (std::exception& error) {
        env->ReleaseStringUTFChars(prompt, promptCstr);
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
        return;
    }
    env->ReleaseStringUTFChars(prompt, promptCstr);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_jegly_offlineLLM_smollm_SmolLM_completionLoop(JNIEnv* env, jobject thiz, jlong modelPtr) {
    auto* llmInference = reinterpret_cast<LLMInference*>(modelPtr);
    try {
        std::string response = llmInference->completionLoop();
        return env->NewStringUTF(response.c_str());
    } catch (std::exception& error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_jegly_offlineLLM_smollm_SmolLM_stopCompletion(JNIEnv* env, jobject thiz, jlong modelPtr) {
    auto* llmInference = reinterpret_cast<LLMInference*>(modelPtr);
    llmInference->stopCompletion();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_jegly_offlineLLM_smollm_SmolLM_benchModel(JNIEnv* env, jobject /*unused*/, jlong modelPtr, jint pp, jint tg, jint pl,
                                             jint nr) {
    auto*       llmInference = reinterpret_cast<LLMInference*>(modelPtr);
    std::string result       = llmInference->benchModel(pp, tg, pl, nr);
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_jegly_offlineLLM_smollm_SmolLM_resetChat(JNIEnv* env, jobject thiz, jlong modelPtr) {
    auto* llmInference = reinterpret_cast<LLMInference*>(modelPtr);
    llmInference->resetChat();
}

extern "C" JNIEXPORT void JNICALL
Java_com_jegly_offlineLLM_smollm_SmolLM_setThinkingNative(JNIEnv* env, jobject thiz, jlong modelPtr,
                                                          jboolean enabled) {
    auto* llmInference = reinterpret_cast<LLMInference*>(modelPtr);
    llmInference->setThinking(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_jegly_offlineLLM_smollm_SmolLM_setContextWindow(JNIEnv* env, jobject thiz, jlong modelPtr, jfloat high,
                                                   jfloat low) {
    auto* llmInference = reinterpret_cast<LLMInference*>(modelPtr);
    llmInference->setContextWindow(high, low);
}

extern "C" JNIEXPORT void JNICALL
Java_com_jegly_offlineLLM_smollm_SmolLM_setSampler(JNIEnv* env, jobject thiz, jlong modelPtr, jfloatArray values) {
    auto* llmInference = reinterpret_cast<LLMInference*>(modelPtr);
    const jsize n = env->GetArrayLength(values);
    jfloat* data = env->GetFloatArrayElements(values, nullptr);
    SamplerSettings settings;
    if (settings.fromArray(data, (int) n)) {
        llmInference->setSampler(settings);
    }
    env->ReleaseFloatArrayElements(values, data, JNI_ABORT);
}

extern "C" JNIEXPORT void JNICALL
Java_com_jegly_offlineLLM_smollm_SmolLM_setMaxResponse(JNIEnv* env, jobject thiz, jlong modelPtr, jint maxTokens) {
    reinterpret_cast<LLMInference*>(modelPtr)->setMaxResponse(maxTokens);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_jegly_offlineLLM_smollm_SmolLM_getPerfStats(JNIEnv* env, jobject thiz, jlong modelPtr) {
    float v[4] = {0, 0, 0, 0};
    reinterpret_cast<LLMInference*>(modelPtr)->getPerf(v);
    jfloatArray out = env->NewFloatArray(4);
    env->SetFloatArrayRegion(out, 0, 4, v);
    return out;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_jegly_offlineLLM_smollm_SmolLM_loadMmproj(JNIEnv* env, jobject thiz, jlong modelPtr, jstring path, jint nThreads) {
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    bool ok = false;
    try {
        ok = reinterpret_cast<LLMInference*>(modelPtr)->loadMmproj(cpath, nThreads);
    } catch (const std::exception& e) {
        __android_log_print(ANDROID_LOG_ERROR, "SmolLM", "loadMmproj failed: %s", e.what());
    }
    env->ReleaseStringUTFChars(path, cpath);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_jegly_offlineLLM_smollm_SmolLM_supportsVision(JNIEnv* env, jobject thiz, jlong modelPtr) {
    return reinterpret_cast<LLMInference*>(modelPtr)->supportsVision() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_jegly_offlineLLM_smollm_SmolLM_addImage(JNIEnv* env, jobject thiz, jlong modelPtr, jbyteArray rgb, jint width, jint height) {
    jsize len = env->GetArrayLength(rgb);
    if ((long) len < (long) width * height * 3) return JNI_FALSE;
    jbyte* data = env->GetByteArrayElements(rgb, nullptr);
    bool ok = reinterpret_cast<LLMInference*>(modelPtr)->addImage(width, height, reinterpret_cast<const unsigned char*>(data));
    env->ReleaseByteArrayElements(rgb, data, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_jegly_offlineLLM_smollm_SmolLM_mediaMarkerNative(JNIEnv* env, jobject thiz) {
    return env->NewStringUTF(LLMInference::mediaMarker());
}
