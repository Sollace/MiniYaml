package com.sollace.yaml;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class YamlWriter implements AutoCloseable {
    private final Writer out;

    private String lastKey = "Root";

    protected String indent = "  ";

    private List<Line> lines = new ArrayList<>();

    private BlockScope currentScope;
    private Line currentLine;

    public YamlWriter(Writer out) {
        this.out = out;
        beginScope();
    }

    public int getLineNumber() {
        return lines.size();
    }

    public int getColumnNumber() {
        return currentLine.length();
    }

    private void nextLine() {
        currentLine = new Line(currentScope);
        currentScope.lines.add(currentLine);
        lines.add(currentLine);
    }

    private void beginScope() {
        currentScope = new BlockScope(currentScope, lastKey == null ? "_" : lastKey, indent);
        nextLine();
    }

    private void endScope() throws IOException {
        for (Line line : currentScope.lines) {
            currentScope.valueStartPosition = Math.max(currentScope.valueStartPosition, line.valueColumn());
        }
        currentScope = currentScope.parent;
        if (currentScope == null) {
            currentScope = new BlockScope(null, "Root", indent);
        }
    }

    public <T> void value(@Nullable Iterable<T> value, IOBiConsumer<YamlWriter, T> valueWriter) throws IOException {
        if (value == null) {
            nullValue();
            return;
        }

        beginScope();
        int i = 0;
        for (T t : value) {
            arrayIndex(i++);
            nextLine();
            currentLine.prefix.append(Constants.ARRAY_ELEMENT_PREFIX);
            valueWriter.accept(this, t);
        }
        endScope();
    }

    public <T> void value(@Nullable Map<String, T> map, IOBiConsumer<YamlWriter, T> valueWriter) throws IOException {
        if (map == null) {
            nullValue();
            return;
        }

        beginScope();
        for (String key : map.keySet()) {
            nextLine();
            name(key);
            valueWriter.accept(this, map.get(key));
        }

        endScope();
    }

    public void comment(String comment) throws IOException {
        if (comment.indexOf("\n") != -1) {
            String[] lines = comment.split("\n");
            int columnPos = getColumnNumber();
            for (String line : lines) {
                while (getColumnNumber() < columnPos) {
                    currentLine.prefix.append(!indent.isEmpty() && getColumnNumber() < columnPos - indent.length() ? indent : " ");
                }
                currentLine.comment = line.stripTrailing();
                nextLine();
            }
        } else {
            currentLine.comment = comment.stripTrailing();
            nextLine();
        }
    }

    public void value(JsonElement json) throws IOException {
        if (json instanceof JsonObject) {
            value(json.getAsJsonObject());
        } else if (json instanceof JsonArray) {
            value(json.getAsJsonArray());
        } else if (json instanceof JsonPrimitive primitive) {
            if (primitive.isBoolean()) {
                value(primitive.getAsBoolean());
            } else if (primitive.isString()) {
                value(primitive.getAsString());
            } else if (primitive.isNumber()) {
                value(primitive.getAsNumber());
            }
        }
    }

    public void value(JsonObject json) throws IOException {
        value(json.asMap(), YamlWriter::value);
    }

    public void value(JsonArray json) throws IOException {
        value(json, YamlWriter::value);
    }

    public void value(boolean value) throws IOException {
        currentLine.value = value ? Constants.TRUE : Constants.FALSE;
    }

    public void value(@Nullable String value) throws IOException {
        if (value == null) {
            nullValue();
        } else if (value.indexOf('\n') != -1) {
            String[] lines = value.split("\n");
            currentLine.prefix.append(Constants.MULTI_LINE_NEWLINE_PRESERVING_STRING);
            for (int i = 0; i < lines.length; i++) {
                nextLine();
                currentLine.value = lines[i].stripTrailing();
            }
        } else {
            currentLine.value = Constants.quoteString(value);
        }
    }

    public void value(Number value) throws IOException {
        if (value == null) {
            nullValue();
        } else if (value instanceof Double) {
            value(value.doubleValue());
        } else if (value instanceof Long) {
            value(value.longValue());
        } else if (value instanceof Float) {
            value(value.floatValue());
        } else if (value instanceof Integer) {
            value(value.intValue());
        } else if (value instanceof BigInteger big) {
            value(big.intValueExact());
        } else if (value instanceof BigDecimal big) {
            value(big.intValueExact());
        } else {
            value(value.toString());
        }
    }

    public void value(double value) throws IOException {
        currentLine.value = String.format("%2.d", value);
    }

    public void value(float value) throws IOException {
        currentLine.value = String.format("%2.d", value);
    }

    public void value(long value) throws IOException {
        currentLine.value = Long.toString(value);
    }

    public void value(short value) throws IOException {
        currentLine.value = Short.toString(value);
    }

    public void value(byte value) throws IOException {
        currentLine.value = Byte.toString(value);
    }

    public void value(int value) throws IOException {
        currentLine.value = Integer.toString(value);
    }

    public void nullValue() throws IOException {
        currentLine.value = Constants.NULL;
    }

    public void name(String key) throws IOException {
        if (currentLine.value != null) {
            nextLine();
        }
        lastKey = key;
        currentLine.prefix.append(key).append(Constants.KEY_VALUE_PAIR_SEPARATOR);
    }

    public void arrayIndex(int i) throws IOException {
        lastKey = Integer.toString(i);
    }

    @Override
    public void close() throws IOException {
        out.write(Constants.REFERENCE_CARD);
        out.write("\n");
        for (Line line : lines) {
            if (line.length() > 0) {
                line.write(out);
            }
        }
        out.flush();
        out.close();
    }

    @Override
    public String toString() {
        return String.format("YamlWriter[at line %d, column %d (%)]", getLineNumber(), getColumnNumber(), currentScope.name);
    }

    static class Line {
        final BlockScope scope;

        StringBuffer prefix = new StringBuffer();

        @Nullable String value;
        @Nullable String comment;

        Line(BlockScope scope) {
            this.scope = scope;
        }

        int length() {
            return prefix.length() + (value == null ? 0 : value.length()) + (comment == null ? 0 : comment.length());
        }

        int valueColumn() {
            if (value == null) {
                return 0;
            }
            return prefix.length();
        }

        void write(Writer out) throws IOException {
            out.append(scope.indent);
            out.append(prefix);
            if (value != null || comment != null) {
                for (int i = prefix.length(); i < scope.valueStartPosition; i++) {
                    out.append(' ');
                }
                if (value != null) {
                    out.append(value);
                }
                if (comment != null) {
                    out.append(Constants.COMMENT_PREFIX);
                    out.append(' ');
                    out.append(comment);
                }
            }
            out.append('\n');
        }
    }

    static class BlockScope {
        @Nullable
        final BlockScope parent;
        final String name;
        final String indent;
        final List<Line> lines = new ArrayList<>();

        int valueStartPosition;

        BlockScope(@Nullable BlockScope parent, String name, String indent) {
            this.parent = parent;
            this.name = parent == null ? "" : parent.name + "." + name;
            this.indent = parent == null || parent.parent == null ? "" : parent.indent + indent;
        }
    }
}
