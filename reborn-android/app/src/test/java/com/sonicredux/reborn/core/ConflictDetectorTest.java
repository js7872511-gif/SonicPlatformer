package com.sonicredux.reborn.core;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ConflictDetectorTest {
    private ModManifest mod(String id, String name, String target) throws Exception {
        return ModManifest.fromJson("{\"id\":\""+id+"\",\"name\":\""+name+"\",\"version\":\"1\",\"author\":\"a\",\"description\":\"d\",\"targets\":[\""+target+"\"]}");
    }

    @Test public void reportsTwoModsTargetingSameEntry() throws Exception {
        List<ModConflict> conflicts = ConflictDetector.find(Arrays.asList(
                mod("a","Mod A","assets/shared.bin"),
                mod("b","Mod B","assets/shared.bin")));
        assertEquals(1, conflicts.size());
        assertEquals("assets/shared.bin", conflicts.get(0).path);
        assertTrue(conflicts.get(0).modNames.contains("Mod A"));
        assertTrue(conflicts.get(0).modNames.contains("Mod B"));
    }

    @Test public void allowsDistinctTargets() throws Exception {
        assertTrue(ConflictDetector.find(Arrays.asList(
                mod("a","A","assets/a"), mod("b","B","assets/b"))).isEmpty());
    }
}
