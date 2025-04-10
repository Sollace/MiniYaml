package com.sollace.yaml;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

public enum YamlObjectType {
    MAP("map", "obj", "omap"),
    SEQUENCE("array", "arr", "seq", "pairs"),
    SET("set"),
    STRING("str", "string"),
    INT("int", "integer"),
    DOUBLE("double"),
    FLOAT("float"),
    LONG("long"),
    SHORT("short"),
    BYTE("byte"),
    BOOL("bool", "boolean");

    private static final Map<String, YamlObjectType> VALUES = Arrays.stream(values())
            .flatMap(type -> type.names.stream().map(name -> Map.entry(name, type)))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    @Nullable
    public static YamlObjectType of(String name) {
        return VALUES.get(name);
    }

    private final Set<String> names;

    YamlObjectType(String...names) {
        this.names = Set.of(names);
    }

    public boolean isBlockScoped() {
        return this == MAP || this == SEQUENCE || this == SET;
    }
}
