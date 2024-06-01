package org.springframework.ai.dashscope.record.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.dashscope.api.DashscopeApi;

import java.util.List;

/**
 * @author Nottyjay Ji
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionRequestParameters(@JsonProperty("seed") Integer seed,
		@JsonProperty("max_tokens") Integer maxTokens, @JsonProperty("temperature") Float temperature,
		@JsonProperty("incremental_output") Boolean incrementalOutput, @JsonProperty("top_p") Float topP,
		@JsonProperty("top_k") Integer topK, @JsonProperty("repetition_penalty") Float repetitionPenalty,
		@JsonProperty("stop") List<Object> stop, @JsonProperty("enable_search") Boolean enableSearch,
		@JsonProperty("result_format") String resultFormat,
		@JsonProperty("tools") List<DashscopeApi.FunctionTool> tools) {
}
