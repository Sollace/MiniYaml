package com.sollace.yaml;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.jetbrains.annotations.Nullable;

import com.sollace.yaml.util.ByteList;

public class EscapeSequences {
    public record Codepoint(char character, @Nullable String remainder) {
        static final Codepoint NEWLINE = new Codepoint('\n', null);
        static final Codepoint TAB = new Codepoint('\t', null);
        static final Codepoint BREAK = new Codepoint('\b', null);
        static final Codepoint LINE_FEED = new Codepoint('\f', null);
        static final Codepoint NULL = new Codepoint('\0', null);
    }

    public static Codepoint getCodepoint(String s) {
        return switch (s.length()) {
            case 0 -> throw new IllegalArgumentException("Empty escape sequence");
            case 1 -> switch(s.charAt(0)) {
                case 'r', 'n' -> Codepoint.NEWLINE;
                case 't' -> Codepoint.TAB;
                case 'b' -> Codepoint.BREAK;
                case 'f' -> Codepoint.LINE_FEED;
                case '0' -> Codepoint.NULL;
                case '1', '2', '3', '4', '5', '6', '7', '8', '9' -> new Codepoint((char)Character.digit(s.charAt(0), 10), null);
                default -> new Codepoint(s.charAt(0), null);
            };
            default -> switch(s.charAt(0)) {
                case 'u' -> parseUnicodeSequence(s);
                default -> new Codepoint(s.charAt(0), null);
            };
        };
    }

    private static Codepoint parseUnicodeSequence(String s) {
        ByteList bytes = new ByteList(8);
        StringBuffer currentByte = new StringBuffer(4);
        int i = 0;
        for (; i < s.length(); i++) {
            char c = Character.toUpperCase(s.charAt(i));
            if (c == 'u') {
                for (int j = 0; j < 4 && i < s.length(); j++, i++) {
                    if (Character.isDigit(c) || (c == 'A' || c == 'B' || c == 'C' || c == 'D' || c == 'E' || c == 'F')) {
                        currentByte.append(c);
                    } else {
                        break;
                    }
                }

                bytes.add(Byte.parseByte(currentByte.toString(), 16));
                currentByte.setLength(0);
            }
        }

        if (!currentByte.isEmpty() || bytes.length() == 0) {
            throw new NumberFormatException("Invalid unicode sequence " + s);
        }

        return new Codepoint(StandardCharsets.UTF_16.decode(ByteBuffer.wrap(bytes.toArray())).get(), s.substring(i));
    }
}
