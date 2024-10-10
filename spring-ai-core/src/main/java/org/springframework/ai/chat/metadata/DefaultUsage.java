/*
 * Copyright 2024 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.chat.metadata;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Default implementation of the {@link Usage} interface.
 *
 * @author Mark Pollack
 * @since 1.0.0
 */
public class DefaultUsage implements Usage {

	private final Long promptTokens;

	private final Long generationTokens;

	private final Long totalTokens;

	/**
	 * Create a new DefaultUsage with promptTokens and generationTokens.
	 * @param promptTokens the number of tokens in the prompt, or {@code null} if not
	 * available
	 * @param generationTokens the number of tokens in the generation, or {@code null} if
	 * not available
	 */
	public DefaultUsage(Long promptTokens, Long generationTokens) {
		this(promptTokens, generationTokens, null);
	}

	/**
	 * Create a new DefaultUsage with promptTokens, generationTokens, and totalTokens.
	 * @param promptTokens the number of tokens in the prompt, or {@code null} if not
	 * available
	 * @param generationTokens the number of tokens in the generation, or {@code null} if
	 * not available
	 * @param totalTokens the total number of tokens, or {@code null} to calculate from
	 * promptTokens and generationTokens
	 */
	@JsonCreator
	public DefaultUsage(@JsonProperty("promptTokens") Long promptTokens,
			@JsonProperty("generationTokens") Long generationTokens, @JsonProperty("totalTokens") Long totalTokens) {
		this.promptTokens = promptTokens != null ? promptTokens : 0L;
		this.generationTokens = generationTokens != null ? generationTokens : 0L;
		this.totalTokens = totalTokens != null ? totalTokens
				: calculateTotalTokens(this.promptTokens, this.generationTokens);
	}

	@Override
	@JsonProperty("promptTokens")
	public Long getPromptTokens() {
		return promptTokens;
	}

	@Override
	@JsonProperty("generationTokens")
	public Long getGenerationTokens() {
		return generationTokens;
	}

	@Override
	@JsonProperty("totalTokens")
	public Long getTotalTokens() {
		return totalTokens;
	}

	private Long calculateTotalTokens(Long promptTokens, Long generationTokens) {
		return promptTokens + generationTokens;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		DefaultUsage that = (DefaultUsage) o;
		return Objects.equals(promptTokens, that.promptTokens)
				&& Objects.equals(generationTokens, that.generationTokens)
				&& Objects.equals(totalTokens, that.totalTokens);
	}

	@Override
	public int hashCode() {
		return Objects.hash(promptTokens, generationTokens, totalTokens);
	}

	@Override
	public String toString() {
		return "DefaultUsage{" + "promptTokens=" + promptTokens + ", generationTokens=" + generationTokens
				+ ", totalTokens=" + totalTokens + '}';
	}

}
