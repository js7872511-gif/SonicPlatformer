#include "super_visual_bridge.h"

#include <android/log.h>
#include <dlfcn.h>

#include <atomic>
#include <cstdint>
#include <cstring>

namespace reborn {
namespace {
constexpr const char* TAG = "RebornVisual";

struct Il2CppDomain;
struct Il2CppAssembly;
struct Il2CppImage;
struct Il2CppClass;
struct Il2CppType;
struct Il2CppObject;
struct Il2CppException;
struct MethodInfo;
struct FieldInfo;

struct ColorValue {
    float r;
    float g;
    float b;
    float a;
};

struct Api {
    void* handle = nullptr;
    Il2CppDomain* (*domain_get)() = nullptr;
    const Il2CppAssembly** (*domain_get_assemblies)(const Il2CppDomain*, size_t*) = nullptr;
    const Il2CppImage* (*assembly_get_image)(const Il2CppAssembly*) = nullptr;
    const char* (*image_get_name)(const Il2CppImage*) = nullptr;
    Il2CppClass* (*class_from_name)(const Il2CppImage*, const char*, const char*) = nullptr;
    const MethodInfo* (*class_get_method_from_name)(Il2CppClass*, const char*, int) = nullptr;
    const Il2CppType* (*class_get_type)(Il2CppClass*) = nullptr;
    Il2CppObject* (*type_get_object)(const Il2CppType*) = nullptr;
    FieldInfo* (*class_get_field_from_name)(Il2CppClass*, const char*) = nullptr;
    void (*field_get_value)(Il2CppObject*, FieldInfo*, void*) = nullptr;
    Il2CppObject* (*runtime_invoke)(const MethodInfo*, void*, void**, Il2CppException**) = nullptr;
    void* (*object_unbox)(Il2CppObject*) = nullptr;
    Il2CppObject* (*string_new)(const char*) = nullptr;
    uint32_t (*gchandle_new)(Il2CppObject*, bool) = nullptr;
    Il2CppObject* (*gchandle_get_target)(uint32_t) = nullptr;
    void (*gchandle_free)(uint32_t) = nullptr;
};

struct TintSnapshot {
    uint32_t rendererHandle = 0;
    ColorValue color{};
};

Api gApi;
std::atomic<bool> gApiLoaded{false};
std::atomic<bool> gResolved{false};
std::atomic<int> gVisualState{SUPER_VISUAL_NOT_READY};

const Il2CppImage* gCSharpImage = nullptr;
Il2CppClass* gKorokClass = nullptr;
Il2CppClass* gGameObjectClass = nullptr;
Il2CppClass* gSpriteRendererClass = nullptr;

const MethodInfo* gGameObjectFind = nullptr;
const MethodInfo* gGameObjectGetComponent = nullptr;
const MethodInfo* gGameObjectGetComponentInChildren = nullptr;
const MethodInfo* gSpriteRendererGetColor = nullptr;
const MethodInfo* gSpriteRendererSetColor = nullptr;

FieldInfo* gVisualField = nullptr;
FieldInfo* gVisualBodyField = nullptr;
FieldInfo* gVisualSpinField = nullptr;

TintSnapshot gSnapshots[3];
int gSnapshotCount = 0;

bool loadSymbol(void** out, const char* name) {
    *out = dlsym(gApi.handle, name);
    if (!*out) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "missing IL2CPP symbol %s", name);
        return false;
    }
    return true;
}

bool loadApi() {
    if (gApiLoaded.load()) return true;
    if (!gApi.handle) gApi.handle = dlopen("libil2cpp.so", RTLD_NOW | RTLD_GLOBAL);
    if (!gApi.handle) return false;

#define LOAD_API(field, symbol) \
    if (!loadSymbol(reinterpret_cast<void**>(&gApi.field), symbol)) return false
    LOAD_API(domain_get, "il2cpp_domain_get");
    LOAD_API(domain_get_assemblies, "il2cpp_domain_get_assemblies");
    LOAD_API(assembly_get_image, "il2cpp_assembly_get_image");
    LOAD_API(image_get_name, "il2cpp_image_get_name");
    LOAD_API(class_from_name, "il2cpp_class_from_name");
    LOAD_API(class_get_method_from_name, "il2cpp_class_get_method_from_name");
    LOAD_API(class_get_type, "il2cpp_class_get_type");
    LOAD_API(type_get_object, "il2cpp_type_get_object");
    LOAD_API(class_get_field_from_name, "il2cpp_class_get_field_from_name");
    LOAD_API(field_get_value, "il2cpp_field_get_value");
    LOAD_API(runtime_invoke, "il2cpp_runtime_invoke");
    LOAD_API(object_unbox, "il2cpp_object_unbox");
    LOAD_API(string_new, "il2cpp_string_new");
    LOAD_API(gchandle_new, "il2cpp_gchandle_new");
    LOAD_API(gchandle_get_target, "il2cpp_gchandle_get_target");
    LOAD_API(gchandle_free, "il2cpp_gchandle_free");
#undef LOAD_API

    gApiLoaded.store(true);
    return true;
}

