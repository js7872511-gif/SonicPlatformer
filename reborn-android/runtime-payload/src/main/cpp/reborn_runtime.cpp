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
jfieldID gCurrentActivityField = nullptr;
jmethodID gInstallMethod = nullptr;
jmethodID gStatusMethod = nullptr;

void clearException(JNIEnv* env, const char* context) {
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        __android_log_print(ANDROID_LOG_ERROR, TAG, "JNI exception while %s", context);
    }
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

    gUnityPlayerClass = reinterpret_cast<jclass>(env->NewGlobalRef(unityLocal));
    gOverlayClass = reinterpret_cast<jclass>(env->NewGlobalRef(overlayLocal));
    env->DeleteLocalRef(unityLocal);
    env->DeleteLocalRef(overlayLocal);
    if (!gUnityPlayerClass || !gOverlayClass) return -6;

    gVm = vm;
    std::thread(initLoop).detach();
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_sonicredux_reborn_runtimeui_RebornOverlay_nativeToggleSuper(JNIEnv*, jclass) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "GO SUPER pressed (runtime bridge pending)");
}

extern "C" JNIEXPORT void JNICALL
Java_com_sonicredux_reborn_runtimeui_RebornOverlay_nativeToggleFlight(JNIEnv*, jclass) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "FLIGHT pressed (runtime bridge pending)");
}

extern "C" JNIEXPORT void JNICALL
Java_com_sonicredux_reborn_runtimeui_RebornOverlay_nativeSuperBoost(JNIEnv*, jclass) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "SUPER BOOST pressed (runtime bridge pending)");
}

extern "C" JNIEXPORT void JNICALL
Java_com_sonicredux_reborn_runtimeui_RebornOverlay_nativeChaosBurst(JNIEnv*, jclass) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "CHAOS BURST pressed (runtime bridge pending)");
}
