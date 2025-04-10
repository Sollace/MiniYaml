package com.sollace.yaml;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Stack;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;


import com.sollace.yaml.YamlTokenizer.Token;
import static com.sollace.yaml.YamlTokenizer.Token.Type.*;

public class YamlReader implements Closeable {

    private final YamlTokenizer in;

    private final Stack<Token> indentation = new Stack<>();

    public YamlReader(Reader in) {
        this.in = new YamlTokenizer(in);
        indentation.push(Token.EMPTY);
    }

    public JsonObject readDocument() throws IOException {
        Token indent = in.readToken();
        if (indent.type() == WHITESPACE) {
            indentation.push(indent);
        } else {
            in.pushBack(indent);
            indentation.push(Token.EMPTY);
        }

        JsonObject json = readObject(true);

        indentation.pop();
        return json;
    }

    public JsonObject readObject(boolean root) throws IOException {
        JsonObject json = new JsonObject();

        do {
            Token token = in.readToken();
            if (token.type() == END) {
                break;
            }
            in.pushBack(token);
            String propertyName = readKey();
            in.readToken().require(SEPARATOR).require(Constants.KEY_VALUE_PAIR_SEPARATOR);
            json.add(propertyName, readValue());
            token = in.readToken();
            if (token.is(END)) {
                break;
            }
            if (token.is(NEWLINE)) {
                token = in.readToken();
            }

            if (token.is(TEXT)) {
                in.pushBack(token);
                if (!root && !indentation.peek().value().isEmpty()) {
                    break;
                }
                continue;
            }

            if (!token.is(WHITESPACE)) {
                in.pushBack(token);
                break;
            }

            if (!root && indentation.peek().value().isEmpty()) {
                indentation.pop();
                indentation.push(token);
            } else {
                if (!indentation.peek().value().equalsIgnoreCase(token.value())) {
                    in.pushBack(token);
                    break;
                }
            }
        } while (true);

        return json;
    }

