package com.sonicredux.reborn.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class ModManifestTest {
    private static String valid(String targets) {
        return "{\"id\":\"speed-ui\",\"name\":\"Speed UI\",\"version\":\"1.0\",\"author\":\"Tester\",\"description\":\"demo\",\"targets\":" + targets + "}";
    }

    @Test public void acceptsRequiredFieldsAndSafeTargets() throws Exception {
        ModManifest m = ModManifest.fromJson(valid("[\"assets/bin/Data/data.unity3d\"]"));
        assertEquals("speed-ui", m.id);
        assertEquals(1, m.targets.size());
        assertEquals("assets/bin/Data/data.unity3d", m.targets.get(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTraversalTarget() throws Exception {
        ModManifest.fromJson(valid("[\"../AndroidManifest.xml\"]"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDuplicateTarget() throws Exception {
        ModManifest.fromJson(valid("[\"assets/a\",\"assets/a\"]"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingRequiredField() throws Exception {
        ModManifest.fromJson("{\"id\":\"x\",\"targets\":[]}");
    }
}
