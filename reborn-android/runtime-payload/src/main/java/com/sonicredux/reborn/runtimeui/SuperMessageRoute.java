package com.sonicredux.reborn.runtimeui;

/**
 * Crash-safe Super route for the supplied Redux build.
 *
 * Runtime renderer mutation is intentionally disabled. UnitySendMessage queues calls through
 * Unity's own scripting path instead of invoking UnityEngine/IL2CPP APIs from Android threads.
 */
public final class SuperMessageRoute {
    private SuperMessageRoute() {}

    public static String playerObject() {
        return "Korok";
    }

    public static String invincibilityMethod() {
        return "OnInvincible";
    }

    public static boolean usesNativeRendererBridge() {
        return false;
    }
}
