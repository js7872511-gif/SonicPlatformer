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
}
