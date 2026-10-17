package com.sonicredux.reborn.core;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

        try (ZipFile in = new ZipFile(baseline);
             FileOutputStream fos = new FileOutputStream(output);
             ApkZipWriter.CountingOutputStream counting = new ApkZipWriter.CountingOutputStream(fos);
             ZipOutputStream out = new ZipOutputStream(counting)) {
            Enumeration<? extends ZipEntry> entries = in.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (ApkZipWriter.isSignatureEntry(name) || replacements.containsKey(name)) continue;
                if (entry.isDirectory()) {
                    ZipEntry dir = new ZipEntry(name);
                    out.putNextEntry(dir);
                    out.closeEntry();
                    continue;
                }
                byte[] bytes = ApkZipWriter.readAll(in.getInputStream(entry));
                if (name.equals("AndroidManifest.xml") || name.equals("resources.arsc")) {
                    bytes = BinaryPatcher.replaceSameLength(bytes, oldUtf16, newUtf16, true);
                }
                if (name.equals("resources.arsc")) {
                    bytes = BinaryPatcher.replaceSameLength(bytes, oldLabel, newLabel, false);
                }
                ApkZipWriter.writeEntry(out, counting, name, bytes, entry.getMethod());
            }
            for (Map.Entry<String, byte[]> replacement : replacements.entrySet()) {
                ZipEntry original = in.getEntry(replacement.getKey());
                int method = original == null ? ZipEntry.DEFLATED : original.getMethod();
                ApkZipWriter.writeEntry(out, counting, replacement.getKey(), replacement.getValue(), method);
            }
        }
        verifyCoreEntries(output);
    }

    private static Payload readPayload(File modFile) throws Exception {
        try (ZipFile zip = new ZipFile(modFile)) {
            ZipEntry manifestEntry = zip.getEntry("manifest.json");
            if (manifestEntry == null || manifestEntry.isDirectory()) {
                throw new IllegalArgumentException("mod is missing manifest.json");
            }
            ModManifest manifest = ModManifest.fromJson(new String(
                    ApkZipWriter.readAll(zip.getInputStream(manifestEntry)), StandardCharsets.UTF_8));
            Set<String> declared = new HashSet<>(manifest.targets);
            Map<String, byte[]> files = new LinkedHashMap<>();
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || entry.getName().equals("manifest.json") || entry.getName().equals("icon.png")) continue;
                if (!entry.getName().startsWith("payload/")) {
                    throw new IllegalArgumentException("undeclared file outside payload/: " + entry.getName());
                }
                String target = ModManifest.normalizeTarget(entry.getName().substring("payload/".length()));
                if (!declared.contains(target)) throw new IllegalArgumentException("payload target not declared: " + target);
                if (files.put(target, ApkZipWriter.readAll(zip.getInputStream(entry))) != null) {
                    throw new IllegalArgumentException("duplicate payload: " + target);
                }
            }
            for (String target : declared) {
                if (!files.containsKey(target)) throw new IllegalArgumentException("declared target has no payload: " + target);
            }
            return new Payload(manifest, files);
        }
    }

    private static void verifyCoreEntries(File apk) throws Exception {
        try (ZipFile zip = new ZipFile(apk)) {
            String[] required = {
                    "AndroidManifest.xml", "resources.arsc",
                    "lib/arm64-v8a/libunity.so", "lib/armeabi-v7a/libunity.so"
            };
            for (String path : required) {
                if (zip.getEntry(path) == null) throw new IllegalStateException("rebuilt APK lost required entry: " + path);
            }
            if (zip.getEntry("resources.arsc").getMethod() != ZipEntry.STORED) {
                throw new IllegalStateException("resources.arsc must be stored uncompressed");
            }
        }
    }
}
