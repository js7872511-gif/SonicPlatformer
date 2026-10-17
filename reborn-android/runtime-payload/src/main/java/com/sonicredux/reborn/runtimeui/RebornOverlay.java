package com.sonicredux.reborn.runtimeui;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

public final class RebornOverlay {
    private static final int BUTTON_WIDTH_DP = 132;
    private static final int BUTTON_HEIGHT_DP = 52;
    private static final int EDGE_DP = 18;
    private static final int GAP_DP = 10;
    private static final long TRANSFORM_IN_MS = 1550L;
    private static final long TRANSFORM_OUT_MS = 850L;

    private static final SuperRuntimeState superState = new SuperRuntimeState();
    private static WeakReference<Activity> activityRef = new WeakReference<>(null);
    private static FrameLayout root;
    private static TextView status;
    private static Button goSuper;
    private static Button flight;
    private static Button superBoost;
    private static Button chaosBurst;
    private static SuperFxView fxView;
    private static Handler mainHandler;
    private static Method unitySendMessage;

    private static final Runnable invincibilityRefresh = new Runnable() {
        @Override public void run() {
            if (!superState.isSuper()) return;
            sendUnity("OnInvincible");
            if (mainHandler != null) mainHandler.postDelayed(this, 6000L);
        }
    };

    private static final Runnable flightPulse = new Runnable() {
        @Override public void run() {
            if (!superState.isFlightEnabled()) return;
            // Controller2D and KorokScript live on the Sonic GameObject in the supplied Redux build.
            // UnitySendMessage queues the call through Unity rather than touching game memory directly.
            sendUnity("StartBounce");
            sendUnity("OnDoubleJumpStart");
            if (mainHandler != null) mainHandler.postDelayed(this, 210L);
        }
    };

    private RebornOverlay() {}

    public static final class ControlState {
        public final boolean goSuperVisible;
        public final boolean goSuperEnabled;
        public final boolean flightVisible;
        public final boolean flightEnabled;
        public final boolean superBoostVisible;
        public final boolean superBoostEnabled;
        public final boolean chaosBurstVisible;
        public final boolean chaosBurstEnabled;

        ControlState(boolean superMode, boolean transforming) {
            goSuperVisible = true;
            goSuperEnabled = !transforming;
            flightVisible = superMode;
            flightEnabled = superMode && !transforming;
            superBoostVisible = superMode;
            superBoostEnabled = superMode && !transforming;
            chaosBurstVisible = superMode;
            chaosBurstEnabled = superMode && !transforming;
        }
    }

    static ControlState controlState(boolean superMode, boolean transforming) {
        return new ControlState(superMode, transforming);
    }

    public static void install(Activity activity) {
        if (activity == null) return;
        activityRef = new WeakReference<>(activity);
        if (mainHandler == null) mainHandler = new Handler(activity.getMainLooper());
        activity.runOnUiThread(() -> {
            if (root != null && root.getParent() != null) return;

            View decor = activity.getWindow().getDecorView();
            if (!(decor instanceof ViewGroup)) return;
            ViewGroup parent = (ViewGroup) decor;

            root = new FrameLayout(activity);
            root.setClickable(false);
            root.setFocusable(false);
            parent.addView(root, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            fxView = new SuperFxView(activity);
            fxView.setClickable(false);
            root.addView(fxView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            status = new TextView(activity);
            status.setText("REBORN RUNTIME STARTING");
            status.setTextColor(Color.WHITE);
            status.setTextSize(12f);
            status.setGravity(Gravity.CENTER);
            status.setBackground(roundedBackground(0xAA111827));
            FrameLayout.LayoutParams statusLp = new FrameLayout.LayoutParams(dp(activity, 235), dp(activity, 36));
            statusLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            statusLp.topMargin = dp(activity, 12);
            root.addView(status, statusLp);

            goSuper = makeButton(activity, "GO SUPER", RebornOverlay::toggleSuper);
            flight = makeButton(activity, "FLIGHT", RebornOverlay::toggleFlight);
            superBoost = makeButton(activity, "SUPER BOOST", RebornOverlay::activateSuperBoost);
            chaosBurst = makeButton(activity, "CHAOS BURST", RebornOverlay::activateChaosBurst);

            addAbilityButton(activity, goSuper, 0);
            addAbilityButton(activity, flight, 1);
            addAbilityButton(activity, superBoost, 2);
            addAbilityButton(activity, chaosBurst, 3);
            applyRuntimeState();
        });
    }

    public static void setRuntimeStatus(String text) {
        Activity activity = activityRef.get();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            if (status != null) status.setText(text == null ? "REBORN RUNTIME" : text);
        });
    }

