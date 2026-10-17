#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>

namespace {
constexpr const char* TAG = "RebornWrapper";
using JniOnLoadFn = jint (*)(JavaVM*, void*);
using RebornStartFn = int (*)(JavaVM*);

void logError(const char* message) {
    __android_log_print(ANDROID_LOG_ERROR, TAG, "%s: %s", message, dlerror() ? dlerror() : "unknown");
}
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    void* original = dlopen("libmain_redux.so", RTLD_NOW | RTLD_GLOBAL);
    if (!original) {
        logError("could not load libmain_redux.so");
        return JNI_ERR;
    }

    dlerror();
    auto originalOnLoad = reinterpret_cast<JniOnLoadFn>(dlsym(original, "JNI_OnLoad"));
    const char* symbolError = dlerror();
    if (symbolError || !originalOnLoad) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "original JNI_OnLoad missing: %s",
                            symbolError ? symbolError : "unknown");
        return JNI_ERR;
    }

    jint originalResult = originalOnLoad(vm, reserved);
    if (originalResult == JNI_ERR) return JNI_ERR;

    void* runtime = dlopen("libreborn.so", RTLD_NOW | RTLD_GLOBAL);
    if (!runtime) {
        logError("could not load libreborn.so; continuing without runtime mods");
        return originalResult;
    }

    dlerror();
    auto start = reinterpret_cast<RebornStartFn>(dlsym(runtime, "reborn_runtime_start"));
    symbolError = dlerror();
    if (symbolError || !start) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "reborn_runtime_start missing: %s",
                            symbolError ? symbolError : "unknown");
        return originalResult;
    }

    if (start(vm) != 0) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "Re-born runtime disabled; Redux continues normally");
    }
    return originalResult;
}
