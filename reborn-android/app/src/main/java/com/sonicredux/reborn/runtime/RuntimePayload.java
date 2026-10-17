package com.sonicredux.reborn.runtime;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class RuntimePayload {
    private static final List<String> REQUIRED = Collections.unmodifiableList(Arrays.asList(
            "reborn-runtime/classes.dex",
            "reborn-runtime/arm64-v8a/libmain.so",
            "reborn-runtime/arm64-v8a/libreborn.so",
            "reborn-runtime/armeabi-v7a/libmain.so",
            "reborn-runtime/armeabi-v7a/libreborn.so"
    ));

    private RuntimePayload() {}

    public static List<String> requiredPaths() {
        return REQUIRED;
    }

    public static InputStream open(Context context, String path) throws IOException {
        if (context == null) throw new IllegalArgumentException("context is required");
        if (!REQUIRED.contains(path)) throw new IllegalArgumentException("unknown runtime payload path: " + path);
        return context.getAssets().open(path);
    }
}
