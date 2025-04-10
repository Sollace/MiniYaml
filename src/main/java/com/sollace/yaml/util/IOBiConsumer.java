package com.sollace.yaml.util;

import java.io.IOException;

@FunctionalInterface
public interface IOBiConsumer<A, B> {
    void accept(A a, B b) throws IOException;
}
