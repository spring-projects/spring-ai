package org.springframework.ai.dashscope.record.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents a chat completion response output.
 *
 * @param choices A list of chat completion choices. Can be more than one if n is greater
 * than 1.
 * @author Nottyjay Ji
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionOutput(@JsonProperty("choices") List<ChatCompletionChoice> choices) {
}
