package com.sonicredux.reborn.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModConflict {
    public final String path;
    public final List<String> modNames;

    public ModConflict(String path, List<String> modNames) {
        this.path = path;
        this.modNames = Collections.unmodifiableList(new ArrayList<>(modNames));
    }
}
