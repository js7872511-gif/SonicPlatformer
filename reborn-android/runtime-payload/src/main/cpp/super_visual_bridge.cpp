#include "super_visual_bridge.h"

#include <android/log.h>
#include <dlfcn.h>
#include <sys/mman.h>
#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <cctype>
#include <cstdint>
#include <cstring>
#include <string>
#include <unordered_set>
#include <vector>

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

struct ColorValue {
    float r;
    float g;
    float b;
    float a;
};

struct Il2CppArrayHeader {
    void* klass;
    void* monitor;
    void* bounds;
    uintptr_t maxLength;
};

struct Il2CppStringLayout {
    void* klass;
    void* monitor;
    int32_t length;
    char16_t chars[1];
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
    Il2CppObject* (*runtime_invoke)(const MethodInfo*, void*, void**, Il2CppException**) = nullptr;
    void* (*object_unbox)(Il2CppObject*) = nullptr;
    size_t (*array_length)(Il2CppObject*) = nullptr;
    void* (*thread_attach)(Il2CppDomain*) = nullptr;
    void (*thread_detach)(void*) = nullptr;
    uint32_t (*gchandle_new)(Il2CppObject*, bool) = nullptr;
    Il2CppObject* (*gchandle_get_target)(uint32_t) = nullptr;
    void (*gchandle_free)(uint32_t) = nullptr;
};

struct MaterialSnapshot {
    uint32_t materialHandle = 0;
    uint32_t textureHandle = 0;
    ColorValue color{};
};

struct MaterialCandidate {
    Il2CppObject* material = nullptr;
    Il2CppObject* texture = nullptr;
    ColorValue color{};
    std::string identity;
};

Api gApi;
std::atomic<bool> gApiLoaded{false};
std::atomic<bool> gHookInstalled{false};
std::atomic<bool> gDesiredSuper{false};
std::atomic<int> gVisualState{SUPER_VISUAL_NOT_READY};

const MethodInfo* gComponentGetGameObject = nullptr;
const MethodInfo* gGameObjectGetComponentsInChildren = nullptr;
const MethodInfo* gRendererGetMaterials = nullptr;
const MethodInfo* gMaterialGetColor = nullptr;
const MethodInfo* gMaterialSetColor = nullptr;
const MethodInfo* gMaterialGetMainTexture = nullptr;
const MethodInfo* gMaterialSetMainTexture = nullptr;
const MethodInfo* gObjectGetName = nullptr;
Il2CppClass* gRendererClass = nullptr;

using OnInvincibleFn = void (*)(void*, const MethodInfo*);
std::atomic<void*> gOriginalOnInvincible{nullptr};
void* gOwner = nullptr;
std::vector<MaterialSnapshot> gSnapshots;

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
    gApi.handle = dlopen("libil2cpp.so", RTLD_NOW | RTLD_GLOBAL);
    if (!gApi.handle) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "libil2cpp.so is not loaded yet");
        return false;
    }

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
    LOAD_API(runtime_invoke, "il2cpp_runtime_invoke");
    LOAD_API(object_unbox, "il2cpp_object_unbox");
    LOAD_API(array_length, "il2cpp_array_length");
    LOAD_API(thread_attach, "il2cpp_thread_attach");
    LOAD_API(thread_detach, "il2cpp_thread_detach");
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
        __android_log_print(ANDROID_LOG_WARN, TAG, "managed exception while invoking Unity API");
        return nullptr;
    }
    return result;
}

void** arrayData(Il2CppObject* array) {
    if (!array) return nullptr;
    auto* bytes = reinterpret_cast<uint8_t*>(array);
    return reinterpret_cast<void**>(bytes + sizeof(Il2CppArrayHeader));
}

std::string managedString(Il2CppObject* stringObject) {
    if (!stringObject) return {};
    auto* value = reinterpret_cast<Il2CppStringLayout*>(stringObject);
    if (value->length <= 0 || value->length > 4096) return {};
    std::string out;
    out.reserve(static_cast<size_t>(value->length));
    for (int32_t i = 0; i < value->length; ++i) {
        char16_t ch = value->chars[i];
        out.push_back(ch <= 0x7f ? static_cast<char>(ch) : '?');
    }
    return out;
}

