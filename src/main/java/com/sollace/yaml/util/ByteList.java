package com.sollace.yaml.util;

public class ByteList {
    private byte[] bytes;
    private int length;

    public ByteList(int initialLength) {
        bytes = new byte[initialLength];
    }

    public int length() {
        return length;
    }

    public byte get(int index) {
        return bytes[index];
    }

    public void add(byte b) {
        if (++length >= bytes.length) {
            byte[] copy = new byte[bytes.length * 2];
            System.arraycopy(bytes, 0, copy, 0, bytes.length);
            bytes = copy;
        }
        bytes[length] = b;
    }

    public byte[] toArray() {
        byte[] copy = new byte[length];
        System.arraycopy(bytes, 0, copy, 0, copy.length);
        return copy;
    }
}
