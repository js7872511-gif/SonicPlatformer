package com.sonicredux.reborn.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ApkZipWriter {
    private ApkZipWriter() {}

    public static final class CountingOutputStream extends OutputStream {
        private final OutputStream delegate;
        private long count;

        public CountingOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        public long count() {
            return count;
        }

        @Override public void write(int b) throws IOException {
            delegate.write(b);
            count++;
        }

        @Override public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            count += len;
        }

        @Override public void flush() throws IOException {
            delegate.flush();
        }

        @Override public void close() throws IOException {
            delegate.close();
        }
    }

    public static void writeEntry(
            ZipOutputStream out,
            CountingOutputStream counting,
            String name,
            byte[] bytes,
            int method) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        if (name.equals("resources.arsc")) method = ZipEntry.STORED;
        if (method == ZipEntry.STORED) {
            CRC32 crc = new CRC32();
            crc.update(bytes);
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(bytes.length);
            entry.setCompressedSize(bytes.length);
            entry.setCrc(crc.getValue());
            int alignment = requiredAlignment(name);
            if (alignment > 1) {
                byte[] extra = alignmentExtra(counting.count(), name, alignment);
                if (extra != null) entry.setExtra(extra);
            }
        } else {
            entry.setMethod(ZipEntry.DEFLATED);
        }
        out.putNextEntry(entry);
        out.write(bytes);
        out.closeEntry();
    }

    public static boolean isSignatureEntry(String name) {
        String upper = name.toUpperCase();
        return upper.startsWith("META-INF/") &&
                (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") ||
                        upper.endsWith(".EC") || upper.equals("META-INF/MANIFEST.MF"));
    }

    public static byte[] readAll(InputStream in) throws Exception {
        try (InputStream stream = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = stream.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }

    private static int requiredAlignment(String name) {
        if (name.equals("resources.arsc")) return 4;
        if (name.startsWith("lib/") && name.endsWith(".so")) return 16384;
        return 1;
    }

    private static byte[] alignmentExtra(long currentOffset, String name, int alignment) {
        int nameLength = name.getBytes(StandardCharsets.UTF_8).length;
        long baseDataOffset = currentOffset + 30L + nameLength;
        if ((baseDataOffset % alignment) == 0) return null;
        int padData = (int) ((alignment - ((baseDataOffset + 4L) % alignment)) % alignment);
        byte[] extra = new byte[4 + padData];
        extra[0] = (byte) 0xFE;
        extra[1] = (byte) 0xCA;
        extra[2] = (byte) (padData & 0xff);
        extra[3] = (byte) ((padData >>> 8) & 0xff);
        return extra;
    }
}
