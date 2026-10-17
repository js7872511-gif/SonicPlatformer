package com.sonicredux.reborn.runtime;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

public class RuntimeInjectorTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private static void stored(ZipOutputStream out, String name, byte[] bytes) throws Exception {
        CRC32 crc = new CRC32();
        crc.update(bytes);
        ZipEntry e = new ZipEntry(name);
        e.setMethod(ZipEntry.STORED);
        e.setSize(bytes.length);
        e.setCompressedSize(bytes.length);
        e.setCrc(crc.getValue());
        out.putNextEntry(e);
        out.write(bytes);
        out.closeEntry();
    }

    private File baseApk(boolean includeClasses3) throws Exception {
        File base = temp.newFile("base.apk");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(base))) {
            out.putNextEntry(new ZipEntry("classes.dex")); out.write(new byte[]{1}); out.closeEntry();
            out.putNextEntry(new ZipEntry("classes2.dex")); out.write(new byte[]{2}); out.closeEntry();
            if (includeClasses3) { out.putNextEntry(new ZipEntry("classes3.dex")); out.write(new byte[]{3}); out.closeEntry(); }
            out.putNextEntry(new ZipEntry("lib/arm64-v8a/libmain.so")); out.write(new byte[]{10, 11}); out.closeEntry();
            out.putNextEntry(new ZipEntry("lib/armeabi-v7a/libmain.so")); out.write(new byte[]{12, 13}); out.closeEntry();
            stored(out, "resources.arsc", new byte[]{20, 21, 22, 23});
            out.putNextEntry(new ZipEntry("META-INF/CERT.SF")); out.write(new byte[]{30}); out.closeEntry();
        }
        return base;
    }

    private static Map<String, byte[]> payload() {
        Map<String, byte[]> p = new LinkedHashMap<>();
        p.put("reborn-runtime/classes.dex", new byte[]{40, 41});
        p.put("reborn-runtime/arm64-v8a/libmain.so", new byte[]{50, 51});
        p.put("reborn-runtime/arm64-v8a/libreborn.so", new byte[]{52, 53});
        p.put("reborn-runtime/armeabi-v7a/libmain.so", new byte[]{60, 61});
        p.put("reborn-runtime/armeabi-v7a/libreborn.so", new byte[]{62, 63});
        return p;
    }

    @Test public void injectsWrapperRuntimeAndNextDexWithoutLosingOriginalMain() throws Exception {
        File input = baseApk(false), output = temp.newFile("out.apk");
        RuntimeInjector.inject(input, output, payload());

        try (ZipFile z = new ZipFile(output)) {
            assertArrayEquals(new byte[]{10, 11}, z.getInputStream(z.getEntry("lib/arm64-v8a/libmain_redux.so")).readAllBytes());
            assertArrayEquals(new byte[]{12, 13}, z.getInputStream(z.getEntry("lib/armeabi-v7a/libmain_redux.so")).readAllBytes());
            assertArrayEquals(new byte[]{50, 51}, z.getInputStream(z.getEntry("lib/arm64-v8a/libmain.so")).readAllBytes());
            assertArrayEquals(new byte[]{52, 53}, z.getInputStream(z.getEntry("lib/arm64-v8a/libreborn.so")).readAllBytes());
            assertArrayEquals(new byte[]{60, 61}, z.getInputStream(z.getEntry("lib/armeabi-v7a/libmain.so")).readAllBytes());
            assertArrayEquals(new byte[]{62, 63}, z.getInputStream(z.getEntry("lib/armeabi-v7a/libreborn.so")).readAllBytes());
            assertArrayEquals(new byte[]{40, 41}, z.getInputStream(z.getEntry("classes3.dex")).readAllBytes());
            assertNull(z.getEntry("META-INF/CERT.SF"));
            assertEquals(ZipEntry.STORED, z.getEntry("resources.arsc").getMethod());
        }
    }

    @Test public void choosesFirstFreeDexFromClasses3Upward() throws Exception {
        File input = baseApk(true), output = temp.newFile("out2.apk");
        RuntimeInjector.inject(input, output, payload());
        try (ZipFile z = new ZipFile(output)) {
            assertArrayEquals(new byte[]{3}, z.getInputStream(z.getEntry("classes3.dex")).readAllBytes());
            assertArrayEquals(new byte[]{40, 41}, z.getInputStream(z.getEntry("classes4.dex")).readAllBytes());
        }
    }

    @Test public void rejectsAlreadyInjectedApk() throws Exception {
        File input = baseApk(false), once = temp.newFile("once.apk"), twice = temp.newFile("twice.apk");
        RuntimeInjector.inject(input, once, payload());
        try {
            RuntimeInjector.inject(once, twice, payload());
            fail("expected rejection");
        } catch (IllegalArgumentException expected) {
            assertEquals("APK already contains Re-born runtime", expected.getMessage());
        }
    }
}
