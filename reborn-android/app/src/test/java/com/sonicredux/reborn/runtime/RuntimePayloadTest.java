package com.sonicredux.reborn.runtime;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.*;

public class RuntimePayloadTest {
    @Test public void requiredPathsAreComplete() {
        List<String> paths = RuntimePayload.requiredPaths();
        assertEquals(Arrays.asList(
                "reborn-runtime/classes.dex",
                "reborn-runtime/arm64-v8a/libmain.so",
                "reborn-runtime/arm64-v8a/libreborn.so",
                "reborn-runtime/armeabi-v7a/libmain.so",
                "reborn-runtime/armeabi-v7a/libreborn.so"
        ), paths);
        assertEquals(paths.size(), new HashSet<>(paths).size());
    }
}
