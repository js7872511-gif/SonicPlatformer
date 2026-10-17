package com.sonicredux.reborn.runtimeui;

/** Pure state machine for the built-in Super Sonic runtime mod. */
public final class SuperRuntimeState {
    public enum Mode { NORMAL, TRANSFORMING_IN, SUPER, TRANSFORMING_OUT }

    private Mode mode = Mode.NORMAL;
    private boolean flightEnabled;

    public synchronized Mode mode() {
        return mode;
    }

    public synchronized boolean isSuper() {
        return mode == Mode.SUPER;
    }

    public synchronized boolean isTransforming() {
        return mode == Mode.TRANSFORMING_IN || mode == Mode.TRANSFORMING_OUT;
    }

    /** True while the real Sonic renderer should use the Super visual. */
    public synchronized boolean wantsSuperVisual() {
        return mode == Mode.TRANSFORMING_IN || mode == Mode.SUPER;
    }

    public synchronized boolean isFlightEnabled() {
        return flightEnabled;
    }

    /** Returns false when a transform is already in progress. */
    public synchronized boolean toggleSuper() {
        if (isTransforming()) return false;
        if (mode == Mode.NORMAL) {
            flightEnabled = false;
            mode = Mode.TRANSFORMING_IN;
            return true;
        }
        if (mode == Mode.SUPER) {
            flightEnabled = false;
            mode = Mode.TRANSFORMING_OUT;
            return true;
        }
        return false;
    }

    /** Finishes the currently active transform, if any. */
    public synchronized void finishTransition() {
        if (mode == Mode.TRANSFORMING_IN) {
            mode = Mode.SUPER;
        } else if (mode == Mode.TRANSFORMING_OUT) {
            mode = Mode.NORMAL;
            flightEnabled = false;
        }
    }

    /** Returns the new flight state; false also means flight is unavailable. */
    public synchronized boolean toggleFlight() {
        if (mode != Mode.SUPER) return false;
        flightEnabled = !flightEnabled;
        return flightEnabled;
    }

    public synchronized void forceNormal() {
        flightEnabled = false;
        mode = Mode.NORMAL;
    }
}
