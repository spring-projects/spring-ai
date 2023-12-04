package org.springframework.ai.openai.embedding;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

@JsonDeserialize(builder = OpenAiEmbeddingsRequest.Builder.class)
public class OpenAiEmbeddingsRequest {

    private final List<String> input;
    private final String model;
    private final String encodingFormat;
    private final String user;

    public static class Builder {

        @JsonProperty("input")
        private List<String> input;

        @JsonProperty("model")
        private String model;
        @JsonProperty("encoding_format")
        private String encodingFormat;

        @JsonProperty("user")
        private String user;

        public Builder input(List<String> input) {
            this.input = input;
            return this;
        }

        public Builder encodingFormat(String encodingFormat) {
            this.encodingFormat = encodingFormat;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public OpenAiEmbeddingsRequest build() {
            return new OpenAiEmbeddingsRequest(this);
        }
    }

    private OpenAiEmbeddingsRequest(Builder builder) {
        this.input = builder.input;
        this.encodingFormat = builder.encodingFormat;
        this.model = builder.model;
        this.user = builder.user;
    }

    public List<String> getInput() {
        return input;
    }

    public String getEncodingFormat() {
        return encodingFormat;
    }

    public String getModel() {
        return model;
    }

    public String getUser() {
        return user;
    }

}