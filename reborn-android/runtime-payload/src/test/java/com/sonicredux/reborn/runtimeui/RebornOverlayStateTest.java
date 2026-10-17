package com.sonicredux.reborn.runtimeui;

import org.junit.Test;

import static org.junit.Assert.*;

public class RebornOverlayStateTest {
    @Test public void normalStateShowsOnlyGoSuper() {
        RebornOverlay.ControlState state = RebornOverlay.controlState(false, false);
        assertTrue(state.goSuperVisible);
        assertTrue(state.goSuperEnabled);
        assertFalse(state.flightVisible);
        assertFalse(state.superBoostVisible);
        assertFalse(state.chaosBurstVisible);
    }

    @Test public void superStateShowsAllSuperAbilities() {
        RebornOverlay.ControlState state = RebornOverlay.controlState(true, false);
        assertTrue(state.goSuperVisible);
        assertTrue(state.goSuperEnabled);
        assertTrue(state.flightVisible);
        assertTrue(state.flightEnabled);
        assertTrue(state.superBoostVisible);
        assertTrue(state.superBoostEnabled);
        assertTrue(state.chaosBurstVisible);
        assertTrue(state.chaosBurstEnabled);
    }

    @Test public void transformingStateDisablesConflictingPresses() {
        RebornOverlay.ControlState state = RebornOverlay.controlState(true, true);
        assertTrue(state.goSuperVisible);
        assertFalse(state.goSuperEnabled);
        assertTrue(state.flightVisible);
        assertFalse(state.flightEnabled);
        assertTrue(state.superBoostVisible);
        assertFalse(state.superBoostEnabled);
        assertTrue(state.chaosBurstVisible);
        assertFalse(state.chaosBurstEnabled);
    }
}
