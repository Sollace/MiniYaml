package com.sollace.yaml;

import java.io.IOException;

@FunctionalInterface
public interface IOBiConsumer<A, B> {
    void accept(A a, B b) throws IOException;
}
