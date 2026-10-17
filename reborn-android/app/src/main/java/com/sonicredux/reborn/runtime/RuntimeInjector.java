package com.sonicredux.reborn.runtime;

import android.content.Context;

import com.sonicredux.reborn.core.ApkZipWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class RuntimeInjector {
    private static final String[] ABIS = {"arm64-v8a", "armeabi-v7a"};

    private RuntimeInjector() {}

    public static void inject(Context context, File inputApk, File outputApk) throws Exception {
        Map<String, byte[]> payload = new LinkedHashMap<>();
        for (String path : RuntimePayload.requiredPaths()) {
            try (InputStream in = RuntimePayload.open(context, path)) {
                payload.put(path, ApkZipWriter.readAll(in));
            }
        }
        inject(inputApk, outputApk, payload);
    }

    static void inject(File inputApk, File outputApk, Map<String, byte[]> payload) throws Exception {
        if (inputApk == null || !inputApk.isFile()) throw new IllegalArgumentException("input APK is missing");
        if (outputApk == null) throw new IllegalArgumentException("output APK is required");
        for (String path : RuntimePayload.requiredPaths()) {
            if (payload == null || !payload.containsKey(path) || payload.get(path) == null) {
                throw new IllegalArgumentException("runtime payload is missing " + path);
            }
        }

        try (ZipFile in = new ZipFile(inputApk)) {
            if (containsRuntime(in)) throw new IllegalArgumentException("APK already contains Re-born runtime");
            for (String abi : ABIS) {
                String main = "lib/" + abi + "/libmain.so";
                if (in.getEntry(main) == null) throw new IllegalArgumentException("APK is missing " + main);
            }

            String runtimeDex = nextDexName(in);
            try (FileOutputStream fos = new FileOutputStream(outputApk);
                 ApkZipWriter.CountingOutputStream counting = new ApkZipWriter.CountingOutputStream(fos);
                 ZipOutputStream out = new ZipOutputStream(counting)) {
                Enumeration<? extends ZipEntry> entries = in.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (ApkZipWriter.isSignatureEntry(name)) continue;
                    if (entry.isDirectory()) {
                        ZipEntry dir = new ZipEntry(name);
                        out.putNextEntry(dir);
                        out.closeEntry();
                        continue;
                    }

                    byte[] bytes = ApkZipWriter.readAll(in.getInputStream(entry));
                    String renamed = renamedOriginalMain(name);
                    ApkZipWriter.writeEntry(out, counting, renamed == null ? name : renamed, bytes, entry.getMethod());
                }

                byte[] dex = payload.get("reborn-runtime/classes.dex");
                ApkZipWriter.writeEntry(out, counting, runtimeDex, dex, ZipEntry.DEFLATED);

                for (String abi : ABIS) {
                    ApkZipWriter.writeEntry(out, counting,
                            "lib/" + abi + "/libmain.so",
                            payload.get("reborn-runtime/" + abi + "/libmain.so"),
                            ZipEntry.STORED);
                    ApkZipWriter.writeEntry(out, counting,
                            "lib/" + abi + "/libreborn.so",
                            payload.get("reborn-runtime/" + abi + "/libreborn.so"),
                            ZipEntry.STORED);
                }
            }
        }
        verifyInjected(outputApk);
    }

    private static boolean containsRuntime(ZipFile zip) {
        for (String abi : ABIS) {
            if (zip.getEntry("lib/" + abi + "/libmain_redux.so") != null ||
                    zip.getEntry("lib/" + abi + "/libreborn.so") != null) return true;
        }
        return false;
    }

    private static String renamedOriginalMain(String name) {
        for (String abi : ABIS) {
            if (name.equals("lib/" + abi + "/libmain.so")) return "lib/" + abi + "/libmain_redux.so";
        }
        return null;
    }

    private static String nextDexName(ZipFile zip) {
        for (int index = 3; index < 1000; index++) {
            String name = "classes" + index + ".dex";
            if (zip.getEntry(name) == null) return name;
        }
        throw new IllegalStateException("could not find a free DEX slot");
    }

    private static void verifyInjected(File apk) throws Exception {
        try (ZipFile zip = new ZipFile(apk)) {
            for (String abi : ABIS) {
                String prefix = "lib/" + abi + "/";
                if (zip.getEntry(prefix + "libmain.so") == null) throw new IllegalStateException("runtime wrapper missing for " + abi);
                if (zip.getEntry(prefix + "libmain_redux.so") == null) throw new IllegalStateException("original Redux main missing for " + abi);
                if (zip.getEntry(prefix + "libreborn.so") == null) throw new IllegalStateException("Re-born runtime missing for " + abi);
            }
            ZipEntry resources = zip.getEntry("resources.arsc");
            if (resources != null && resources.getMethod() != ZipEntry.STORED) {
                throw new IllegalStateException("resources.arsc must remain stored uncompressed");
            }
        }
    }
}
