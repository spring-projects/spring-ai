package org.springframework.ai.dashscope.record.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Nottyjay Ji
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolCall(@JsonProperty("type") String type, @JsonProperty("function") ChatCompletionFunction function

) {
}
