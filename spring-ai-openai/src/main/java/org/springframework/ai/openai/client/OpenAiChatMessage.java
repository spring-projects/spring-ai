package org.springframework.ai.openai.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

@JsonDeserialize(builder = OpenAiChatMessage.Builder.class)
public class OpenAiChatMessage {

    private final String role;
    private final String name;
    private final List<ToolCall> toolCalls;
    private final String content;

    private OpenAiChatMessage(Builder builder) {
        this.role = builder.role;
        this.name = builder.name;
        this.toolCalls = builder.toolCalls;
        this.content = builder.content;
    }

    public String getRole() {
        return role;
    }

    public String getName() {
        return name;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public String getContent() {
        return content;
    }

    public static class Builder {

        @JsonProperty("role")
        private String role;

        @JsonProperty("name")
        private String name;

        @JsonProperty("tool_calls")
        private List<ToolCall> toolCalls;

        @JsonProperty("content")
        private String content;

        public Builder role(String role) {
            this.role = role;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder toolCalls(List<ToolCall> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public OpenAiChatMessage build() {
            return new OpenAiChatMessage(this);
        }
    }
    public record ToolCall(

            @JsonProperty("function")
            ChatCompletionsRequest.Function function,

            @JsonProperty("id")
            String id,

            @JsonProperty("type")
            String type
    ) {
    }
}