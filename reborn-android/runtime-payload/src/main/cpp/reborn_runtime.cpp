#include "reborn_runtime.h"

#include <android/log.h>
#include <atomic>
#include <chrono>
#include <thread>

namespace {
constexpr const char* TAG = "RebornRuntime";
std::atomic<bool> gStarted{false};
JavaVM* gVm = nullptr;
jclass gUnityPlayerClass = nullptr;
jclass gOverlayClass = nullptr;
jclass gVisualBridgeClass = nullptr;
jfieldID gCurrentActivityField = nullptr;
jmethodID gInstallMethod = nullptr;
jmethodID gStatusMethod = nullptr;
jmethodID gVisualInstallMethod = nullptr;

void clearException(JNIEnv* env, const char* context) {
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        __android_log_print(ANDROID_LOG_ERROR, TAG, "JNI exception while %s", context);
    }
}

void postStatus(JNIEnv* env, const char* text) {
    if (!env || !gOverlayClass || !gStatusMethod || !text) return;
    jstring value = env->NewStringUTF(text);
    if (!value) return;
    env->CallStaticVoidMethod(gOverlayClass, gStatusMethod, value);
    env->DeleteLocalRef(value);
    clearException(env, "posting Re-born status");
}

void initLoop() {
    JNIEnv* env = nullptr;
    if (!gVm || gVm->AttachCurrentThread(&env, nullptr) != JNI_OK || !env) return;

    for (int attempt = 0; attempt < 200; ++attempt) {
        jobject activity = env->GetStaticObjectField(gUnityPlayerClass, gCurrentActivityField);
        if (env->ExceptionCheck()) {
            clearException(env, "reading UnityPlayer.currentActivity");
        } else if (activity) {
            // Beta 5 deliberately keeps startup and Super activation Java/JNI-only. No direct
            // IL2CPP renderer/material APIs are called from Re-born native code.
            env->CallStaticVoidMethod(gOverlayClass, gInstallMethod, activity);
            clearException(env, "installing Re-born overlay");
            env->CallStaticVoidMethod(gVisualBridgeClass, gVisualInstallMethod, activity);
            clearException(env, "installing crash-safe Super message adapter");
            postStatus(env, "REBORN RUNTIME ONLINE • SAFE GAME HOOK");
            env->DeleteLocalRef(activity);
            __android_log_print(ANDROID_LOG_INFO, TAG,
                                "Re-born runtime online; native IL2CPP Super visuals disabled");
            gVm->DetachCurrentThread();
            return;
        }
        if (activity) env->DeleteLocalRef(activity);
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }

    __android_log_print(ANDROID_LOG_WARN, TAG, "Unity activity was not ready before runtime timeout");
    gVm->DetachCurrentThread();
}
}

extern "C" int reborn_runtime_start(JavaVM* vm) {
    if (!vm) return -1;
    bool expected = false;
    if (!gStarted.compare_exchange_strong(expected, true)) return 0;

    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK || !env) return -2;

    jclass unityLocal = env->FindClass("com/unity3d/player/UnityPlayer");
    if (!unityLocal) {
        clearException(env, "finding UnityPlayer");
        return -3;
    }
    jclass overlayLocal = env->FindClass("com/sonicredux/reborn/runtimeui/RebornOverlay");
    if (!overlayLocal) {
        env->DeleteLocalRef(unityLocal);
        clearException(env, "finding RebornOverlay");
        return -4;
    }
    jclass visualLocal = env->FindClass("com/sonicredux/reborn/runtimeui/RebornVisualBridge");
    if (!visualLocal) {
        env->DeleteLocalRef(unityLocal);
        env->DeleteLocalRef(overlayLocal);
        clearException(env, "finding RebornVisualBridge");
        return -5;
    }

    gCurrentActivityField = env->GetStaticFieldID(unityLocal, "currentActivity", "Landroid/app/Activity;");
    gInstallMethod = env->GetStaticMethodID(overlayLocal, "install", "(Landroid/app/Activity;)V");
    gStatusMethod = env->GetStaticMethodID(overlayLocal, "setRuntimeStatus", "(Ljava/lang/String;)V");
    gVisualInstallMethod = env->GetStaticMethodID(visualLocal, "install", "(Landroid/app/Activity;)V");
    if (!gCurrentActivityField || !gInstallMethod || !gStatusMethod || !gVisualInstallMethod ||
            env->ExceptionCheck()) {
        env->DeleteLocalRef(unityLocal);
        env->DeleteLocalRef(overlayLocal);
        env->DeleteLocalRef(visualLocal);
        clearException(env, "resolving runtime Java bridge");
        return -6;
    }

    gUnityPlayerClass = reinterpret_cast<jclass>(env->NewGlobalRef(unityLocal));
    gOverlayClass = reinterpret_cast<jclass>(env->NewGlobalRef(overlayLocal));
    gVisualBridgeClass = reinterpret_cast<jclass>(env->NewGlobalRef(visualLocal));
    env->DeleteLocalRef(unityLocal);
    env->DeleteLocalRef(overlayLocal);
    env->DeleteLocalRef(visualLocal);
    if (!gUnityPlayerClass || !gOverlayClass || !gVisualBridgeClass) return -7;

    gVm = vm;
    std::thread(initLoop).detach();
    return 0;
}
