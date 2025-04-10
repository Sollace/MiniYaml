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

import com.sollace.yaml.util.CharBuf;

public class YamlTokenizer implements Closeable, Iterable<YamlTokenizer.Token> {
    private CharBuf in;

    private final List<Token> bufferedTokens = new ArrayList<>();

    public YamlTokenizer(Reader in) {
        this.in = new CharBuf(in);
    }

    public boolean ready() throws IOException {
        return !bufferedTokens.isEmpty() || in.ready();
    }

    public void pushBack(Token token) {
        bufferedTokens.add(0, token);
    }

    public Token readToken() throws IOException {
        return skipComments(nextSkipEmptyLines());
    }

    public Token peekToken() throws IOException {
        Token token = nextToken();
        pushBack(token);
        return token;
    }

    public Token skipToken(Token.Type type) throws IOException {
        Token token = nextToken();
        if (!token.is(type)) {
            pushBack(token);
        }
        return token;
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
        if (token.is(Token.Type.NEWLINE) || token.is(Token.Type.WHITESPACE)) {
            Token next = nextSkipEmptyLines();
            if (!next.isCommentBegin()) {
                pushBack(next);
                return token;
            }
            token = next;
        }

        if (token.isCommentBegin()) {
            do {
                token = nextSkipEmptyLines();
                if (token.is(Token.Type.END) || token.is(Token.Type.NEWLINE)) {
                    return token;
                }
            } while (true);
        }

        return token;
    }

    private Token nextSkipEmptyLines() throws IOException {
        Token token = nextToken();
        if (token.is(Token.Type.NEWLINE)) {
            do {
                Token next = nextToken();
                if (!next.is(Token.Type.NEWLINE)) {
                    pushBack(next);
                    break;
                }
            } while (true);
        }
        return token;
    }

    private Token doReadToken() throws IOException {
        char c = in.read();

        if (c == '\0') {
            return Token.END;
        }

        if (c == '\r' || c == '\n') {
            char c2 = in.peek(0);
            if (c != c2 && (c2 == '\r' || c2 == '\n')) {
                in.read();
            }
            return Token.NEWLINE;
        }

        if (c == ':' || c == '-') {
            char next = in.peek(0);
            if (Character.isWhitespace(next) || (c == ':' && (next == '\r' || next == '\n'))) {
                return new Token(Token.Type.SEPARATOR, Character.toString(c) + " ");
            }
        }

        if (c == '?') {
            if (Character.isWhitespace(in.peek(0))) {
                in.read();
                return new Token(Token.Type.SEPARATOR, Constants.SET_ELEMENT_PREFIX);
            }
            return new Token(Token.Type.CONTROL_CHARACTER, Constants.KEY_INDICATOR);
        }

        if (c == '#' || c == '%' || c == '\\') {
            return new Token(Token.Type.CONTROL_CHARACTER, Character.toString(c));
        }

        if (c == '!' && in.peek(0) == '!' && !Character.isWhitespace(in.peek(1))) {
            in.read();
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
                c = in.peek(0);
                if (c == '\0' || c == '\n' || c == '\r' || c == '"' || c == '\'' || c == '\\' || c == '[' || c == '{') {
                    break;
                } else if (!Character.isWhitespace(c)) {
                    if (buffer.length() == 1 && (c == '|' || c == '>')) {
                        buffer.append(in.read());

                        c = in.peek(0);
                        if (c == '-') {
                            buffer.append(in.read());
                        }

                        return new Token(Token.Type.CONTROL_CHARACTER, buffer.toString());
                    }
                    break;
                }

                c = in.peek(1);
                if (c == '|' || c == '>') {
                    break;
                }

                buffer.append(in.read());
            } while (true);

            return new Token(Token.Type.WHITESPACE, buffer.toString());
        } else {
            do {
                c = in.peek(0);
                if (c == '\0' || c == '\n' || c == '\r' || c == '"' || c == '\'' || c == '\\' || c == ':' || c == ']' || c == '}' || Character.isWhitespace(c)) {
                    break;
                }

                buffer.append(in.read());
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
