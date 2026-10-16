package com.sonicredux.reborn.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConflictDetector {
    private ConflictDetector() {}

    public static List<ModConflict> find(List<ModManifest> mods) {
        Map<String, List<String>> owners = new LinkedHashMap<>();
        for (ModManifest mod : mods) {
            for (String target : mod.targets) {
                owners.computeIfAbsent(target, k -> new ArrayList<>()).add(mod.name);
            }
        }
        List<ModConflict> conflicts = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : owners.entrySet()) {
            if (e.getValue().size() > 1) conflicts.add(new ModConflict(e.getKey(), e.getValue()));
        }
        return conflicts;
    }
}
