package org.springframework.ai.dashscope.record.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @author Nottyjay Ji
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionRequestInput(@JsonProperty("messages") List<ChatCompletionMessage> messages) {
}
