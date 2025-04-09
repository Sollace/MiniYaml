package com.sollace.yaml;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.Stack;

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

                if (token.is(TEXT)) {
                    in.pushBack(token);
                    continue;
                }
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
                        throw new IOException("Inline values are not supported");
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
                        return new JsonPrimitive(readMultiLineString(true));
                    }
                    if (token.value().equalsIgnoreCase(Constants.MULTI_LINE_STRING)) {
                        return new JsonPrimitive(readMultiLineString(false));
                    }
                    if (token.value().equalsIgnoreCase(Constants.TYPE_COERSION_INDICATOR)) {
                        token = in.readToken().require(TEXT);
                        do {
                            next = in.readToken();
                            if (!next.is(WHITESPACE) && !next.is(NEWLINE)) {
                                in.pushBack(next);
                                break;
                            }
                        } while (true);
                        return switch (token.value()) {
                            case "str", "string" -> new JsonPrimitive(readString());
                            case "int", "integer" -> new JsonPrimitive(readInt());
                            case "double" -> new JsonPrimitive(readDouble());
                            case "float" -> new JsonPrimitive(readFloat());
                            case "long" -> new JsonPrimitive(readLong());
                            case "short" -> new JsonPrimitive(readShort());
                            case "byte" -> new JsonPrimitive(readByte());
                            case "bool", "boolean" -> new JsonPrimitive(readBoolean());
                            case "array", "arr", "seq", "pairs" -> readArray();
                            case "set" -> readSet();
                            case "map", "obj", "object", "omap" -> readObject(false);
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

    public JsonArray readSequence(boolean allowDuplicates) throws IOException {
        JsonArray array = new JsonArray();
        String elementPrefix = allowDuplicates ? Constants.ARRAY_ELEMENT_PREFIX : Constants.SET_ELEMENT_PREFIX;
        Set<JsonElement> values = allowDuplicates ? new HashSet<>() : null;
        do {
            Token token = in.readToken();
            token.require(SEPARATOR).require(elementPrefix);
            JsonElement value = readValue();
            if (values == null || values.add(value)) {
                array.add(value);
            }
            token = in.readToken();
            if (token.is(WHITESPACE)) {
                token = in.readToken();
            }
            if (token.is(END)) {
                return array;
            }
            token.require(NEWLINE);
            token = in.readToken();
            switch (token.type()) {
                case END: return array;
                case WHITESPACE:
                    if (!indentation.peek().value().equalsIgnoreCase(token.value())) {
                        return array;
                    }
                    break;
                default:
                    in.pushBack(token);
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
        Token token = in.readToken();
        if (token.type() == QUOTE) {
            return readQuotedString(token.value());
        }
        in.pushBack(token);
        return readUnquotedString(true, false);
    }

    public String readQuotedString(String quoteChars) throws IOException {
        StringBuffer buffer = new StringBuffer();
        do {
            Token token = in.nextToken();

            if (token.is(NEWLINE)) {
                throw new IOException("Unterminated string \"" + buffer.toString() + "\"");
            }

            if (token.is(CONTROL_CHARACTER) && token.value().equalsIgnoreCase("\\")) {
                buffer.append(in.nextToken().value());
            } else {
                if (token.is(QUOTE) && token.value().equalsIgnoreCase(quoteChars)) {
                    return buffer.toString();
                }
                buffer.append(token.value());
            }
        } while (true);
    }

    public String readUnquotedString(boolean stopOnModeChange, boolean stopOnDelimiter) throws IOException {
        StringBuffer buffer = new StringBuffer();
        do {
            Token token = in.nextToken();

            if (token.is(END) || token.is(NEWLINE) || token.isCommentBegin()
                    || (stopOnModeChange && token.is(MODE_CHANGE) && token.value().equalsIgnoreCase(Constants.MAP_END) && token.value().equalsIgnoreCase(Constants.ARRAY_END))
                    || (stopOnDelimiter && token.is(SEPARATOR))) {
                in.pushBack(token);
                return buffer.toString().trim();
            } else {
                buffer.append(token.value());
            }
        } while (true);
    }

    public String readMultiLineString(boolean keepNewlines) throws IOException {
        StringBuffer buffer = new StringBuffer();
        Token token = in.readToken();
        if (token.is(NEWLINE)) {
            token = in.readToken();
        }
        String baseIndent = token.require(WHITESPACE).value();
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
                    return buffer.toString();
                }
            }

            if (!escapeNext) {
                if (token.is(CONTROL_CHARACTER) && token.value().equalsIgnoreCase("\\")) {
                    escapeNext = true;
                    continue;
                }
                if (token.is(NEWLINE)) {
                    isOnLineStart = true;
                    Token next = in.nextToken();
                    in.pushBack(next);
                    if (!next.is(WHITESPACE)) {
                        in.pushBack(token);
                        return buffer.toString();
                    }
                    if (keepNewlines) {
                        buffer.append(token.value());
                    }
                    continue;
                }
            }

            buffer.append(token.value());
        } while (true);
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
