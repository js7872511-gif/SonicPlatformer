package com.sonicredux.reborn.runtime;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ReduxBuildProfileTest {
    private static Map<String, ReduxBuildProfile.Fingerprint> supplied() {
        Map<String, ReduxBuildProfile.Fingerprint> m = new LinkedHashMap<>();
        m.put("lib/arm64-v8a/libmain.so", new ReduxBuildProfile.Fingerprint(6728L, "7de2b8bbbdaae62d05592f3ab74f03de6372758dda47554094121298363f5cc8"));
        m.put("lib/arm64-v8a/libil2cpp.so", new ReduxBuildProfile.Fingerprint(58503712L, "ee5ee8c44d14fedf5788dd3d6b2521cb1159ba2c4b19fbd2d82c92ea1001fd38"));
        m.put("lib/armeabi-v7a/libmain.so", new ReduxBuildProfile.Fingerprint(8588L, "10b9a628813d91d830225c3c5a12346af6c598973a222ccdc8c59974dc812161"));
        m.put("lib/armeabi-v7a/libil2cpp.so", new ReduxBuildProfile.Fingerprint(48633732L, "355c0b14f7affaf8798cdf36ffc632363a0a744ead9ce42770cb6ac5112db3e0"));
        m.put("assets/bin/Data/Managed/Metadata/global-metadata.dat", new ReduxBuildProfile.Fingerprint(8295240L, "7518bd2682b1e125a8b10e0e2bddb56485a3353378764ebe43df394a03a779b1"));
        return m;
    }

    @Test public void acceptsSuppliedReduxFingerprint() {
        ReduxBuildProfile.ValidationResult result = ReduxBuildProfile.validateObserved(supplied());
        assertTrue(result.supported);
        assertEquals(ReduxBuildProfile.SUPPLIED_REDUX_V1, result.profileId);
        assertEquals("supported", result.reason);
    }

    @Test public void rejectsWrongMetadataHash() {
        Map<String, ReduxBuildProfile.Fingerprint> m = supplied();
        m.put("assets/bin/Data/Managed/Metadata/global-metadata.dat",
                new ReduxBuildProfile.Fingerprint(8295240L, "0000000000000000000000000000000000000000000000000000000000000000"));
        ReduxBuildProfile.ValidationResult result = ReduxBuildProfile.validateObserved(m);
        assertFalse(result.supported);
        assertTrue(result.reason.contains("assets/bin/Data/Managed/Metadata/global-metadata.dat"));
        assertTrue(result.reason.toLowerCase().contains("sha256"));
    }

    @Test public void rejectsMissingRequiredAbi() {
        Map<String, ReduxBuildProfile.Fingerprint> m = supplied();
        m.remove("lib/armeabi-v7a/libmain.so");
        ReduxBuildProfile.ValidationResult result = ReduxBuildProfile.validateObserved(m);
        assertFalse(result.supported);
        assertTrue(result.reason.contains("lib/armeabi-v7a/libmain.so"));
        assertTrue(result.reason.toLowerCase().contains("missing"));
    }
}
