package com.sonicredux.reborn.core;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ModManifest {
    public final String id;
    public final String name;
    public final String version;
    public final String author;
    public final String description;
    public final List<String> targets;

    private ModManifest(String id, String name, String version, String author, String description, List<String> targets) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.author = author;
        this.description = description;
        this.targets = Collections.unmodifiableList(new ArrayList<>(targets));
    }

    public static ModManifest fromJson(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        String id = required(o, "id");
        String name = required(o, "name");
        String version = required(o, "version");
        String author = required(o, "author");
        String description = required(o, "description");
        if (!o.has("targets") || !(o.get("targets") instanceof JSONArray)) {
            throw new IllegalArgumentException("targets must be an array");
        }
        JSONArray a = o.getJSONArray("targets");
        if (a.length() == 0) throw new IllegalArgumentException("targets must not be empty");
        List<String> targets = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < a.length(); i++) {
            String t = normalizeTarget(a.getString(i));
            if (!seen.add(t)) throw new IllegalArgumentException("duplicate target: " + t);
            targets.add(t);
        }
        return new ModManifest(id, name, version, author, description, targets);
    }

    private static String required(JSONObject o, String key) {
        String value = o.optString(key, "").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("missing required field: " + key);
        return value;
    }

    public static String normalizeTarget(String raw) {
        if (raw == null) throw new IllegalArgumentException("null target");
        String p = raw.trim();
        if (p.isEmpty() || p.startsWith("/") || p.startsWith("\\") || p.contains("\\") || p.contains(":")) {
            throw new IllegalArgumentException("unsafe target: " + raw);
        }
        String[] parts = p.split("/");
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException("unsafe target: " + raw);
            }
        }
        String upper = p.toUpperCase();
        if (upper.startsWith("META-INF/") || p.equals("AndroidManifest.xml") || p.equals("resources.arsc")) {
            throw new IllegalArgumentException("protected target: " + p);
        }
        return p;
    }
}
