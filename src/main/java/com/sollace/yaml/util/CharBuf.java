package com.sollace.yaml.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;

public class CharBuf implements Closeable {
    private final Reader in;
    private int readerPosition;
    private char[] buffer;
    private int bufferFillLength;

    public CharBuf(Reader in) {
        this.in = in;
    }

    public boolean ready() throws IOException {
        if (buffer == null) {
            return in.ready();
        }
        return readerPosition < bufferFillLength;
    }

    public char peek(int index) throws IOException {
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

    public char read() throws IOException {
        char c = peek(0);
        readerPosition++;
        return c;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
