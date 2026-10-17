package com.sonicredux.reborn.runtimeui;

import android.app.Activity;
import android.os.Handler;
import android.view.View;
import android.widget.Button;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Beta 5 crash-safe Super adapter.
 *
 * Beta 2-4 crashed when native code directly invoked Unity/IL2CPP renderer APIs. Beta 5 removes
 * that path completely. GO SUPER now keeps the existing overlay transition and sends Redux's own
 * OnInvincible message to the Korok player through UnityPlayer.UnitySendMessage, which lets Unity
 * execute the method on its normal scripting path.
 */
public final class RebornVisualBridge {
    private static final long APPLY_DELAY_MS = 1650L;
    private static final float FX_VERTICAL_SHIFT = 0.12f;

    private static Handler handler;
    private static Method overlayToggleSuper;
    private static Method unitySendMessage;
    private static Button goSuper;
    private static boolean installed;

    private RebornVisualBridge() {}

    public static void install(Activity host) {
        if (host == null || installed) return;
        handler = new Handler(host.getMainLooper());
        host.runOnUiThread(() -> {
            try {
                Field buttonField = RebornOverlay.class.getDeclaredField("goSuper");
                buttonField.setAccessible(true);
                goSuper = (Button) buttonField.get(null);

                overlayToggleSuper = RebornOverlay.class.getDeclaredMethod("toggleSuper");
                overlayToggleSuper.setAccessible(true);

                alignTransformationFxToSonicArea();
                if (goSuper == null) {
                    RebornOverlay.setRuntimeStatus("REBORN RUNTIME ONLINE • SAFE MODE");
                    return;
                }

                goSuper.setOnClickListener(RebornVisualBridge::onGoSuperClicked);
                installed = true;
                RebornOverlay.setRuntimeStatus("REBORN RUNTIME ONLINE • SAFE GAME HOOK");
            } catch (Throwable error) {
                RebornOverlay.setRuntimeStatus("REBORN RUNTIME ONLINE • SAFE MODE");
            }
        });
    }

    private static void onGoSuperClicked(View ignored) {
        if (goSuper == null || overlayToggleSuper == null || !goSuper.isEnabled()) return;
        String before = String.valueOf(goSuper.getText());
        boolean goingSuper = before.contains("GO SUPER");
        boolean goingNormal = before.contains("GO NORMAL");
        if (!goingSuper && !goingNormal) return;

        try {
            overlayToggleSuper.invoke(null);
        } catch (Throwable error) {
            RebornOverlay.setRuntimeStatus("SUPER TRANSFORM ERROR");
            return;
        }

        if (goingSuper && handler != null) {
            handler.postDelayed(RebornVisualBridge::sendSafeSuperMessage, APPLY_DELAY_MS);
        } else if (goingNormal) {
            RebornOverlay.setRuntimeStatus("REBORN RUNTIME ONLINE • SAFE GAME HOOK");
        }
    }

    private static void sendSafeSuperMessage() {
        try {
            if (unitySendMessage == null) {
                Class<?> unityPlayer = Class.forName("com.unity3d.player.UnityPlayer");
                unitySendMessage = unityPlayer.getMethod(
                        "UnitySendMessage", String.class, String.class, String.class);
            }
            unitySendMessage.invoke(
                    null,
                    SuperMessageRoute.playerObject(),
                    SuperMessageRoute.invincibilityMethod(),
                    "");
            RebornOverlay.setRuntimeStatus("SUPER SONIC • GAME HOOK ACTIVE");
        } catch (Throwable error) {
            RebornOverlay.setRuntimeStatus("SUPER GAME HOOK UNAVAILABLE");
        }
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