    public JsonElement readValue() throws IOException {
        do {
            Token token = in.readToken();
            Token next;

            switch (token.type()) {
                case END: throw new IOException("Premature end of document");
                case SEPARATOR: throw new IOException("Expected value");
                case QUOTE: return new JsonPrimitive(readQuotedString(token.value()));
                case MODE_CHANGE:
                    if (token.value().equalsIgnoreCase(Constants.ARRAY_START)) {
                        throw new IOException("Inline sequences are not supported");
                    }
                    if (token.value().equalsIgnoreCase(Constants.MAP_START)) {
                        throw new IOException("Inline maps are not supported");
                    }
                    in.pushBack(token);
                    return TypeCoersion.valueOf(readString());
                case TEXT:
                    next = in.readToken();
                    // check for dangling object pairs
                    //  - one
                    //  - two
                    //  - name: mama
                    //    age: 24
                    if (next.is(SEPARATOR) && next.value().equalsIgnoreCase(Constants.KEY_VALUE_PAIR_SEPARATOR)) {
                        in.pushBack(next);
                        in.pushBack(token);
                        indentation.push(Token.EMPTY);
                        JsonObject json = readObject(false);
                        indentation.pop();
                        return json;
                    }
                    in.pushBack(next);
                    in.pushBack(token);
                    return TypeCoersion.valueOf(readString());
                case CONTROL_CHARACTER:
                    if (token.value().equalsIgnoreCase(Constants.KEY_INDICATOR)) {
                        in.pushBack(token);
                        indentation.push(Token.EMPTY);
                        JsonObject json = readObject(false);
                        indentation.pop();
                        return json;
                    }
                    if (token.value().equalsIgnoreCase(Constants.MULTI_LINE_NEWLINE_PRESERVING_STRING)) {
                        return new JsonPrimitive(readMultiLineString(true, true));
                    }
                    if (token.value().equalsIgnoreCase(Constants.MULTI_LINE_STRING)) {
                        return new JsonPrimitive(readMultiLineString(false, true));
                    }
                    if (token.value().equalsIgnoreCase(Constants.MULTI_LINE_NEWLINE_PRESERVING_STRING_NO_NEWLINE)) {
                        return new JsonPrimitive(readMultiLineString(true, false));
                    }
                    if (token.value().equalsIgnoreCase(Constants.MULTI_LINE_STRING_NO_NEWLINE)) {
                        return new JsonPrimitive(readMultiLineString(false, false));
                    }
                    if (token.value().equalsIgnoreCase(Constants.TYPE_COERSION_INDICATOR)) {
                        token = in.readToken().require(TEXT);

                        @Nullable
                        YamlObjectType type = YamlObjectType.of(token.value());

                        if (type == null) {
                            throw new IOException("Type unsupported: " + token.value());
                        }

                        if (type.isBlockScoped()) {
                            token = in.readToken();
                            if (token.is(WHITESPACE)) {
                                token = in.readToken();
                            }
                            token.require(NEWLINE);
                            indentation.push(in.readToken().require(WHITESPACE));
                            try {
                                return switch (type) {
                                    case MAP -> readObject(false);
                                    case SEQUENCE -> readArray();
                                    case SET -> readSet();
                                    default -> throw new IOException("Type unsupported: " + token.value());
                                };
                            } finally {
                                indentation.pop();
                            }
                        }

                        do {
                            next = in.readToken();
                            if (!next.is(WHITESPACE) && !next.is(NEWLINE)) {
                                in.pushBack(next);
                                break;
                            }
                        } while (true);
                        return switch (type) {
                            case STRING -> new JsonPrimitive(readString());
                            case INT -> new JsonPrimitive(readInt());
                            case DOUBLE -> new JsonPrimitive(readDouble());
                            case FLOAT -> new JsonPrimitive(readFloat());
                            case LONG -> new JsonPrimitive(readLong());
                            case SHORT -> new JsonPrimitive(readShort());
                            case BYTE -> new JsonPrimitive(readByte());
                            case BOOL -> new JsonPrimitive(readBoolean());
                            default -> throw new IOException("Type unsupported: " + token.value());
                        };
                    }

                    break;
                case NEWLINE:
                    try {
                        Token indent = in.readToken().require(WHITESPACE);
                        indentation.push(indent);
                        token = in.readToken();
                        if (token.is(SEPARATOR) && token.value().equalsIgnoreCase(Constants.ARRAY_ELEMENT_PREFIX)) {
                            in.pushBack(token);
                            return readArray();
                        }
                        if (token.is(CONTROL_CHARACTER) && token.value().equalsIgnoreCase(Constants.KEY_INDICATOR)) {
                            in.pushBack(token);
                            return readObject(false);
                        }
                        if (token.is(TEXT)) {
                            next = in.readToken();
                            if (next.is(SEPARATOR) && next.value().equalsIgnoreCase(Constants.KEY_VALUE_PAIR_SEPARATOR)) {
                                in.pushBack(next);
                                in.pushBack(token);
                                return readObject(false);
                            }
                        }
                        return new JsonPrimitive("");
                    } finally {
                        indentation.pop();
                    }
                case WHITESPACE:
                    continue;
            }
        } while (true);
    }

    public JsonArray readArray() throws IOException {
        return readSequence(true);
    }

    public JsonArray readSet() throws IOException {
        return readSequence(false);
    }

    private JsonArray readSequence(boolean allowDuplicates) throws IOException {
        JsonArray array = new JsonArray();
        String elementPrefix = allowDuplicates ? Constants.ARRAY_ELEMENT_PREFIX : Constants.SET_ELEMENT_PREFIX;
        Set<JsonElement> values = allowDuplicates ? null : new HashSet<>();
        do {
            Token token = in.readToken().require(SEPARATOR).require(elementPrefix);
            JsonElement value = readValue();
            if (values == null || values.add(value)) {
                array.add(value);
            }
            token = in.readToken();
            if (token.is(TEXT)) {
                in.pushBack(token);
                return array;
            }

            if (token.is(WHITESPACE) && in.skipToken(NEWLINE).is(NEWLINE)) {
                token = in.readToken();
            }

            if (token.is(NEWLINE)) {
                token = in.readToken();
            }

            if (token.is(END)) {
                return array;
            }

            if (token.is(WHITESPACE) && !indentation.peek().value().equalsIgnoreCase(token.value())) {
                in.pushBack(token);
                return array;
            }
        } while (true);
    }