const Il2CppImage* findImage(Il2CppDomain* domain, const char* wanted) {
    size_t count = 0;
    const Il2CppAssembly** assemblies = gApi.domain_get_assemblies(domain, &count);
    if (!assemblies) return nullptr;
    for (size_t i = 0; i < count; ++i) {
        const Il2CppImage* image = gApi.assembly_get_image(assemblies[i]);
        const char* name = image ? gApi.image_get_name(image) : nullptr;
        if (name && std::strcmp(name, wanted) == 0) return image;
    }
    return nullptr;
}

Il2CppObject* invoke(const MethodInfo* method, void* instance, void** args = nullptr) {
    if (!method) return nullptr;
    Il2CppException* exception = nullptr;
    Il2CppObject* result = gApi.runtime_invoke(method, instance, args, &exception);
    if (exception) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "managed exception in focused Super bridge");
        return nullptr;
    }
    return result;
}

bool resolveBridge() {
    if (gResolved.load()) return true;
    if (!loadApi()) return false;

    Il2CppDomain* domain = gApi.domain_get();
    if (!domain) return false;

    const Il2CppImage* core = findImage(domain, "UnityEngine.CoreModule.dll");
    gCSharpImage = findImage(domain, "Assembly-CSharp.dll");
    if (!core || !gCSharpImage) return false;

    gGameObjectClass = gApi.class_from_name(core, "UnityEngine", "GameObject");
    gSpriteRendererClass = gApi.class_from_name(core, "UnityEngine", "SpriteRenderer");
    gKorokClass = gApi.class_from_name(gCSharpImage, "", "KorokScript");
    if (!gGameObjectClass || !gSpriteRendererClass || !gKorokClass) return false;

    gGameObjectFind = gApi.class_get_method_from_name(gGameObjectClass, "Find", 1);
    gGameObjectGetComponent = gApi.class_get_method_from_name(gGameObjectClass, "GetComponent", 1);
    gGameObjectGetComponentInChildren =
            gApi.class_get_method_from_name(gGameObjectClass, "GetComponentInChildren", 2);
    gSpriteRendererGetColor =
            gApi.class_get_method_from_name(gSpriteRendererClass, "get_color", 0);
    gSpriteRendererSetColor =
            gApi.class_get_method_from_name(gSpriteRendererClass, "set_color", 1);

    gVisualField = gApi.class_get_field_from_name(gKorokClass, "visual");
    gVisualBodyField = gApi.class_get_field_from_name(gKorokClass, "visualBody");
    gVisualSpinField = gApi.class_get_field_from_name(gKorokClass, "visualSpin");

    if (!gGameObjectFind || !gGameObjectGetComponent || !gGameObjectGetComponentInChildren ||
            !gSpriteRendererGetColor || !gSpriteRendererSetColor ||
            !gVisualField || !gVisualBodyField || !gVisualSpinField) {
        return false;
    }

    gResolved.store(true);
    gVisualState.store(SUPER_VISUAL_BRIDGE_READY);
    __android_log_print(ANDROID_LOG_INFO, TAG,
                        "focused Korok visualBody/visualSpin bridge ready");
    return true;
}

Il2CppObject* typeObject(Il2CppClass* klass) {
    const Il2CppType* type = klass ? gApi.class_get_type(klass) : nullptr;
    return type ? gApi.type_get_object(type) : nullptr;
}

Il2CppObject* findGameObject(const char* name) {
    Il2CppObject* text = gApi.string_new(name);
    if (!text) return nullptr;
    void* args[] = {&text};
    return invoke(gGameObjectFind, nullptr, args);
}

Il2CppObject* findSonicRoot() {
    const char* names[] = {"Sonic", "Korok", "NiseSegaSonic", "Player"};
    for (const char* name : names) {
        Il2CppObject* found = findGameObject(name);
        if (found) {
            __android_log_print(ANDROID_LOG_INFO, TAG, "focused bridge found %s", name);
            return found;
        }
    }
    return nullptr;
}

Il2CppObject* getComponent(Il2CppObject* gameObject, Il2CppClass* componentClass) {
    if (!gameObject || !componentClass) return nullptr;
    Il2CppObject* type = typeObject(componentClass);
    if (!type) return nullptr;
    Il2CppObject* typeArg = type;
    void* args[] = {&typeArg};
    return invoke(gGameObjectGetComponent, gameObject, args);
}

