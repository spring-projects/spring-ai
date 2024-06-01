package org.springframework.ai.dashscope.record;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.chat.metadata.Usage;

/**
 * Dashscope请求返回token使用量
 *
 * @param outputTokens 模型输出token量
 * @param inputTokens 提示词token量
 * @param totalTokens 总token量
 */
public record TokenUsage(@JsonProperty("output_tokens") Integer outputTokens,
		@JsonProperty("input_tokens") Integer inputTokens,
		@JsonProperty("total_tokens") Integer totalTokens) implements Usage {

	@Override
	public Long getPromptTokens() {
		return Long.valueOf(inputTokens);
	}

	@Override
	public Long getGenerationTokens() {
		return Long.valueOf(outputTokens);
	}

	@Override
	public Long getTotalTokens() {
		return Long.valueOf(totalTokens);
	}
}