std::string objectName(Il2CppObject* object) {
    return managedString(invoke(gObjectGetName, object));
}

std::string lowerCopy(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
        return static_cast<char>(std::tolower(c));
    });
    return value;
}

bool containsAny(const std::string& value, const std::vector<const char*>& needles) {
    std::string lower = lowerCopy(value);
    for (const char* needle : needles) {
        if (lower.find(needle) != std::string::npos) return true;
    }
    return false;
}

bool likelySonicMaterial(const std::string& identity) {
    return containsAny(identity, {"sonic", "nisesega", "body", "quill", "fur", "atlas"});
}

bool likelyDetailMaterial(const std::string& identity) {
    return containsAny(identity, {"eye", "mouth", "shoe", "glove", "shield", "trail", "ring"});
}

bool readColor(Il2CppObject* material, ColorValue* out) {
    Il2CppObject* boxed = invoke(gMaterialGetColor, material);
    if (!boxed) return false;
    void* raw = gApi.object_unbox(boxed);
    if (!raw) return false;
    std::memcpy(out, raw, sizeof(ColorValue));
    return true;
}

void setColor(Il2CppObject* material, const ColorValue& color) {
    ColorValue mutableColor = color;
    void* args[] = {&mutableColor};
    invoke(gMaterialSetColor, material, args);
}

Il2CppObject* getMainTexture(Il2CppObject* material) {
    return invoke(gMaterialGetMainTexture, material);
}

void setMainTexture(Il2CppObject* material, Il2CppObject* texture) {
    Il2CppObject* mutableTexture = texture;
    void* args[] = {&mutableTexture};
    invoke(gMaterialSetMainTexture, material, args);
}

void releaseSnapshots(bool restore) {
    for (MaterialSnapshot& snapshot : gSnapshots) {
        Il2CppObject* material = snapshot.materialHandle
                ? gApi.gchandle_get_target(snapshot.materialHandle) : nullptr;
        if (restore && material) {
            Il2CppObject* texture = snapshot.textureHandle
                    ? gApi.gchandle_get_target(snapshot.textureHandle) : nullptr;
            setMainTexture(material, texture);
            setColor(material, snapshot.color);
        }
        if (snapshot.textureHandle) gApi.gchandle_free(snapshot.textureHandle);
        if (snapshot.materialHandle) gApi.gchandle_free(snapshot.materialHandle);
    }
    gSnapshots.clear();
    gOwner = nullptr;
}

bool reapplyGold() {
    if (gSnapshots.empty()) return false;
    const ColorValue goldBase{1.0f, 0.72f, 0.055f, 1.0f};
    size_t changed = 0;
    for (MaterialSnapshot& snapshot : gSnapshots) {
        Il2CppObject* material = snapshot.materialHandle
                ? gApi.gchandle_get_target(snapshot.materialHandle) : nullptr;
        if (!material) continue;
        ColorValue gold = goldBase;
        gold.a = snapshot.color.a;
        setMainTexture(material, nullptr);
        setColor(material, gold);
        ++changed;
    }
    return changed > 0;
}