    /** Kept as a native-runtime bridge entry point for later IL2CPP-backed builds. */
    public static void setSuperState(boolean superMode, boolean transforming) {
        Activity activity = activityRef.get();
        if (activity == null) return;
        ControlState state = controlState(superMode, transforming);
        activity.runOnUiThread(() -> applyState(state));
    }

    private static void toggleSuper() {
        if (!superState.toggleSuper()) return;
        if (superState.mode() == SuperRuntimeState.Mode.TRANSFORMING_IN) {
            beginTransformIn();
        } else if (superState.mode() == SuperRuntimeState.Mode.TRANSFORMING_OUT) {
            beginTransformOut();
        }
    }

    private static void beginTransformIn() {
        stopFlightLoop();
        setRuntimeStatus("CHAOS EMERALDS • TRANSFORMING");
        if (fxView != null) fxView.startTransformIn();
        sendUnity("OnInvincible");
        applyRuntimeState();
        if (mainHandler != null) {
            mainHandler.postDelayed(() -> {
                if (superState.mode() != SuperRuntimeState.Mode.TRANSFORMING_IN) return;
                superState.finishTransition();
                if (fxView != null) fxView.setSuperActive(true);
                sendUnity("OnInvincible");
                startInvincibilityRefresh();
                setRuntimeStatus("SUPER SONIC • RE-BORN");
                applyRuntimeState();
            }, TRANSFORM_IN_MS);
        }
    }

    private static void beginTransformOut() {
        stopInvincibilityRefresh();
        stopFlightLoop();
        if (fxView != null) fxView.startTransformOut();
        setRuntimeStatus("RETURNING TO NORMAL");
        applyRuntimeState();
        if (mainHandler != null) {
            mainHandler.postDelayed(() -> {
                if (superState.mode() != SuperRuntimeState.Mode.TRANSFORMING_OUT) return;
                superState.finishTransition();
                if (fxView != null) fxView.setSuperActive(false);
                setRuntimeStatus("REBORN RUNTIME ONLINE");
                applyRuntimeState();
            }, TRANSFORM_OUT_MS);
        }
    }

    private static void toggleFlight() {
        if (!superState.isSuper()) return;
        boolean enabled = superState.toggleFlight();
        if (fxView != null) fxView.setFlightActive(enabled);
        if (enabled) {
            sendUnity("StartBounce");
            if (mainHandler != null) {
                mainHandler.removeCallbacks(flightPulse);
                mainHandler.post(flightPulse);
            }
            setRuntimeStatus("SUPER FLIGHT • ON");
        } else {
            stopFlightLoop();
            setRuntimeStatus("SUPER SONIC • RE-BORN");
        }
        applyRuntimeState();
    }

    private static void activateSuperBoost() {
        if (!superState.isSuper() || superState.isTransforming()) return;
        if (fxView != null) fxView.triggerBoost();
        sendUnity("StartSpinDash");
        if (mainHandler != null) {
            mainHandler.postDelayed(() -> sendUnity("StartSpin"), 70L);
        }
        setRuntimeStatus("SUPER BOOST!");
        restoreSuperStatusLater(650L);
    }

    private static void activateChaosBurst() {
        if (!superState.isSuper() || superState.isTransforming()) return;
        if (fxView != null) fxView.triggerChaosBurst();
        sendUnity("OnInvincible");
        sendUnity("StartSpin");
        setRuntimeStatus("CHAOS BURST!");
        restoreSuperStatusLater(900L);
    }

    private static void restoreSuperStatusLater(long delay) {
        if (mainHandler == null) return;
        mainHandler.postDelayed(() -> {
            if (superState.isSuper()) {
                setRuntimeStatus(superState.isFlightEnabled() ? "SUPER FLIGHT • ON" : "SUPER SONIC • RE-BORN");
            }
        }, delay);
    }