    public float readFloat() throws IOException {
        return TypeCoersion.parseFloat(in.readToken().require(TEXT).value());
    }

    public double readDouble() throws IOException {
        return TypeCoersion.parseDouble(in.readToken().require(TEXT).value());
    }

    public int readInt() throws IOException {
        String value = in.readToken().require(TEXT).value().trim().toUpperCase(Locale.ROOT);
        return Integer.parseInt(value, TypeCoersion.getRadix(value));
    }

    public long readLong() throws IOException {
        String value = in.readToken().require(TEXT).value().trim().toUpperCase(Locale.ROOT);
        return Long.parseLong(value, TypeCoersion.getRadix(value));
    }

    public short readShort() throws IOException {
        String value = in.readToken().require(TEXT).value().trim().toUpperCase(Locale.ROOT);
        return Short.parseShort(value, TypeCoersion.getRadix(value));
    }

    public byte readByte() throws IOException {
        String value = in.readToken().require(TEXT).value().trim().toUpperCase(Locale.ROOT);
        return Byte.parseByte(value, TypeCoersion.getRadix(value));
    }

    public boolean readBoolean() throws IOException {
        String value = in.readToken().require(TEXT).value();
        if (TypeCoersion.isTrue(value)) {
            return true;
        }
        if (TypeCoersion.isFalse(value)) {
            return false;
        }
        throw new IOException(value + " cannot be converted to a boolean");
    }

    public String readKey() throws IOException {
        do {
            Token token = in.readToken();

            switch (token.type()) {
                case END: throw new IOException("Premature end of document");
                case SEPARATOR: throw new IOException("Unexpected symbol: " + token);
                case CONTROL_CHARACTER:
                    token.require(Constants.KEY_INDICATOR);
                    return readUnquotedString(false, true);
                case QUOTE: return readQuotedString(token.value());
                case MODE_CHANGE:
                    if (token.value().equalsIgnoreCase(Constants.ARRAY_START) || token.value().equalsIgnoreCase(Constants.MAP_START)) {
                        throw new IOException("Complex keys are not supported");
                    }
                case TEXT:
                    in.pushBack(token);
                    return readUnquotedString(true, true);
                case NEWLINE:
                case WHITESPACE:
                    continue;
            }
        } while (true);
    }

    public String readString() throws IOException {
        Token token = in.skipToken(QUOTE);
        return token.is(QUOTE) ? readQuotedString(token.value()) : readUnquotedString(true, false);
    }

    public String readQuotedString(String quoteChars) throws IOException {
        StringBuffer buffer = new StringBuffer();
        do {
            Token token = in.nextToken();

            if (token.is(END)) {
                throw new IOException("Unterminated string \"" + buffer.toString() + "\"");
            }

            if (token.is(WHITESPACE)) {
                Token next = in.nextToken();
                if (next.is(NEWLINE)) {
                    token = next;
                } else {
                    in.pushBack(next);
                }
            }

            if (token.is(NEWLINE)) {
                Token next = in.nextToken();
                if (next.is(NEWLINE)) {
                    buffer.append(token.value());
                    in.skipToken(WHITESPACE);
                } else {
                    if (!next.is(WHITESPACE)) {
                        in.pushBack(next);
                    }
                    buffer.append(" ");
                }

                continue;
            }

            if (quoteChars.equalsIgnoreCase(Constants.DOUBLE_QUOTE)) {
                if (token.is(CONTROL_CHARACTER) && token.value().equalsIgnoreCase("\\")) {
                    Token next = in.nextToken();
                    if (next.is(NEWLINE)) {
                        buffer.append(next.value());
                    } else {
                        var codepoint = EscapeSequences.getCodepoint(next.value());
                        buffer.append(codepoint.character());
                        if (codepoint.remainder() != null) {
                            buffer.append(codepoint.remainder());
                        }
                    }
                    continue;
                }
            } else if (token.is(QUOTE) && token.value().equalsIgnoreCase(quoteChars)) {
                Token next = in.nextToken();
                if (next.is(QUOTE) && next.value().equalsIgnoreCase(quoteChars)) {
                    buffer.append(next.value());
                    continue;
                }

                in.pushBack(next);
            }

            if (token.is(QUOTE) && token.value().equalsIgnoreCase(quoteChars)) {
                break;
            }
            buffer.append(token.value());
        } while (true);

        return buffer.toString().trim();
    }

