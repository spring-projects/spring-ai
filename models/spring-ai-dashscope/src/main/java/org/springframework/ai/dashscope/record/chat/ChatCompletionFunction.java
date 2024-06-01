package org.springframework.ai.dashscope.record.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Nottyjay Ji
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionFunction(@JsonProperty("name") String name, @JsonProperty("arguments") String arguments

) {
}