    private static void startInvincibilityRefresh() {
        if (mainHandler == null) return;
        mainHandler.removeCallbacks(invincibilityRefresh);
        mainHandler.postDelayed(invincibilityRefresh, 5500L);
    }

    private static void stopInvincibilityRefresh() {
        if (mainHandler != null) mainHandler.removeCallbacks(invincibilityRefresh);
    }

    private static void stopFlightLoop() {
        if (mainHandler != null) mainHandler.removeCallbacks(flightPulse);
        if (fxView != null) fxView.setFlightActive(false);
    }

    private static void sendUnity(String methodName) {
        try {
            if (unitySendMessage == null) {
                Class<?> unityPlayer = Class.forName("com.unity3d.player.UnityPlayer");
                unitySendMessage = unityPlayer.getMethod(
                        "UnitySendMessage", String.class, String.class, String.class);
            }
            unitySendMessage.invoke(null, "Sonic", methodName, "");
        } catch (Throwable ignored) {
            // Visual transformation remains usable if a gameplay hook is unavailable in a scene.
        }
    }

    private static void applyRuntimeState() {
        boolean stableSuper = superState.isSuper();
        boolean transforming = superState.isTransforming();
        applyState(controlState(stableSuper, transforming));
        if (goSuper != null) {
            if (superState.mode() == SuperRuntimeState.Mode.SUPER) goSuper.setText("GO NORMAL");
            else if (transforming) goSuper.setText("TRANSFORMING…");
            else goSuper.setText("GO SUPER");
        }
        if (flight != null) flight.setText(superState.isFlightEnabled() ? "FLIGHT • ON" : "FLIGHT");
    }

    private static void applyState(ControlState state) {
        apply(goSuper, state.goSuperVisible, state.goSuperEnabled);
        apply(flight, state.flightVisible, state.flightEnabled);
        apply(superBoost, state.superBoostVisible, state.superBoostEnabled);
        apply(chaosBurst, state.chaosBurstVisible, state.chaosBurstEnabled);
    }

