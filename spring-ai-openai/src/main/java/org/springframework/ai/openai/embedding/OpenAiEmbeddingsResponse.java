package org.springframework.ai.openai.embedding;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OpenAiEmbeddingsResponse(

        @JsonProperty("data")
        List<Data> data,

        @JsonProperty("usage")
        Usage usage,

        @JsonProperty("model")
        String model,

        @JsonProperty("object")
        String object
) {
    public record Data(

            @JsonProperty("index")
            Integer index,

            @JsonProperty("embedding")
            List<Double> embedding,

            @JsonProperty("object")
            String object
    ) {
    }

    public record Usage(

            @JsonProperty("prompt_tokens")
            long promptTokens,

            @JsonProperty("completion_tokens")
            long completionTokens,

            @JsonProperty("total_tokens")
            long totalTokens

    ) {
    }

}