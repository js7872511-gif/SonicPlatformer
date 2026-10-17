package com.sonicredux.reborn.runtimeui;

import org.junit.Test;

import static org.junit.Assert.*;

public class SuperRuntimeStateTest {
    @Test public void togglesThroughStableStatesAndIgnoresRapidInput() {
        SuperRuntimeState state = new SuperRuntimeState();
        assertEquals(SuperRuntimeState.Mode.NORMAL, state.mode());

        assertTrue(state.toggleSuper());
        assertEquals(SuperRuntimeState.Mode.TRANSFORMING_IN, state.mode());
        assertFalse(state.toggleSuper());

        state.finishTransition();
        assertEquals(SuperRuntimeState.Mode.SUPER, state.mode());
        assertTrue(state.isSuper());

        assertTrue(state.toggleSuper());
        assertEquals(SuperRuntimeState.Mode.TRANSFORMING_OUT, state.mode());
        assertFalse(state.toggleSuper());

        state.finishTransition();
        assertEquals(SuperRuntimeState.Mode.NORMAL, state.mode());
        assertFalse(state.isSuper());
    }

    @Test public void flightOnlyTogglesWhileStableSuper() {
        SuperRuntimeState state = new SuperRuntimeState();
        assertFalse(state.toggleFlight());

        state.toggleSuper();
        assertFalse(state.toggleFlight());
        state.finishTransition();
        assertTrue(state.toggleFlight());
        assertTrue(state.isFlightEnabled());
        assertTrue(state.toggleFlight());
        assertFalse(state.isFlightEnabled());

        state.toggleFlight();
        assertTrue(state.isFlightEnabled());
        state.toggleSuper();
        assertFalse(state.isFlightEnabled());
    }

    @Test public void forceNormalIsIdempotent() {
        SuperRuntimeState state = new SuperRuntimeState();
        state.toggleSuper();
        state.finishTransition();
        state.toggleFlight();

        state.forceNormal();
        assertEquals(SuperRuntimeState.Mode.NORMAL, state.mode());
        assertFalse(state.isFlightEnabled());

        state.forceNormal();
        assertEquals(SuperRuntimeState.Mode.NORMAL, state.mode());
        assertFalse(state.isFlightEnabled());
    }
}