bool captureAndApplyGold(void* korok) {
    Il2CppObject* gameObject = invoke(gComponentGetGameObject, korok);
    if (!gameObject) return false;

    Il2CppObject* rendererType = gApi.type_get_object(gApi.class_get_type(gRendererClass));
    if (!rendererType) return false;

    bool includeInactive = true;
    Il2CppObject* rendererTypeArg = rendererType;
    void* rendererArgs[] = {&rendererTypeArg, &includeInactive};
    Il2CppObject* renderers = invoke(gGameObjectGetComponentsInChildren, gameObject, rendererArgs);
    if (!renderers) return false;

    const size_t rendererCount = gApi.array_length(renderers);
    void** rendererItems = arrayData(renderers);
    if (!rendererItems || rendererCount == 0) return false;

    std::vector<MaterialCandidate> candidates;
    std::unordered_set<Il2CppObject*> seenMaterials;
    for (size_t i = 0; i < rendererCount; ++i) {
        auto* renderer = reinterpret_cast<Il2CppObject*>(rendererItems[i]);
        if (!renderer) continue;
        Il2CppObject* materials = invoke(gRendererGetMaterials, renderer);
        if (!materials) continue;
        const size_t materialCount = gApi.array_length(materials);
        void** materialItems = arrayData(materials);
        if (!materialItems) continue;
        for (size_t j = 0; j < materialCount; ++j) {
            auto* material = reinterpret_cast<Il2CppObject*>(materialItems[j]);
            if (!material || !seenMaterials.insert(material).second) continue;
            ColorValue original{};
            if (!readColor(material, &original)) continue;
            Il2CppObject* texture = getMainTexture(material);
            std::string identity = objectName(material);
            if (texture) {
                std::string textureName = objectName(texture);
                if (!textureName.empty()) {
                    identity += " ";
                    identity += textureName;
                }
            }
            __android_log_print(ANDROID_LOG_INFO, TAG, "Sonic child material: %s", identity.c_str());
            candidates.push_back({material, texture, original, identity});
        }
    }

    if (candidates.empty()) return false;

    bool hasNamedSonicMaterial = false;
    for (const MaterialCandidate& candidate : candidates) {
        if (likelySonicMaterial(candidate.identity)) {
            hasNamedSonicMaterial = true;
            break;
        }
    }

    const ColorValue goldBase{1.0f, 0.72f, 0.055f, 1.0f};
    for (const MaterialCandidate& candidate : candidates) {
        bool selected = hasNamedSonicMaterial
                ? likelySonicMaterial(candidate.identity)
                : !likelyDetailMaterial(candidate.identity);
        if (!selected) continue;

        MaterialSnapshot snapshot;
        snapshot.materialHandle = gApi.gchandle_new(candidate.material, false);
        snapshot.textureHandle = candidate.texture ? gApi.gchandle_new(candidate.texture, false) : 0;
        snapshot.color = candidate.color;
        if (!snapshot.materialHandle) {
            if (snapshot.textureHandle) gApi.gchandle_free(snapshot.textureHandle);
            continue;
        }
        gSnapshots.push_back(snapshot);

        ColorValue gold = goldBase;
        gold.a = candidate.color.a;
        setMainTexture(candidate.material, nullptr);
        setColor(candidate.material, gold);
    }

    if (gSnapshots.empty() && hasNamedSonicMaterial) {
        // Name matching can vary between Redux scenes. Fall back to all non-detail materials.
        for (const MaterialCandidate& candidate : candidates) {
            if (likelyDetailMaterial(candidate.identity)) continue;
            MaterialSnapshot snapshot;
            snapshot.materialHandle = gApi.gchandle_new(candidate.material, false);
            snapshot.textureHandle = candidate.texture ? gApi.gchandle_new(candidate.texture, false) : 0;
            snapshot.color = candidate.color;
            if (!snapshot.materialHandle) {
                if (snapshot.textureHandle) gApi.gchandle_free(snapshot.textureHandle);
                continue;
            }
            gSnapshots.push_back(snapshot);
            ColorValue gold = goldBase;
            gold.a = candidate.color.a;
            setMainTexture(candidate.material, nullptr);
            setColor(candidate.material, gold);
        }
    }

    gOwner = korok;
    __android_log_print(ANDROID_LOG_INFO, TAG, "gold override applied to %zu materials", gSnapshots.size());
    return !gSnapshots.empty();
}

void applyRequestedVisual(void* korok) {
    if (!korok || !gHookInstalled.load()) return;
    if (gDesiredSuper.load()) {
        if (gOwner != korok) {
            releaseSnapshots(false);
        }
        bool applied = !gSnapshots.empty() ? reapplyGold() : captureAndApplyGold(korok);
        gVisualState.store(applied ? SUPER_VISUAL_GOLD_APPLIED : SUPER_VISUAL_NO_RENDERERS);
    } else {
        releaseSnapshots(true);
        gVisualState.store(SUPER_VISUAL_NORMAL_RESTORED);
    }
}

void onInvincibleHook(void* self, const MethodInfo* method) {
    auto original = reinterpret_cast<OnInvincibleFn>(gOriginalOnInvincible.load());
    if (original) original(self, method);
    applyRequestedVisual(self);
}

