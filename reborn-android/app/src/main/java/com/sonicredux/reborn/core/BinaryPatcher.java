package com.sonicredux.reborn.core;

import java.util.Arrays;

public final class BinaryPatcher {
    private BinaryPatcher() {}

    public static byte[] replaceSameLength(byte[] input, byte[] from, byte[] to, boolean requireMatch) {
        if (input == null || from == null || to == null || from.length == 0) {
            throw new IllegalArgumentException("input/from/to must be non-null and pattern non-empty");
        }
        if (from.length != to.length) {
            throw new IllegalArgumentException("replacement must be exactly the same byte length");
        }
        byte[] out = Arrays.copyOf(input, input.length);
        int matches = 0;
        for (int i = 0; i <= out.length - from.length; i++) {
            boolean same = true;
            for (int j = 0; j < from.length; j++) {
                if (out[i + j] != from[j]) { same = false; break; }
            }
            if (same) {
                System.arraycopy(to, 0, out, i, to.length);
                matches++;
                i += from.length - 1;
            }
        }
        if (requireMatch && matches == 0) {
            throw new IllegalArgumentException("required byte pattern was not found");
        }
        return out;
    }
}
