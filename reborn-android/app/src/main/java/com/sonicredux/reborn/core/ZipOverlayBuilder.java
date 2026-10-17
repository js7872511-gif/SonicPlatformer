package com.sonicredux.reborn.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.zip.CRC32;
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

    private static final class CountingOutputStream extends OutputStream {
        private final OutputStream delegate;
        private long count;
        CountingOutputStream(OutputStream delegate) { this.delegate = delegate; }
        long count() { return count; }
        @Override public void write(int b) throws java.io.IOException { delegate.write(b); count++; }
        @Override public void write(byte[] b, int off, int len) throws java.io.IOException { delegate.write(b, off, len); count += len; }
        @Override public void flush() throws java.io.IOException { delegate.flush(); }
        @Override public void close() throws java.io.IOException { delegate.close(); }
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
             CountingOutputStream counting = new CountingOutputStream(fos);
             ZipOutputStream out = new ZipOutputStream(counting)) {
            Enumeration<? extends ZipEntry> entries = in.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName();
                if (isSignatureEntry(name) || replacements.containsKey(name)) continue;
                if (e.isDirectory()) {
                    ZipEntry dir = new ZipEntry(name);
                    out.putNextEntry(dir); out.closeEntry();
                    continue;
                }
                byte[] bytes = readAll(in.getInputStream(e));
                if (name.equals("AndroidManifest.xml") || name.equals("resources.arsc")) {
                    bytes = BinaryPatcher.replaceSameLength(bytes, oldUtf16, newUtf16, true);
                }
                if (name.equals("resources.arsc")) {
                    bytes = BinaryPatcher.replaceSameLength(bytes, oldLabel, newLabel, false);
                }
                writeEntry(out, counting, name, bytes, e.getMethod());
            }
            for (Map.Entry<String, byte[]> r : replacements.entrySet()) {
                ZipEntry original = in.getEntry(r.getKey());
                int method = original == null ? ZipEntry.DEFLATED : original.getMethod();
                writeEntry(out, counting, r.getKey(), r.getValue(), method);
            }
        }
        verifyCoreEntries(output);
    }

    private static void writeEntry(ZipOutputStream out, CountingOutputStream counting, String name, byte[] bytes, int method) throws Exception {
        ZipEntry e = new ZipEntry(name);
        // Android 11+ rejects target-R+ APKs when resources.arsc is compressed.
        if (name.equals("resources.arsc")) method = ZipEntry.STORED;
        if (method == ZipEntry.STORED) {
            CRC32 crc = new CRC32();
            crc.update(bytes);
            e.setMethod(ZipEntry.STORED);
            e.setSize(bytes.length);
            e.setCompressedSize(bytes.length);
            e.setCrc(crc.getValue());
            int alignment = requiredAlignment(name);
            if (alignment > 1) {
                byte[] extra = alignmentExtra(counting.count(), name, alignment);
                if (extra != null) e.setExtra(extra);
            }
        } else {
            e.setMethod(ZipEntry.DEFLATED);
        }
        out.putNextEntry(e);
        out.write(bytes);
        out.closeEntry();
    }

    private static int requiredAlignment(String name) {
        if (name.equals("resources.arsc")) return 4;
        // If a future Redux build stores native libraries uncompressed, page-align them too.
        if (name.startsWith("lib/") && name.endsWith(".so")) return 16384;
        return 1;
    }

    private static byte[] alignmentExtra(long currentOffset, String name, int alignment) {
        int nameLength = name.getBytes(StandardCharsets.UTF_8).length;
        long baseDataOffset = currentOffset + 30L + nameLength;
        if ((baseDataOffset % alignment) == 0) return null;
        int padData = (int) ((alignment - ((baseDataOffset + 4L) % alignment)) % alignment);
        byte[] extra = new byte[4 + padData];
        // Valid, ignorable private ZIP extra field: header ID 0xCAFE + payload length.
        extra[0] = (byte) 0xFE;
        extra[1] = (byte) 0xCA;
        extra[2] = (byte) (padData & 0xff);
        extra[3] = (byte) ((padData >>> 8) & 0xff);
        return extra;
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
            if (z.getEntry("resources.arsc").getMethod() != ZipEntry.STORED) {
                throw new IllegalStateException("resources.arsc must be stored uncompressed");
            }
        }
    }
}
