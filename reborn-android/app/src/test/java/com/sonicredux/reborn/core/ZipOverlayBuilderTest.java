package com.sonicredux.reborn.core;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

public class ZipOverlayBuilderTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private void writeZip(File f, Map<String, byte[]> entries) throws Exception {
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(f))) {
            for (Map.Entry<String,byte[]> e : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(e.getKey())); out.write(e.getValue()); out.closeEntry();
            }
        }
    }

    private void writeBaseWithStoredResources(File f, Map<String, byte[]> entries) throws Exception {
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(f))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipEntry ze = new ZipEntry(e.getKey());
                if (e.getKey().equals("resources.arsc")) {
                    CRC32 crc = new CRC32(); crc.update(e.getValue());
                    ze.setMethod(ZipEntry.STORED);
                    ze.setSize(e.getValue().length);
                    ze.setCompressedSize(e.getValue().length);
                    ze.setCrc(crc.getValue());
                }
                out.putNextEntry(ze); out.write(e.getValue()); out.closeEntry();
            }
        }
    }

    private long localDataOffset(File zip, String wanted) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(zip, "r")) {
            long p = 0;
            while (p + 30 <= raf.length()) {
                raf.seek(p);
                int sig = Integer.reverseBytes(raf.readInt());
                if (sig != 0x04034b50) break;
                raf.skipBytes(22);
                int nameLen = Short.toUnsignedInt(Short.reverseBytes(raf.readShort()));
                int extraLen = Short.toUnsignedInt(Short.reverseBytes(raf.readShort()));
                byte[] name = new byte[nameLen]; raf.readFully(name);
                String entryName = new String(name, StandardCharsets.UTF_8);
                long dataOffset = p + 30L + nameLen + extraLen;
                if (entryName.equals(wanted)) return dataOffset;
                try (ZipFile z = new ZipFile(zip)) {
                    ZipEntry e = z.getEntry(entryName);
                    if (e == null) throw new IOException("missing entry while scanning: " + entryName);
                    p = dataOffset + e.getCompressedSize();
                    if (e.getMethod() == ZipEntry.DEFLATED) p += 16; // data descriptor used by ZipOutputStream
                }
            }
        }
        throw new IOException("entry not found: " + wanted);
    }

    @Test public void overlaysDeclaredPayloadAndPatchesPackage() throws Exception {
        String oldPkg="com.SpecialTeams.SonicRedux", newPkg="com.RebornXTeams.SonicRedux";
        File base=temp.newFile("base.apk"), mod=temp.newFile("m.sreduxmod"), out=temp.newFile("out.apk");
        Map<String,byte[]> baseEntries=new LinkedHashMap<>();
        baseEntries.put("AndroidManifest.xml", ("pre"+oldPkg+"post").getBytes(StandardCharsets.UTF_16LE));
        ByteArrayOutputStream res = new ByteArrayOutputStream();
        res.write(("r"+oldPkg+"r").getBytes(StandardCharsets.UTF_16LE));
        res.write("Sonic Redux".getBytes(StandardCharsets.UTF_8));
        baseEntries.put("resources.arsc", res.toByteArray());
        baseEntries.put("assets/demo.txt", "old".getBytes(StandardCharsets.UTF_8));
        baseEntries.put("lib/arm64-v8a/libunity.so", new byte[]{1});
        baseEntries.put("lib/armeabi-v7a/libunity.so", new byte[]{2});
        baseEntries.put("META-INF/CERT.SF", new byte[]{3});
        writeZip(base,baseEntries);
        String manifest="{\"id\":\"m\",\"name\":\"M\",\"version\":\"1\",\"author\":\"a\",\"description\":\"d\",\"targets\":[\"assets/demo.txt\"]}";
        Map<String,byte[]> modEntries=new LinkedHashMap<>();
        modEntries.put("manifest.json",manifest.getBytes(StandardCharsets.UTF_8));
        modEntries.put("payload/assets/demo.txt","new".getBytes(StandardCharsets.UTF_8));
        writeZip(mod,modEntries);

        ZipOverlayBuilder.build(base, Collections.singletonList(mod), out, oldPkg, newPkg);
        try(ZipFile z=new ZipFile(out)) {
            assertEquals("new",new String(z.getInputStream(z.getEntry("assets/demo.txt")).readAllBytes(),StandardCharsets.UTF_8));
            assertNull(z.getEntry("META-INF/CERT.SF"));
            String axml=new String(z.getInputStream(z.getEntry("AndroidManifest.xml")).readAllBytes(),StandardCharsets.UTF_16LE);
            assertTrue(axml.contains(newPkg)); assertFalse(axml.contains(oldPkg));
            byte[] patchedRes=z.getInputStream(z.getEntry("resources.arsc")).readAllBytes();
            assertTrue(new String(patchedRes, StandardCharsets.ISO_8859_1).contains("SonicReborn"));
            assertNotNull(z.getEntry("lib/arm64-v8a/libunity.so"));
            assertNotNull(z.getEntry("lib/armeabi-v7a/libunity.so"));
        }
    }

    @Test public void resourcesArscStaysStoredAndFourByteAligned() throws Exception {
        String oldPkg="com.SpecialTeams.SonicRedux", newPkg="com.RebornXTeams.SonicRedux";
        File base=temp.newFile("aligned-base.apk"), out=temp.newFile("aligned-out.apk");
        Map<String,byte[]> baseEntries=new LinkedHashMap<>();
        baseEntries.put("AndroidManifest.xml", ("pre"+oldPkg+"post").getBytes(StandardCharsets.UTF_16LE));
        ByteArrayOutputStream res = new ByteArrayOutputStream();
        res.write(("r"+oldPkg+"r").getBytes(StandardCharsets.UTF_16LE));
        res.write("Sonic Redux".getBytes(StandardCharsets.UTF_8));
        baseEntries.put("resources.arsc", res.toByteArray());
        baseEntries.put("lib/arm64-v8a/libunity.so", new byte[]{1});
        baseEntries.put("lib/armeabi-v7a/libunity.so", new byte[]{2});
        writeBaseWithStoredResources(base, baseEntries);

        ZipOverlayBuilder.build(base, Collections.emptyList(), out, oldPkg, newPkg);
        try (ZipFile z = new ZipFile(out)) {
            assertEquals("resources.arsc must remain uncompressed", ZipEntry.STORED, z.getEntry("resources.arsc").getMethod());
        }
        assertEquals("resources.arsc data must be 4-byte aligned", 0, localDataOffset(out, "resources.arsc") % 4);
    }
}
