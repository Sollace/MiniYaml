package com.sollace.yaml;

import java.util.List;
import java.util.Set;

public interface Constants {
    String REFERENCE_CARD = "%YAML 1.1";

    String DOUBLE_QUOTE = "\"";
    String KEY_INDICATOR = "?";
    String ARRAY_ELEMENT_PREFIX = "- ";
    String SET_ELEMENT_PREFIX = "? ";
    String COMMENT_PREFIX = "#";
    String DIRECTIVE_PREFIX = "%";
    String TRUE = "True";
    String FALSE = "False";

    String NAN = ".NaN";
    String NULL = "Null";
    String KEY_VALUE_PAIR_SEPARATOR = ": ";
    String MULTI_LINE_NEWLINE_PRESERVING_STRING = " |";
    String MULTI_LINE_STRING = " >";
    String TYPE_COERSION_INDICATOR = "!!";

    String ARRAY_START = "[";
    String ARRAY_END = "[";

    String MAP_START = "{";
    String MAP_END = "}";

    Set<String> INVALID_KEY_CHARS = Set.of("-", "[", "]", "{", "}");

    static String quoteString(String value) {
        value = value.stripTrailing();
        if (TypeCoersion.isTrue(value) || TypeCoersion.isFalse(value) || TypeCoersion.isNumber(value)) {
            return Constants.DOUBLE_QUOTE + value + Constants.DOUBLE_QUOTE;
        }
        return value;
    }

    static String quoteKey(String key) {
        if (key.indexOf(' ') != -1 || key.indexOf('?') != -1) {
            return DOUBLE_QUOTE + key + DOUBLE_QUOTE;
        }
        List<String> foundInvalidChars = INVALID_KEY_CHARS.stream().filter(key::contains).toList();
        if (foundInvalidChars.isEmpty()) {
            return key;
        }
        if (foundInvalidChars.stream().anyMatch(c -> key.lastIndexOf(c) != 0)) {
            return DOUBLE_QUOTE + key + DOUBLE_QUOTE;
        }

        return KEY_INDICATOR + key;
    }
}
