package com.sonicredux.reborn.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class ZipOverlayBuilder {
    private ZipOverlayBuilder() {}

    private static final class Payload {
        final ModManifest manifest;
        final Map<String, byte[]> files;
        Payload(ModManifest manifest, Map<String, byte[]> files) { this.manifest = manifest; this.files = files; }
    }

    public static ModManifest validateMod(File modFile) throws Exception {
        return readPayload(modFile).manifest;
    }

    public static void build(File baseline, List<File> modFiles, File output, String oldPackage, String newPackage) throws Exception {
        if (oldPackage.length() != newPackage.length()) {
            throw new IllegalArgumentException("package IDs must have equal length for safe binary patching");
        }
        List<Payload> payloads = new ArrayList<>();
        List<ModManifest> manifests = new ArrayList<>();
        for (File mod : modFiles) {
            Payload p = readPayload(mod);
            payloads.add(p);
            manifests.add(p.manifest);
        }
        List<ModConflict> conflicts = ConflictDetector.find(manifests);
        if (!conflicts.isEmpty()) {
            ModConflict c = conflicts.get(0);
            throw new IllegalArgumentException("mod conflict on " + c.path + ": " + c.modNames);
        }
        Map<String, byte[]> replacements = new LinkedHashMap<>();
        for (Payload p : payloads) replacements.putAll(p.files);

        byte[] oldUtf16 = oldPackage.getBytes(StandardCharsets.UTF_16LE);
        byte[] newUtf16 = newPackage.getBytes(StandardCharsets.UTF_16LE);
        byte[] oldLabel = "Sonic Redux".getBytes(StandardCharsets.UTF_8);
        byte[] newLabel = "SonicReborn".getBytes(StandardCharsets.UTF_8);

        try (ZipFile in = new ZipFile(baseline); ZipOutputStream out = new ZipOutputStream(new FileOutputStream(output))) {
            Enumeration<? extends ZipEntry> entries = in.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName();
                if (isSignatureEntry(name) || replacements.containsKey(name)) continue;
                ZipEntry copy = new ZipEntry(name);
                copy.setTime(e.getTime());
                if (e.isDirectory()) {
                    out.putNextEntry(copy); out.closeEntry(); continue;
                }
                byte[] bytes = readAll(in.getInputStream(e));
                if (name.equals("AndroidManifest.xml") || name.equals("resources.arsc")) {
                    bytes = BinaryPatcher.replaceSameLength(bytes, oldUtf16, newUtf16, true);
                }
                if (name.equals("resources.arsc")) {
                    bytes = BinaryPatcher.replaceSameLength(bytes, oldLabel, newLabel, false);
                }
                out.putNextEntry(copy);
                out.write(bytes);
                out.closeEntry();
            }
            for (Map.Entry<String, byte[]> r : replacements.entrySet()) {
                ZipEntry e = new ZipEntry(r.getKey());
                out.putNextEntry(e);
                out.write(r.getValue());
                out.closeEntry();
            }
        }
        verifyCoreEntries(output);
    }

    private static Payload readPayload(File modFile) throws Exception {
        try (ZipFile z = new ZipFile(modFile)) {
            ZipEntry manifestEntry = z.getEntry("manifest.json");
            if (manifestEntry == null || manifestEntry.isDirectory()) throw new IllegalArgumentException("mod is missing manifest.json");
            ModManifest manifest = ModManifest.fromJson(new String(readAll(z.getInputStream(manifestEntry)), StandardCharsets.UTF_8));
            Set<String> declared = new HashSet<>(manifest.targets);
            Map<String, byte[]> files = new LinkedHashMap<>();
            Enumeration<? extends ZipEntry> entries = z.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory() || e.getName().equals("manifest.json") || e.getName().equals("icon.png")) continue;
                if (!e.getName().startsWith("payload/")) throw new IllegalArgumentException("undeclared file outside payload/: " + e.getName());
                String target = ModManifest.normalizeTarget(e.getName().substring("payload/".length()));
                if (!declared.contains(target)) throw new IllegalArgumentException("payload target not declared: " + target);
                if (files.put(target, readAll(z.getInputStream(e))) != null) throw new IllegalArgumentException("duplicate payload: " + target);
            }
            for (String target : declared) {
                if (!files.containsKey(target)) throw new IllegalArgumentException("declared target has no payload: " + target);
            }
            return new Payload(manifest, files);
        }
    }

    private static boolean isSignatureEntry(String name) {
        String upper = name.toUpperCase();
        return upper.startsWith("META-INF/") && (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC") || upper.equals("META-INF/MANIFEST.MF"));
    }

    private static byte[] readAll(InputStream in) throws Exception {
        try (InputStream stream = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = stream.read(buf)) != -1) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static void verifyCoreEntries(File apk) throws Exception {
        try (ZipFile z = new ZipFile(apk)) {
            String[] required = {
                    "AndroidManifest.xml", "resources.arsc",
                    "lib/arm64-v8a/libunity.so", "lib/armeabi-v7a/libunity.so"
            };
            for (String p : required) if (z.getEntry(p) == null) throw new IllegalStateException("rebuilt APK lost required entry: " + p);
        }
    }
}
