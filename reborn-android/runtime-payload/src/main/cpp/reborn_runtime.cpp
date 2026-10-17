#include "reborn_runtime.h"
#include "super_visual_bridge.h"

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
jfieldID gCurrentActivityField = nullptr;
jmethodID gInstallMethod = nullptr;
jmethodID gStatusMethod = nullptr;

void clearException(JNIEnv* env, const char* context) {
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        __android_log_print(ANDROID_LOG_ERROR, TAG, "JNI exception while %s", context);
    }
}

jboolean nativeSetSuperVisualRequested(JNIEnv*, jclass, jboolean enabled) {
    reborn::request_super_visual(enabled == JNI_TRUE);
    return reborn::super_visual_bridge_ready() ? JNI_TRUE : JNI_FALSE;
}

jint nativeSuperVisualState(JNIEnv*, jclass) {
    return static_cast<jint>(reborn::super_visual_state());
}

bool registerOverlayNatives(JNIEnv* env, jclass overlayClass) {
    JNINativeMethod methods[] = {
            {const_cast<char*>("nativeSetSuperVisualRequested"),
             const_cast<char*>("(Z)Z"),
             reinterpret_cast<void*>(nativeSetSuperVisualRequested)},
            {const_cast<char*>("nativeSuperVisualState"),
             const_cast<char*>("()I"),
             reinterpret_cast<void*>(nativeSuperVisualState)},
    };
    if (env->RegisterNatives(overlayClass, methods,
                             static_cast<jint>(sizeof(methods) / sizeof(methods[0]))) != JNI_OK) {
        clearException(env, "registering Re-born native visual methods");
        return false;
    }
    return true;
}

void initLoop() {
    JNIEnv* env = nullptr;
    if (!gVm || gVm->AttachCurrentThread(&env, nullptr) != JNI_OK || !env) return;

    for (int attempt = 0; attempt < 200; ++attempt) {
        jobject activity = env->GetStaticObjectField(gUnityPlayerClass, gCurrentActivityField);
        if (env->ExceptionCheck()) {
            clearException(env, "reading UnityPlayer.currentActivity");
        } else if (activity) {
            env->CallStaticVoidMethod(gOverlayClass, gInstallMethod, activity);
            clearException(env, "installing Re-born overlay");

            jstring online = env->NewStringUTF("REBORN RUNTIME ONLINE");
            if (online) {
                env->CallStaticVoidMethod(gOverlayClass, gStatusMethod, online);
                env->DeleteLocalRef(online);
                clearException(env, "posting runtime status");
            }
            env->DeleteLocalRef(activity);
            __android_log_print(ANDROID_LOG_INFO, TAG, "Re-born runtime online");
            gVm->DetachCurrentThread();
            return;
        }
        if (activity) env->DeleteLocalRef(activity);
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }

    __android_log_print(ANDROID_LOG_WARN, TAG, "Unity activity was not ready before runtime timeout");
    gVm->DetachCurrentThread();
}

void bridgeLoop() {
    for (int attempt = 0; attempt < 300; ++attempt) {
        if (reborn::install_super_visual_bridge()) {
            __android_log_print(ANDROID_LOG_INFO, TAG, "Super visual bridge ready");
            return;
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }
    __android_log_print(ANDROID_LOG_WARN, TAG, "Super visual bridge was not ready before timeout");
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

    gCurrentActivityField = env->GetStaticFieldID(unityLocal, "currentActivity", "Landroid/app/Activity;");
    gInstallMethod = env->GetStaticMethodID(overlayLocal, "install", "(Landroid/app/Activity;)V");
    gStatusMethod = env->GetStaticMethodID(overlayLocal, "setRuntimeStatus", "(Ljava/lang/String;)V");
    if (!gCurrentActivityField || !gInstallMethod || !gStatusMethod || env->ExceptionCheck()) {
        env->DeleteLocalRef(unityLocal);
        env->DeleteLocalRef(overlayLocal);
        clearException(env, "resolving runtime Java bridge");
        return -5;
    }
    if (!registerOverlayNatives(env, overlayLocal)) {
        env->DeleteLocalRef(unityLocal);
        env->DeleteLocalRef(overlayLocal);
        return -6;
    }

    gUnityPlayerClass = reinterpret_cast<jclass>(env->NewGlobalRef(unityLocal));
    gOverlayClass = reinterpret_cast<jclass>(env->NewGlobalRef(overlayLocal));
    env->DeleteLocalRef(unityLocal);
    env->DeleteLocalRef(overlayLocal);
    if (!gUnityPlayerClass || !gOverlayClass) return -7;

    gVm = vm;
    std::thread(initLoop).detach();
    std::thread(bridgeLoop).detach();
    return 0;
}
