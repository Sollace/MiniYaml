package com.sollace.yaml;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;

public class TypeCoersion {
    static final JsonPrimitive TRUE = new JsonPrimitive(Boolean.TRUE);
    static final JsonPrimitive FALSE = new JsonPrimitive(Boolean.FALSE);

    static final JsonPrimitive NAN = new JsonPrimitive(Double.NaN);
    static final JsonPrimitive NEGATIVE_INFINITY = new JsonPrimitive(Double.NEGATIVE_INFINITY);
    static final JsonPrimitive POSITIVE_INFINITY = new JsonPrimitive(Double.POSITIVE_INFINITY);

    static final Set<String> UNDEFINED_NUMBERS = Set.of(".Inf", "-.Inf", ".NaN");

    public static JsonElement valueOf(String s) {
        if (isNull(s)) {
            return JsonNull.INSTANCE;
        }

        if (isTrue(s)) {
            return TRUE;
        }

        if (isFalse(s)) {
            return FALSE;
        }

        if (isNumber(s)) {
            int radix = getRadix(s);
            if (radix != 10) {
                s = s.replaceFirst("0[xoXO]", "");
                return new JsonPrimitive(Integer.valueOf(s.toLowerCase(Locale.ROOT), radix));
            }

            if (isInf(s)) {
                return s.charAt(0) == '-' ? NEGATIVE_INFINITY : POSITIVE_INFINITY;
            }
            return isNan(s) ? NAN : new JsonPrimitive(Double.valueOf(s));
        }

        return new JsonPrimitive(s);
    }

    public static int getRadix(String s) {
        if (isOctal(s)) {
            return 8;
        }
        if (isHexadecimal(s)) {
            return 16;
        }

        return 10;
    }

    public static double parseDouble(String s) {
        s = s.toUpperCase(Locale.ROOT).trim();
        if (isInf(s)) {
            return s.charAt(0) == '-' ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        return isNan(s) ? Double.NaN : Double.parseDouble(s);
    }

    public static float parseFloat(String s) {
        s = s.toUpperCase(Locale.ROOT).trim();
        if (isInf(s)) {
            return s.charAt(0) == '-' ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        }
        return isNan(s) ? Float.NaN : Float.parseFloat(s);
    }

    public static boolean isTrue(String value) {
        return Pattern.matches("^(true|yes|on|y)$", value.toLowerCase(Locale.ROOT).trim());
    }

    public static boolean isFalse(String value) {
        return Pattern.matches("^(false|no|off|n)$", value.toLowerCase(Locale.ROOT).trim());
    }

    public static boolean isNumber(String value) {
        return isHexadecimal(value)
                || isOctal(value)
                || Pattern.matches("^[+-]?(\\.(inf|nan)|[0-9]+([_,][0-9][0-9][0-9])*(\\.[0-9]*)?(e\\+[0-9]+)?)$", value.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isDecimal(String value) {
        return Pattern.matches("^[+-]?[0-9]+$", value.trim());
    }

    public static boolean isHexadecimal(String value) {
        return Pattern.matches("^[+-]?0x[0-9abcdef]+$", value.toLowerCase(Locale.ROOT).trim());
    }

    public static boolean isOctal(String value) {
        return Pattern.matches("^[+-]?0o[0-7]+$", value.toLowerCase(Locale.ROOT).trim());
    }

    public static boolean isNull(String value) {
        return Pattern.matches("^(null|~)$", value.toLowerCase(Locale.ROOT).trim());
    }

    public static boolean isNan(String value) {
        return Pattern.matches("^[+-]?.nan$", value.toLowerCase(Locale.ROOT).trim());
    }

    public static boolean isInf(String value) {
        return Pattern.matches("^[+-]?.inf$", value.toLowerCase(Locale.ROOT).trim());
    }
}
