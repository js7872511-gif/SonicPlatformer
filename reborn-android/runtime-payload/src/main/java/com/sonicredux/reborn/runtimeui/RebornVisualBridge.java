package com.sonicredux.reborn.runtimeui;

import android.app.Activity;
import android.view.View;

import java.lang.reflect.Field;

/**
 * Beta 5 crash-safe visual adapter.
 *
 * Beta 2-4 each crashed when native code directly invoked Unity/IL2CPP renderer APIs. This
 * adapter no longer calls any native renderer bridge. It leaves the GO SUPER button owned by
 * RebornOverlay and only keeps the harmless Android overlay positioning tweak.
 */
public final class RebornVisualBridge {
    private static final float FX_VERTICAL_SHIFT = 0.12f;
    private static boolean installed;

    private RebornVisualBridge() {}

    public static void install(Activity host) {
        if (host == null || installed) return;
        installed = true;
        host.runOnUiThread(() -> {
            alignTransformationFxToSonicArea();
            RebornOverlay.setRuntimeStatus("REBORN RUNTIME ONLINE • SAFE GAME HOOK");
        });
    }

    private static void alignTransformationFxToSonicArea() {
        try {
            Field fxField = RebornOverlay.class.getDeclaredField("fxView");
            fxField.setAccessible(true);
            View fx = (View) fxField.get(null);
            if (fx == null) return;
            fx.post(() -> {
                int height = fx.getHeight();
                if (height > 0) fx.setTranslationY(height * FX_VERTICAL_SHIFT);
            });
        } catch (Throwable ignored) {
            // Effects are optional. Never risk the game over an overlay-position adjustment.
        }
    }
}
