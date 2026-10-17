package com.sonicredux.reborn.runtimeui;

import android.app.Activity;
import android.os.Handler;
import android.view.View;
import android.widget.Button;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Safe adapter between the Android overlay and the focused Redux Super visual bridge.
 *
 * Beta 3 still used a broad native renderer/material scan when GO SUPER was pressed. Beta 4
 * narrows that native path to KorokScript.visual / visualBody / visualSpin and their own
 * SpriteRenderers. The native request is still delayed until the transformation has finished.
 */
public final class RebornVisualBridge {
    private static final long APPLY_DELAY_MS = 1650L;
    private static final long RESTORE_DELAY_MS = 900L;
    private static final float FX_VERTICAL_SHIFT = 0.12f;

    private static Activity activity;
    private static Handler handler;
    private static Object unityPlayer;
    private static Method invokeOnMainThread;
    private static Method overlayToggleSuper;
    private static Button goSuper;
    private static boolean installed;

    private RebornVisualBridge() {}

    public static void install(Activity host) {
        if (host == null || installed) return;
        activity = host;
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
                RebornOverlay.setRuntimeStatus("REBORN RUNTIME ONLINE");
            } catch (Throwable error) {
                RebornOverlay.setRuntimeStatus("REBORN RUNTIME ONLINE • SAFE MODE");
            }
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

    private static void onGoSuperClicked(View ignored) {
        if (goSuper == null || overlayToggleSuper == null || !goSuper.isEnabled()) return;
        String before = String.valueOf(goSuper.getText());
        boolean goingSuper = before.contains("GO SUPER");
        boolean goingNormal = before.contains("GO NORMAL");
        if (!goingSuper && !goingNormal) return;

        try {
            // Keeps the existing 7-emerald animation, buttons and Redux OnInvincible sparkles.
            overlayToggleSuper.invoke(null);
        } catch (Throwable error) {
            RebornOverlay.setRuntimeStatus("SUPER TRANSFORM ERROR");
            return;
        }

        long delay = goingSuper ? APPLY_DELAY_MS : RESTORE_DELAY_MS;
        handler.postDelayed(() -> requestVisual(goingSuper), delay);
    }

    private static void requestVisual(boolean superEnabled) {
        if (!runOnUnityMainThread(() -> {
            boolean success = nativeSetSuperVisualRequested(superEnabled);
            int state = nativeSuperVisualState();
            if (handler == null) return;
            handler.post(() -> {
                if (superEnabled) {
                    if (success && state == 2) {
                        RebornOverlay.setRuntimeStatus("SUPER SONIC • GOLD TINT ACTIVE");
                    } else if (state == -2) {
                        RebornOverlay.setRuntimeStatus("SUPER VISUAL • PLAYER SPRITE NOT FOUND");
                    } else {
                        RebornOverlay.setRuntimeStatus("SUPER VISUAL BRIDGE ERROR");
                    }
                } else {
                    RebornOverlay.setRuntimeStatus("REBORN RUNTIME ONLINE");
                }
            });
        })) {
            RebornOverlay.setRuntimeStatus("SUPER VISUAL • UNITY THREAD UNAVAILABLE");
        }
    }

    private static boolean runOnUnityMainThread(Runnable task) {
        try {
            if (unityPlayer == null) unityPlayer = findUnityPlayer(activity);
            if (unityPlayer == null) return false;
            if (invokeOnMainThread == null) {
                Class<?> type = unityPlayer.getClass();
                while (type != null && invokeOnMainThread == null) {
                    try {
                        invokeOnMainThread = type.getDeclaredMethod("invokeOnMainThread", Runnable.class);
                        invokeOnMainThread.setAccessible(true);
                    } catch (NoSuchMethodException ignored) {
                        type = type.getSuperclass();
                    }
                }
            }
            if (invokeOnMainThread == null) return false;
            invokeOnMainThread.invoke(unityPlayer, task);
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    private static Object findUnityPlayer(Activity host) {
        Class<?> type = host.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("mUnityPlayer");
                field.setAccessible(true);
                Object value = field.get(host);
                if (value != null) return value;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
                continue;
            } catch (Throwable error) {
                return null;
            }
            type = type.getSuperclass();
        }
        return null;
    }

    public static native boolean nativeSetSuperVisualRequested(boolean enabled);
    public static native int nativeSuperVisualState();
}
