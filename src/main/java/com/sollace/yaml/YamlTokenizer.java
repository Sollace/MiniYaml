package com.sollace.yaml;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

public class YamlTokenizer implements Closeable, Iterable<YamlTokenizer.Token> {
    private final Reader in;
    private int readerPosition;
    private char[] buffer;
    private int bufferFillLength;

    private final List<Token> bufferedTokens = new ArrayList<>();

    public YamlTokenizer(Reader in) {
        this.in = in;
    }

    private boolean ready() throws IOException {
        if (!bufferedTokens.isEmpty()) {
            return true;
        }

        if (buffer == null) {
            return in.ready();
        }
        return readerPosition < bufferFillLength;
    }

    private char peek(int index) throws IOException {
        if (buffer == null) {
            if (!in.ready()) {
                return '\0';
            }

            buffer = new char[256];
            bufferFillLength = in.read(buffer);
        }

        if (readerPosition + index >= bufferFillLength) {
            if (!in.ready()) {
                return '\0';
            }

            if (index == 0) {
                readerPosition = 0;
                bufferFillLength = in.read(buffer);
            } else {
                char[] newBuf = new char[buffer.length * 2];
                System.arraycopy(buffer, 0, newBuf, 0, bufferFillLength);
                bufferFillLength += in.read(newBuf, bufferFillLength, newBuf.length - bufferFillLength);
                buffer = newBuf;
            }
        }
        return buffer[readerPosition + index];
    }

    private char read() throws IOException {
        char c = peek(0);
        readerPosition++;
        return c;
    }

    public void pushBack(Token token) {
        bufferedTokens.add(0, token);
    }

    public Token readToken() throws IOException {
        return skipComments(nextToken());
    }

    public Token nextToken() throws IOException {
        if (!bufferedTokens.isEmpty()) {
            return bufferedTokens.remove(0);
        }

        Token token = doReadToken();
        //System.out.println(token);
        return token;
    }

    private Token skipComments(Token token) throws IOException {
        if (token.is(Token.Type.NEWLINE)) {
            do {
                Token next = nextToken();
                if (!next.is(Token.Type.NEWLINE)) {
                    pushBack(next);
                    break;
                }
            } while (true);
        }

        if (token.is(Token.Type.NEWLINE) || token.is(Token.Type.WHITESPACE)) {
            Token next = nextToken();
            if (!next.isCommentBegin()) {
                pushBack(next);
                return token;
            }
            token = next;
        }

        if (token.isCommentBegin()) {
            do {
                token = nextToken();
                if (token.is(Token.Type.END) || token.is(Token.Type.NEWLINE)) {
                    return token;
                }
            } while (true);
        }

        return token;
    }

    private Token doReadToken() throws IOException {
        char c = read();

        if (c == '\0') {
            return Token.END;
        }

        if (c == '\r' || c == '\n') {
            c = peek(0);
            if (c == '\r' || c == '\n') {
                read();
            }
            return Token.NEWLINE;
        }

        if (c == ':' || c == '-') {
            char next = peek(0);
            if (Character.isWhitespace(next) || (c == ':' && (next == '\r' || next == '\n'))) {
                return new Token(Token.Type.SEPARATOR, Character.toString(c) + " ");
            }
        }

        if (c == '?' && !Character.isWhitespace(peek(0))) {
            read();
            return new Token(Token.Type.CONTROL_CHARACTER, Constants.KEY_INDICATOR);
        }

        if (c == '#' || c == '%' || c == '\\') {
            return new Token(Token.Type.CONTROL_CHARACTER, Character.toString(c));
        }

        if (c == '!' && peek(0) == '!' && !Character.isWhitespace(peek(1))) {
            read();
            return new Token(Token.Type.CONTROL_CHARACTER, Constants.TYPE_COERSION_INDICATOR);
        }

        if (c == '"' || c == '\'') {
            return new Token(Token.Type.QUOTE, Character.toString(c));
        }

        if (c == '[' || c == ']' || c == '{' || c == '}') {
            return new Token(Token.Type.MODE_CHANGE, Character.toString(c));
        }

        StringBuffer buffer = new StringBuffer();
        buffer.append(c);

        if (Character.isWhitespace(c)) {
            do {
                c = peek(0);
                if (c == '\0' || c == '\n' || c == '\r' || c == '"' || c == '\'' || c == '\\' || c == '[' || c == '{') {
                    break;
                } else if (!Character.isWhitespace(c)) {
                    if (buffer.length() == 1 && (c == '|' || c == '>')) {
                        buffer.append(read());
                        return new Token(Token.Type.CONTROL_CHARACTER, buffer.toString());
                    }
                    break;
                }

                c = peek(1);
                if (c == '|' || c == '>') {
                    break;
                }

                buffer.append(read());
            } while (true);

            return new Token(Token.Type.WHITESPACE, buffer.toString());
        } else {
            do {
                c = peek(0);
                if (c == '\0' || c == '\n' || c == '\r' || c == '"' || c == '\'' || c == '\\' || c == ':' || c == ']' || c == '}' || Character.isWhitespace(c)) {
                    break;
                }

                buffer.append(read());
            } while (true);

            return new Token(Token.Type.TEXT, buffer.toString());
        }
    }

    @Override
    public void close() throws IOException {
        in.close();
    }


    record Token(Type type, @Nullable String value) {
        static final Token END = new Token(Type.END, "");
        static final Token NEWLINE = new Token(Type.NEWLINE, System.lineSeparator());
        static final Token EMPTY = new Token(Token.Type.WHITESPACE, "");
        public enum Type {
            TEXT,
            WHITESPACE,
            NEWLINE,
            QUOTE,
            SEPARATOR,
            CONTROL_CHARACTER,
            MODE_CHANGE,
            END
        }

        boolean isCommentBegin() {
            return is(Type.CONTROL_CHARACTER)
                    && (value().equalsIgnoreCase(Constants.COMMENT_PREFIX) || value().equalsIgnoreCase(Constants.DIRECTIVE_PREFIX));
        }

        boolean is(Type type) {
            return type() == type;
        }

        Token require(String value) throws IOException {
            if (!value.equalsIgnoreCase(value())) {
                throw new IOException("Expected token with value=" + value + " but got " + value());
            }
            return this;
        }

        Token require(Type type) throws IOException {
            if (type != type()) {
                throw new IOException("Expected token with type=" + type + " but got " + type());
            }
            return this;
        }

        Token permit(Type...types) throws IOException {
            for (var type : types) {
                if (type == type()) {
                    return this;
                }
            }
            throw new IOException("Expected token with type=" + Arrays.toString(types) + " but got " + type());
        }
    }

    @Override
    public Iterator<Token> iterator() {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                try {
                    return ready();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public Token next() {
                try {
                    return nextToken();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
    }
}