    public String readUnquotedString(boolean stopOnModeChange, boolean stopOnDelimiter) throws IOException {
        StringBuffer buffer = new StringBuffer();

        outer: do {
            Token token = in.nextToken();

            if (!stopOnDelimiter && token.is(NEWLINE)) {
                List<Token> forward = new ArrayList<>();
                try {
                    do {
                        Token upcoming = in.nextToken();
                        forward.add(0, upcoming);
                        if (upcoming.is(SEPARATOR)) {
                            break outer;
                        } else if (upcoming.is(NEWLINE) || upcoming.is(END)) {
                            break;
                        }
                    } while (true);
                } finally {
                    while (!forward.isEmpty()) {
                        in.pushBack(forward.remove(0));
                    }
                }
            }

            if (token.is(END)
                    || token.isCommentBegin()
                    || (stopOnDelimiter && token.is(NEWLINE))
                    || (stopOnModeChange && token.is(MODE_CHANGE) && token.value().equalsIgnoreCase(Constants.MAP_END) && token.value().equalsIgnoreCase(Constants.ARRAY_END))
                    || (stopOnDelimiter && token.is(SEPARATOR))) {
                in.pushBack(token);
                break;
            } else {
                buffer.append(token.value());
            }
        } while (true);

        return buffer.toString().trim();
    }

    public String readMultiLineString(boolean keepNewlines, boolean appendNewline) throws IOException {
        StringBuffer buffer = new StringBuffer();
        Token token = in.nextToken();
        String baseIndent;
        if (token.is(TEXT) && TypeCoersion.isDecimal(token.value())) {
            char[] indentText = new char[Math.max(1, Math.min(9, Integer.parseInt(token.value())))];
            Arrays.fill(indentText, ' ');
            baseIndent = new String(indentText);
        } else {
            in.pushBack(token);
            token = in.readToken();
            if (token.is(NEWLINE)) {
                token = in.readToken();
            }
            baseIndent = token.require(WHITESPACE).value();
        }

        boolean escapeNext = false;
        boolean isOnLineStart = false;
        do {
            token = in.nextToken();
            if (isOnLineStart) {
                if (token.is(WHITESPACE)) {
                    isOnLineStart = false;
                    buffer.append(token.value().replaceFirst(baseIndent, ""));
                    continue;
                } else {
                    in.pushBack(token);
                    break;
                }
            }

            if (!escapeNext) {
                if (token.is(CONTROL_CHARACTER) && token.value().equalsIgnoreCase("\\")) {
                    escapeNext = true;
                    continue;
                }
                if (token.is(NEWLINE)) {
                    isOnLineStart = true;
                    if (!in.peekToken().is(WHITESPACE)) {
                        in.pushBack(token);
                        break;
                    }
                    buffer.append(keepNewlines ? token.value() : " ");
                    continue;
                }
            }

            buffer.append(token.value());
        } while (true);

        if (appendNewline) {
            buffer.append('\n');
        }
        return buffer.toString().trim();
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
