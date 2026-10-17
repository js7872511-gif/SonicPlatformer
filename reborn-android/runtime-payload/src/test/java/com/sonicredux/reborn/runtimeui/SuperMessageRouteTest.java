package com.sonicredux.reborn.runtimeui;

import org.junit.Test;

import static org.junit.Assert.*;

public class SuperMessageRouteTest {
    @Test public void superUsesReduxKorokMessageRouteWithoutNativeRendererBridge() {
        assertEquals("Korok", SuperMessageRoute.playerObject());
        assertEquals("OnInvincible", SuperMessageRoute.invincibilityMethod());
        assertFalse(SuperMessageRoute.usesNativeRendererBridge());
    }
}
