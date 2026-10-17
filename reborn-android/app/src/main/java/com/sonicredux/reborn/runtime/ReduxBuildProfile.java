package com.sonicredux.reborn.runtime;

import java.io.File;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ReduxBuildProfile {
    public static final String SUPPLIED_REDUX_V1 = "supplied-redux-v1";

    static final class Fingerprint {
        final long size;
        final String sha256;

        Fingerprint(long size, String sha256) {
            this.size = size;
            this.sha256 = sha256;
        }
    }

    public static final class ValidationResult {
        public final boolean supported;
        public final String profileId;
        public final String reason;

        ValidationResult(boolean supported, String profileId, String reason) {
            this.supported = supported;
            this.profileId = profileId;
            this.reason = reason;
        }
    }

    private static final Map<String, Fingerprint> EXPECTED;
    static {
        Map<String, Fingerprint> m = new LinkedHashMap<>();
        m.put("lib/arm64-v8a/libmain.so", new Fingerprint(6728L, "7de2b8bbbdaae62d05592f3ab74f03de6372758dda47554094121298363f5cc8"));
        m.put("lib/arm64-v8a/libil2cpp.so", new Fingerprint(58503712L, "ee5ee8c44d14fedf5788dd3d6b2521cb1159ba2c4b19fbd2d82c92ea1001fd38"));
        m.put("lib/armeabi-v7a/libmain.so", new Fingerprint(8588L, "10b9a628813d91d830225c3c5a12346af6c598973a222ccdc8c59974dc812161"));
        m.put("lib/armeabi-v7a/libil2cpp.so", new Fingerprint(48633732L, "355c0b14f7affaf8798cdf36ffc632363a0a744ead9ce42770cb6ac5112db3e0"));
        m.put("assets/bin/Data/Managed/Metadata/global-metadata.dat", new Fingerprint(8295240L, "7518bd2682b1e125a8b10e0e2bddb56485a3353378764ebe43df394a03a779b1"));
        EXPECTED = Collections.unmodifiableMap(m);
    }

    private ReduxBuildProfile() {}

    public static ValidationResult validate(File apk) {
        if (apk == null || !apk.isFile()) {
            return rejected("APK file is missing");
        }
        try (ZipFile zip = new ZipFile(apk)) {
            Map<String, Fingerprint> observed = new LinkedHashMap<>();
            for (String path : EXPECTED.keySet()) {
                ZipEntry entry = zip.getEntry(path);
                if (entry == null || entry.isDirectory()) {
                    return rejected("missing " + path);
                }
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long count = 0;
                try (InputStream in = zip.getInputStream(entry)) {
                    byte[] buffer = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        digest.update(buffer, 0, n);
                        count += n;
                    }
                }
                observed.put(path, new Fingerprint(count, hex(digest.digest())));
            }
            return validateObserved(observed);
        } catch (Exception e) {
            String message = e.getMessage();
            return rejected("could not inspect APK" + (message == null ? "" : ": " + message));
        }
    }

    static ValidationResult validateObserved(Map<String, Fingerprint> observed) {
        for (Map.Entry<String, Fingerprint> expectedEntry : EXPECTED.entrySet()) {
            String path = expectedEntry.getKey();
            Fingerprint expected = expectedEntry.getValue();
            Fingerprint actual = observed == null ? null : observed.get(path);
            if (actual == null) {
                return rejected("missing " + path);
            }
            if (actual.size != expected.size) {
                return rejected(path + " size mismatch: expected " + expected.size + " bytes, got " + actual.size);
            }
            if (!expected.sha256.equalsIgnoreCase(actual.sha256)) {
                return rejected(path + " sha256 mismatch");
            }
        }
        return new ValidationResult(true, SUPPLIED_REDUX_V1, "supported");
    }

    private static ValidationResult rejected(String reason) {
        return new ValidationResult(false, null, reason);
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format("%02x", b & 0xff));
        return out.toString();
    }
}
