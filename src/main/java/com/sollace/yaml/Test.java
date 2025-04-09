package com.sollace.yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;

public class Test {
    public static void main(String[] args) {

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            try {
                System.out.print("(read/write)> ");
                String[] line = in.readLine().split(" ");

                switch (line[0]) {
                    case "exit": return;
                    case "read": {
                        String path = line[1];
                        System.out.println("JSON: ");
                        try (var reader = new YamlReader(Files.newBufferedReader(Path.of(path)))) {
                            JsonObject json = reader.readDocument();
                            StringWriter buffer = new StringWriter();
                            JsonWriter writer = new JsonWriter(buffer);
                            writer.setIndent("  ");
                            writer.setSerializeNulls(true);
                            writer.setLenient(true);
                            Streams.write(json, writer);
                            System.out.println(buffer.getBuffer().toString());
                        }
                        break;
                    }
                    case "tokens": {
                        String path = line[1];
                        System.out.println("Tokens: ");
                        try (var reader = new YamlTokenizer(Files.newBufferedReader(Path.of(path)))) {
                            for (var token : reader) {
                                System.out.print(String.format("(%s)%s", token.type(), token.value()));
                            }
                            System.out.println();
                        }
                        break;
                    }
                    case "write": {
                        String path = line[1];
                        try (var writer = new YamlWriter(Files.newBufferedWriter(Path.of(path)))) {
                            JsonObject json = new JsonObject();
                            json.addProperty("testBoolean", true);
                            json.addProperty("testString", "I AM STEVE");
                            json.addProperty("testStringTwoLongKey", "Looooong");
                            json.addProperty("testStringMulti", "I \nAM \nSTEVE");
                            JsonObject nester = new JsonObject();
                            nester.addProperty("testBoolean", true);
                            nester.addProperty("testString", "I AM STEVE");
                            nester.addProperty("testStringMulti", "I\nAM\nSTEVE");
                            json.add("nestingTest", nester);
                            JsonArray arr = new JsonArray();
                            arr.add(1);
                            arr.add(2);
                            arr.add(3);
                            json.add("testArray", arr);
                            writer.value(json);
                        }
                        break;
                    }
                    default:
                }

            } catch (Exception e) {
                e.printStackTrace();
                System.out.flush();
                System.out.println("> ");
            }
        }

    }
}