Il2CppObject* getComponentInChildren(Il2CppObject* gameObject, Il2CppClass* componentClass) {
    if (!gameObject || !componentClass) return nullptr;
    Il2CppObject* type = typeObject(componentClass);
    if (!type) return nullptr;
    bool includeInactive = true;
    Il2CppObject* typeArg = type;
    void* args[] = {&typeArg, &includeInactive};
    return invoke(gGameObjectGetComponentInChildren, gameObject, args);
}

bool getColor(Il2CppObject* renderer, ColorValue* out) {
    if (!renderer || !out) return false;
    Il2CppObject* boxed = invoke(gSpriteRendererGetColor, renderer);
    if (!boxed) return false;
    void* raw = gApi.object_unbox(boxed);
    if (!raw) return false;
    std::memcpy(out, raw, sizeof(ColorValue));
    return true;
}

void setColor(Il2CppObject* renderer, const ColorValue& color) {
    if (!renderer) return;
    ColorValue mutableColor = color;
    void* args[] = {&mutableColor};
    invoke(gSpriteRendererSetColor, renderer, args);
}

void clearSnapshots(bool restore) {
    for (int i = 0; i < gSnapshotCount; ++i) {
        TintSnapshot& snapshot = gSnapshots[i];
        Il2CppObject* renderer = snapshot.rendererHandle
                ? gApi.gchandle_get_target(snapshot.rendererHandle) : nullptr;
        if (restore && renderer) setColor(renderer, snapshot.color);
        if (snapshot.rendererHandle) gApi.gchandle_free(snapshot.rendererHandle);
        snapshot.rendererHandle = 0;
    }
    gSnapshotCount = 0;
}

bool addTintForVisual(Il2CppObject* visualGameObject) {
    if (!visualGameObject || gSnapshotCount >= 3) return false;
    Il2CppObject* renderer = getComponent(visualGameObject, gSpriteRendererClass);
    if (!renderer) renderer = getComponentInChildren(visualGameObject, gSpriteRendererClass);
    if (!renderer) return false;

    for (int i = 0; i < gSnapshotCount; ++i) {
        Il2CppObject* existing = gApi.gchandle_get_target(gSnapshots[i].rendererHandle);
        if (existing == renderer) return true;
    }

    ColorValue original{};
    if (!getColor(renderer, &original)) return false;

    uint32_t handle = gApi.gchandle_new(renderer, false);
    if (!handle) return false;

    gSnapshots[gSnapshotCount].rendererHandle = handle;
    gSnapshots[gSnapshotCount].color = original;
    ++gSnapshotCount;

    // This keeps the original sprite/texture intact. It is intentionally much safer than
    // Beta 3's material scanner and texture replacement path.
    const ColorValue gold{1.0f, 0.82f, 0.14f, original.a};
    setColor(renderer, gold);
    return true;
}

bool applyFocusedGold() {
    Il2CppObject* root = findSonicRoot();
    if (!root) return false;

    Il2CppObject* korok = getComponent(root, gKorokClass);
    if (!korok) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "KorokScript not found on Sonic root");
        return false;
    }

    Il2CppObject* visual = nullptr;
    Il2CppObject* visualBody = nullptr;
    Il2CppObject* visualSpin = nullptr;
    gApi.field_get_value(korok, gVisualField, &visual);
    gApi.field_get_value(korok, gVisualBodyField, &visualBody);
    gApi.field_get_value(korok, gVisualSpinField, &visualSpin);

    clearSnapshots(false);
    bool changed = false;
    changed |= addTintForVisual(visualBody);
    changed |= addTintForVisual(visualSpin);
    changed |= addTintForVisual(visual);

    __android_log_print(ANDROID_LOG_INFO, TAG,
                        "focused gold tint applied to %d SpriteRenderer(s)", gSnapshotCount);
    return changed && gSnapshotCount > 0;
}

}  // namespace

bool install_super_visual_bridge() {
    // Resolve metadata only. Do not enumerate renderers, patch MethodInfo memory, or mutate
    // Unity objects during startup.
    return resolveBridge();
}

bool super_visual_bridge_ready() {
    return gResolved.load();
}

bool request_super_visual(bool enabled) {
    if (!resolveBridge()) {
        gVisualState.store(SUPER_VISUAL_FAILED);
        return false;
    }

    if (!enabled) {
        clearSnapshots(true);
        gVisualState.store(SUPER_VISUAL_NORMAL_RESTORED);
        return true;
    }

    const bool applied = applyFocusedGold();
    gVisualState.store(applied ? SUPER_VISUAL_GOLD_APPLIED : SUPER_VISUAL_NO_RENDERERS);
    return applied;
}

int super_visual_state() {
    return gVisualState.load();
}

}  // namespace reborn