bool makeWritable(void* address, size_t bytes) {
    const long pageSize = sysconf(_SC_PAGESIZE);
    if (pageSize <= 0) return false;
    const uintptr_t mask = static_cast<uintptr_t>(pageSize - 1);
    const uintptr_t start = reinterpret_cast<uintptr_t>(address) & ~mask;
    const uintptr_t end = (reinterpret_cast<uintptr_t>(address) + bytes + mask) & ~mask;
    return mprotect(reinterpret_cast<void*>(start), end - start, PROT_READ | PROT_WRITE) == 0;
}

bool resolveUnityMethods(Il2CppDomain* domain) {
    const Il2CppImage* csharp = findImage(domain, "Assembly-CSharp.dll");
    const Il2CppImage* core = findImage(domain, "UnityEngine.CoreModule.dll");
    if (!csharp || !core) return false;

    Il2CppClass* korok = gApi.class_from_name(csharp, "", "KorokScript");
    Il2CppClass* component = gApi.class_from_name(core, "UnityEngine", "Component");
    Il2CppClass* gameObject = gApi.class_from_name(core, "UnityEngine", "GameObject");
    gRendererClass = gApi.class_from_name(core, "UnityEngine", "Renderer");
    Il2CppClass* material = gApi.class_from_name(core, "UnityEngine", "Material");
    Il2CppClass* object = gApi.class_from_name(core, "UnityEngine", "Object");
    if (!korok || !component || !gameObject || !gRendererClass || !material || !object) return false;

    const MethodInfo* onInvincible = gApi.class_get_method_from_name(korok, "OnInvincible", 0);
    gComponentGetGameObject = gApi.class_get_method_from_name(component, "get_gameObject", 0);
    gGameObjectGetComponentsInChildren = gApi.class_get_method_from_name(gameObject, "GetComponentsInChildren", 2);
    gRendererGetMaterials = gApi.class_get_method_from_name(gRendererClass, "get_materials", 0);
    gMaterialGetColor = gApi.class_get_method_from_name(material, "get_color", 0);
    gMaterialSetColor = gApi.class_get_method_from_name(material, "set_color", 1);
    gMaterialGetMainTexture = gApi.class_get_method_from_name(material, "get_mainTexture", 0);
    gMaterialSetMainTexture = gApi.class_get_method_from_name(material, "set_mainTexture", 1);
    gObjectGetName = gApi.class_get_method_from_name(object, "get_name", 0);

    if (!onInvincible || !gComponentGetGameObject || !gGameObjectGetComponentsInChildren ||
            !gRendererGetMaterials || !gMaterialGetColor || !gMaterialSetColor ||
            !gMaterialGetMainTexture || !gMaterialSetMainTexture || !gObjectGetName) {
        return false;
    }

    auto** words = reinterpret_cast<void**>(const_cast<MethodInfo*>(onInvincible));
    void* original = words[0];
    if (!original || original == reinterpret_cast<void*>(&onInvincibleHook)) return original != nullptr;
    if (!makeWritable(words, sizeof(void*))) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "could not make OnInvincible MethodInfo writable");
        return false;
    }

    gOriginalOnInvincible.store(original);
    words[0] = reinterpret_cast<void*>(&onInvincibleHook);
    __android_log_print(ANDROID_LOG_INFO, TAG, "KorokScript.OnInvincible hook installed");
    return true;
}

}  // namespace

bool install_super_visual_bridge() {
    if (gHookInstalled.load()) return true;
    if (!loadApi()) return false;
    Il2CppDomain* domain = gApi.domain_get();
    if (!domain) return false;
    void* thread = gApi.thread_attach(domain);
    if (!thread) return false;
    const bool ok = resolveUnityMethods(domain);
    gApi.thread_detach(thread);
    if (ok) {
        gHookInstalled.store(true);
        gVisualState.store(SUPER_VISUAL_HOOK_READY);
    }
    return ok;
}

bool super_visual_bridge_ready() {
    return gHookInstalled.load();
}

void request_super_visual(bool enabled) {
    gDesiredSuper.store(enabled);
}

int super_visual_state() {
    return gVisualState.load();
}

}  // namespace reborn