    private static void apply(Button button, boolean visible, boolean enabled) {
        if (button == null) return;
        button.setVisibility(visible ? View.VISIBLE : View.GONE);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 0.94f : 0.45f);
    }

    private static Button makeButton(Activity activity, String label, Runnable action) {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextSize(12f);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setBackground(roundedBackground(0xCC0B5FA5));
        button.setOnClickListener(v -> {
            if (button.isEnabled()) action.run();
        });
        return button;
    }

    private static void addAbilityButton(Activity activity, Button button, int index) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dp(activity, BUTTON_WIDTH_DP), dp(activity, BUTTON_HEIGHT_DP));
        lp.gravity = Gravity.END | Gravity.BOTTOM;
        lp.rightMargin = dp(activity, EDGE_DP);
        lp.bottomMargin = dp(activity, EDGE_DP + index * (BUTTON_HEIGHT_DP + GAP_DP));
        root.addView(button, lp);
    }

    private static GradientDrawable roundedBackground(int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(24f);
        return bg;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    /** Draws the seven emeralds using a small perspective-projected faceted gem model. */
    static final class SuperFxView extends View {
        private static final int[] EMERALD_COLORS = {
                0xFFFF3030, 0xFF287BFF, 0xFFFFE02E, 0xFF39E66B,
                0xFF36E8FF, 0xFFC15CFF, 0xFFF5F8FF
        };
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint auraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private long transformStarted;
        private long boostStarted;
        private long burstStarted;
        private boolean transformIn;
        private boolean transformOut;
        private boolean superActive;
        private boolean flightActive;

        SuperFxView(Activity activity) {
            super(activity);
            setWillNotDraw(false);
        }

        void startTransformIn() {
            transformStarted = System.nanoTime();
            transformIn = true;
            transformOut = false;
            superActive = false;
            invalidate();
        }

        void startTransformOut() {
            transformStarted = System.nanoTime();
            transformOut = true;
            transformIn = false;
            superActive = true;
            invalidate();
        }

        void setSuperActive(boolean active) {
            superActive = active;
            if (!active) flightActive = false;
            transformIn = false;
            transformOut = false;
            invalidate();
        }

        void setFlightActive(boolean active) {
            flightActive = active;
            invalidate();
        }

        void triggerBoost() {
            boostStarted = System.nanoTime();
            invalidate();
        }

        void triggerChaosBurst() {
            burstStarted = System.nanoTime();
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (getWidth() <= 0 || getHeight() <= 0) return;
            long now = System.nanoTime();
            float cx = getWidth() * 0.50f;
            float cy = getHeight() * 0.54f;

            if (superActive || transformOut) drawAura(canvas, cx, cy, now);
            if (transformIn || transformOut) drawEmeraldOrbit(canvas, cx, cy, now);
            drawBoost(canvas, cx, cy, now);
            drawBurst(canvas, cx, cy, now);

            if (superActive || transformIn || transformOut || flightActive
                    || elapsedMs(now, boostStarted) < 650f || elapsedMs(now, burstStarted) < 950f) {
                postInvalidateOnAnimation();
            }
        }

        private void drawAura(Canvas canvas, float cx, float cy, long now) {
            float pulse = 0.92f + 0.08f * (float)Math.sin(now / 170_000_000.0);
            float radius = Math.min(getWidth(), getHeight()) * 0.115f * pulse;
            auraPaint.setShader(new RadialGradient(cx, cy, radius,
                    new int[]{0x55FFFFFF, 0x88FFD72E, 0x33FF9D00, 0x00FFB300},
                    new float[]{0f, 0.32f, 0.68f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, radius, auraPaint);
            auraPaint.setShader(null);

            paint.setStrokeWidth(Math.max(2f, radius * 0.035f));
            for (int i = 0; i < 10; i++) {
                double a = now / 310_000_000.0 + i * 0.628;
                float rr = radius * (0.62f + 0.24f * ((i % 3) / 2f));
                float x = cx + (float)Math.cos(a) * rr;
                float y = cy + (float)Math.sin(a * 1.23) * rr * 0.68f;
                paint.setColor(i % 2 == 0 ? 0xDDFFF08A : 0xAAFFFFFF);
                canvas.drawCircle(x, y, Math.max(2.5f, radius * 0.025f), paint);
            }

            if (flightActive) {
                paint.setColor(0xAAFFE45A);
                paint.setStrokeWidth(Math.max(3f, radius * 0.05f));
                for (int i = 0; i < 5; i++) {
                    float yy = cy + radius * 0.15f + i * radius * 0.16f;
                    canvas.drawLine(cx - radius * 0.55f, yy,
                            cx + radius * 0.55f, yy + radius * 0.08f, paint);
                }
            }
        }

        private void drawEmeraldOrbit(Canvas canvas, float cx, float cy, long now) {
            float elapsed = elapsedMs(now, transformStarted);
            float duration = transformIn ? TRANSFORM_IN_MS : TRANSFORM_OUT_MS;
            float t = clamp(elapsed / duration, 0f, 1f);
            if (t >= 1f) {
                transformIn = false;
                transformOut = false;
                return;
            }
            float inward = transformIn ? smooth(t) : 1f - smooth(t);
            float baseRadius = Math.min(getWidth(), getHeight()) * 0.19f;
            float radius = baseRadius * (1f - 0.82f * inward);
            float speed = 1.2f + inward * 5.4f;
            float squash = 0.56f;

            for (int i = 0; i < 7; i++) {
                double angle = (now / 1_000_000_000.0) * speed + i * (Math.PI * 2.0 / 7.0);
                float depth = (float)Math.sin(angle);
                float x = cx + (float)Math.cos(angle) * radius;
                float y = cy + (float)Math.sin(angle) * radius * squash;
                float perspective = 0.78f + 0.28f * ((depth + 1f) * 0.5f);
                float size = Math.min(getWidth(), getHeight()) * 0.034f * perspective * (1f - inward * 0.18f);
                drawGem(canvas, x, y, size, EMERALD_COLORS[i], (float)angle);
            }
        }

        private void drawGem(Canvas canvas, float cx, float cy, float s, int color, float phase) {
            float tilt = (float)Math.sin(phase) * s * 0.20f;
            Path outer = new Path();
            outer.moveTo(cx + tilt, cy - s * 1.28f);
            outer.lineTo(cx + s * 0.78f, cy - s * 0.28f);
            outer.lineTo(cx + s * 0.58f, cy + s * 0.72f);
            outer.lineTo(cx + tilt, cy + s * 1.18f);
            outer.lineTo(cx - s * 0.58f, cy + s * 0.72f);
            outer.lineTo(cx - s * 0.78f, cy - s * 0.28f);
            outer.close();
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(outer, paint);

            int bright = blend(color, Color.WHITE, 0.43f);
            int dark = blend(color, Color.BLACK, 0.34f);
            Path left = new Path();
            left.moveTo(cx + tilt, cy - s * 1.28f);
            left.lineTo(cx, cy + s * 0.08f);
            left.lineTo(cx - s * 0.78f, cy - s * 0.28f);
            left.close();
            paint.setColor(bright);
            canvas.drawPath(left, paint);

            Path right = new Path();
            right.moveTo(cx, cy + s * 0.08f);
            right.lineTo(cx + s * 0.58f, cy + s * 0.72f);
            right.lineTo(cx + tilt, cy + s * 1.18f);
            right.lineTo(cx - s * 0.58f, cy + s * 0.72f);
            right.close();
            paint.setColor(dark);
            canvas.drawPath(right, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1.5f, s * 0.07f));
            paint.setColor(0xCCFFFFFF);
            canvas.drawPath(outer, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawBoost(Canvas canvas, float cx, float cy, long now) {
            float t = elapsedMs(now, boostStarted) / 650f;
            if (t < 0f || t >= 1f) return;
            paint.setStrokeWidth(Math.max(3f, getHeight() * 0.008f));
            for (int i = 0; i < 8; i++) {
                float y = cy - getHeight() * 0.10f + i * getHeight() * 0.028f;
                float len = getWidth() * (0.12f + 0.16f * (1f - t));
                paint.setColor(i % 2 == 0 ? 0xCCFFF073 : 0x99FFFFFF);
                canvas.drawLine(cx - len - i * 7f, y, cx - getWidth() * 0.035f, y, paint);
            }
        }

        private void drawBurst(Canvas canvas, float cx, float cy, long now) {
            float t = elapsedMs(now, burstStarted) / 950f;
            if (t < 0f || t >= 1f) return;
            float maxR = Math.min(getWidth(), getHeight()) * 0.31f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(3f, getHeight() * 0.009f * (1f - t * 0.5f)));
            paint.setColor((Math.max(0, (int)(210 * (1f - t))) << 24) | 0x00FFE45A);
            canvas.drawCircle(cx, cy, maxR * smooth(t), paint);
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 14; i++) {
                double a = i * Math.PI * 2.0 / 14.0;
                float r = maxR * smooth(t);
                float x = cx + (float)Math.cos(a) * r;
                float y = cy + (float)Math.sin(a) * r * 0.65f;
                int color = EMERALD_COLORS[i % EMERALD_COLORS.length];
                paint.setColor((0xCC << 24) | (color & 0x00FFFFFF));
                canvas.drawCircle(x, y, Math.max(3f, getHeight() * 0.008f * (1f - t * 0.4f)), paint);
            }
        }

        private static float elapsedMs(long now, long start) {
            if (start == 0L) return Float.POSITIVE_INFINITY;
            return (now - start) / 1_000_000f;
        }

        private static float clamp(float v, float lo, float hi) {
            return Math.max(lo, Math.min(hi, v));
        }

        private static float smooth(float t) {
            t = clamp(t, 0f, 1f);
            return t * t * (3f - 2f * t);
        }

        private static int blend(int a, int b, float t) {
            t = clamp(t, 0f, 1f);
            int ar = Color.red(a), ag = Color.green(a), ab = Color.blue(a);
            int br = Color.red(b), bg = Color.green(b), bb = Color.blue(b);
            return Color.rgb(
                    (int)(ar + (br - ar) * t),
                    (int)(ag + (bg - ag) * t),
                    (int)(ab + (bb - ab) * t));
        }
    }

    // Native callbacks remain for compatibility with the native runtime API 2.0.
    public static native void nativeToggleSuper();
    public static native void nativeToggleFlight();
    public static native void nativeSuperBoost();
    public static native void nativeChaosBurst();
}
