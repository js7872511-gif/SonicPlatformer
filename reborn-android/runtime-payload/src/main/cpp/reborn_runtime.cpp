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

void postStatus(JNIEnv* env, const char* text) {
    if (!env || !gOverlayClass || !gStatusMethod || !text) return;
    jstring value = env->NewStringUTF(text);
    if (!value) return;
    env->CallStaticVoidMethod(gOverlayClass, gStatusMethod, value);
    env->DeleteLocalRef(value);
    clearException(env, "posting Re-born status");
}

void sendUnityMessage(JNIEnv* env, jmethodID unitySendMessage, const char* methodName) {
    if (!env || !unitySendMessage || !methodName) return;
    jstring objectName = env->NewStringUTF("Sonic");
    jstring method = env->NewStringUTF(methodName);
    jstring argument = env->NewStringUTF("");
    if (objectName && method && argument) {
        env->CallStaticVoidMethod(gUnityPlayerClass, unitySendMessage, objectName, method, argument);
        clearException(env, "dispatching UnitySendMessage");
    }
    if (objectName) env->DeleteLocalRef(objectName);
    if (method) env->DeleteLocalRef(method);
    if (argument) env->DeleteLocalRef(argument);
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
            postStatus(env, "REBORN RUNTIME ONLINE");
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

void stateSyncLoop() {
    JNIEnv* env = nullptr;
    if (!gVm || gVm->AttachCurrentThread(&env, nullptr) != JNI_OK || !env) return;

    jfieldID stateField = env->GetStaticFieldID(
            gOverlayClass, "superState", "Lcom/sonicredux/reborn/runtimeui/SuperRuntimeState;");
    if (!stateField || env->ExceptionCheck()) {
        clearException(env, "resolving SuperRuntimeState field");
        gVm->DetachCurrentThread();
        return;
    }

    jobject state = env->GetStaticObjectField(gOverlayClass, stateField);
    if (!state || env->ExceptionCheck()) {
        clearException(env, "reading SuperRuntimeState");
        if (state) env->DeleteLocalRef(state);
        gVm->DetachCurrentThread();
        return;
    }

    jclass stateClass = env->GetObjectClass(state);
    jmethodID isSuper = stateClass
            ? env->GetMethodID(stateClass, "isSuper", "()Z") : nullptr;
    jmethodID wantsSuperVisual = stateClass
            ? env->GetMethodID(stateClass, "wantsSuperVisual", "()Z") : nullptr;
    jmethodID unitySendMessage = env->GetStaticMethodID(
            gUnityPlayerClass, "UnitySendMessage",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    if (!stateClass || !isSuper || !wantsSuperVisual || !unitySendMessage || env->ExceptionCheck()) {
        clearException(env, "resolving Super state synchronization methods");
        if (stateClass) env->DeleteLocalRef(stateClass);
        env->DeleteLocalRef(state);
        gVm->DetachCurrentThread();
        return;
    }

    bool requested = false;
    bool sawReadyBridge = false;
    int lastVisualState = reborn::SUPER_VISUAL_NOT_READY;

    while (gStarted.load()) {
        const bool bridgeReady = reborn::super_visual_bridge_ready();
        const bool stableSuper = env->CallBooleanMethod(state, isSuper) == JNI_TRUE;
        const bool wantsVisual = env->CallBooleanMethod(state, wantsSuperVisual) == JNI_TRUE;
        if (env->ExceptionCheck()) {
            clearException(env, "reading Super state");
            std::this_thread::sleep_for(std::chrono::milliseconds(150));
            continue;
        }

        if (bridgeReady) {
            if (stableSuper && (!requested || !sawReadyBridge)) {
                reborn::request_super_visual(true);
                requested = true;
                sendUnityMessage(env, unitySendMessage, "OnInvincible");
                __android_log_print(ANDROID_LOG_INFO, TAG, "requested real Super renderer");
            } else if (!wantsVisual && requested) {
                reborn::request_super_visual(false);
                requested = false;
                sendUnityMessage(env, unitySendMessage, "OnInvincible");
                __android_log_print(ANDROID_LOG_INFO, TAG, "requested normal Sonic renderer restore");
            }
            sawReadyBridge = true;
        }

        const int visualState = reborn::super_visual_state();
        if (visualState != lastVisualState) {
            lastVisualState = visualState;
            if (visualState == reborn::SUPER_VISUAL_GOLD_APPLIED) {
                postStatus(env, "SUPER SONIC • GOLD ACTIVE");
            } else if (visualState == reborn::SUPER_VISUAL_NO_RENDERERS && stableSuper) {
                postStatus(env, "SUPER VISUAL • SONIC MATERIAL NOT FOUND");
            } else if (visualState == reborn::SUPER_VISUAL_FAILED && stableSuper) {
                postStatus(env, "SUPER VISUAL BRIDGE ERROR");
            }
        }

        std::this_thread::sleep_for(std::chrono::milliseconds(150));
    }

    env->DeleteLocalRef(stateClass);
    env->DeleteLocalRef(state);
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
    std::thread(bridgeLoop).detach();
    std::thread(stateSyncLoop).detach();
    return 0;
}
