package com.sollace.yaml;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;

public class TypeCoersion {
    static final JsonPrimitive TRUE = new JsonPrimitive(Boolean.TRUE);
    static final JsonPrimitive FALSE = new JsonPrimitive(Boolean.FALSE);

    static final JsonPrimitive NAN = new JsonPrimitive(Double.NaN);
    static final JsonPrimitive NEGATIVE_INFINITY = new JsonPrimitive(Double.NEGATIVE_INFINITY);
    static final JsonPrimitive POSITIVE_INFINITY = new JsonPrimitive(Double.POSITIVE_INFINITY);

    @Nullable
    public static JsonElement valueOf(String s) {
        if (Constants.isNull(s)) {
            return JsonNull.INSTANCE;
        }

        if (Constants.isTrue(s)) {
            return TRUE;
        }
        if (Constants.isFalse(s)) {
            return FALSE;
        }
        if (Constants.isNumber(s)) {
            if (s.equalsIgnoreCase(Constants.NAN)) {
                return NAN;
            }
            if (Constants.isInf(s)) {
                return s.charAt(0) == '-' ? NEGATIVE_INFINITY : POSITIVE_INFINITY;
            }
            return new JsonPrimitive(Double.parseDouble(s));
        }

        return new JsonPrimitive(s);
    }

}
