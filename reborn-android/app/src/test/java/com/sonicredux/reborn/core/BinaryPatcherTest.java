package com.sonicredux.reborn.core;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class BinaryPatcherTest {
    @Test public void replacesSameLengthUtf16Package() {
        String from = "com.SpecialTeams.SonicRedux";
        String to = "com.RebornXTeams.SonicRedux";
        byte[] data = ("xx" + from + "yy").getBytes(StandardCharsets.UTF_16LE);
        byte[] out = BinaryPatcher.replaceSameLength(data,
                from.getBytes(StandardCharsets.UTF_16LE),
                to.getBytes(StandardCharsets.UTF_16LE), true);
        assertTrue(new String(out, StandardCharsets.UTF_16LE).contains(to));
        assertFalse(new String(out, StandardCharsets.UTF_16LE).contains(from));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDifferentLengthReplacement() {
        BinaryPatcher.replaceSameLength(new byte[]{1,2,3}, new byte[]{1}, new byte[]{1,2}, true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void requiresAtLeastOneMatchWhenRequested() {
        BinaryPatcher.replaceSameLength(new byte[]{1,2,3}, new byte[]{9}, new byte[]{8}, true);
    }
}
