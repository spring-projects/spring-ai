package org.springframework.ai.loader.impl;

import org.springframework.ai.document.Document;
import org.springframework.ai.loader.Loader;
import org.springframework.ai.splitter.TextSplitter;
import org.springframework.ai.splitter.TokenTextSplitter;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class TextLoader implements Loader {

    private final Resource resource;

    public TextLoader(Resource resource) {
        Objects.requireNonNull(resource, "The Spring Resource must not be null");
        this.resource = resource;
    }

    @Override
    public List<Document> load() {
        return load(new TokenTextSplitter());
    }

    @Override
    public List<Document> load(TextSplitter textSplitter) {
        try {
            InputStream inputStream = resource.getInputStream();
            String doc = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            return textSplitter.apply(Collections.singletonList(new Document(doc)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}