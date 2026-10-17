package com.sonicredux.reborn.runtimeui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;

public final class RebornOverlay {
    private static final int BUTTON_WIDTH_DP = 132;
    private static final int BUTTON_HEIGHT_DP = 52;
    private static final int EDGE_DP = 18;
    private static final int GAP_DP = 10;

    private static WeakReference<Activity> activityRef = new WeakReference<>(null);
    private static FrameLayout root;
    private static TextView status;
    private static Button goSuper;
    private static Button flight;
    private static Button superBoost;
    private static Button chaosBurst;

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

        ControlState(boolean superState, boolean transforming) {
            goSuperVisible = true;
            goSuperEnabled = !transforming;
            flightVisible = superState;
            flightEnabled = superState && !transforming;
            superBoostVisible = superState;
            superBoostEnabled = superState && !transforming;
            chaosBurstVisible = superState;
            chaosBurstEnabled = superState && !transforming;
        }
    }

    static ControlState controlState(boolean superState, boolean transforming) {
        return new ControlState(superState, transforming);
    }

    public static void install(Activity activity) {
        if (activity == null) return;
        activityRef = new WeakReference<>(activity);
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

            status = new TextView(activity);
            status.setText("REBORN RUNTIME STARTING");
            status.setTextColor(Color.WHITE);
            status.setTextSize(12f);
            status.setGravity(Gravity.CENTER);
            status.setBackground(roundedBackground(0xAA111827));
            FrameLayout.LayoutParams statusLp = new FrameLayout.LayoutParams(dp(activity, 210), dp(activity, 36));
            statusLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            statusLp.topMargin = dp(activity, 12);
            root.addView(status, statusLp);

            goSuper = makeButton(activity, "GO SUPER", RebornOverlay::nativeToggleSuper);
            flight = makeButton(activity, "FLIGHT", RebornOverlay::nativeToggleFlight);
            superBoost = makeButton(activity, "SUPER BOOST", RebornOverlay::nativeSuperBoost);
            chaosBurst = makeButton(activity, "CHAOS BURST", RebornOverlay::nativeChaosBurst);

            addAbilityButton(activity, goSuper, 0);
            addAbilityButton(activity, flight, 1);
            addAbilityButton(activity, superBoost, 2);
            addAbilityButton(activity, chaosBurst, 3);
            applyState(controlState(false, false));
        });
    }

    public static void setRuntimeStatus(String text) {
        Activity activity = activityRef.get();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            if (status != null) status.setText(text == null ? "REBORN RUNTIME" : text);
        });
    }

    public static void setSuperState(boolean superState, boolean transforming) {
        Activity activity = activityRef.get();
        if (activity == null) return;
        ControlState state = controlState(superState, transforming);
        activity.runOnUiThread(() -> applyState(state));
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

    public static native void nativeToggleSuper();
    public static native void nativeToggleFlight();
    public static native void nativeSuperBoost();
    public static native void nativeChaosBurst();
}
