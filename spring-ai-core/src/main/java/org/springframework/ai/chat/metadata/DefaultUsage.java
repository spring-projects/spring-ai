/*
 * Copyright 2023-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.chat.metadata;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Default implementation of the {@link Usage} interface.
 *
 * @author Mark Pollack
 * @author Ilayaperumal Gopinathan
 * @since 1.0.0
 */
public class DefaultUsage implements Usage {

	private final Integer promptTokens;

	private final Integer completionTokens;

	@Deprecated(forRemoval = true, since = "1.0.0-M6")
	private final Long generationTokens;

	private final int totalTokens;

	private final Object nativeUsage;

	/**
	 * Create a new DefaultUsage with promptTokens, completionTokens, totalTokens and
	 * native {@link Usage} object.
	 * @param promptTokens the number of tokens in the prompt, or {@code null} if not
	 * available
	 * @param completionTokens the number of tokens in the generation, or {@code null} if
	 * not available
	 * @param totalTokens the total number of tokens, or {@code null} to calculate from
	 * promptTokens and completionTokens
	 * @param nativeUsage the native usage object returned by the model provider, or
	 * {@code null} to return the map of prompt, completion and total tokens.
	 */
	public DefaultUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens, Object nativeUsage) {
		this.promptTokens = promptTokens != null ? promptTokens : 0;
		this.completionTokens = completionTokens != null ? completionTokens : 0;
		this.generationTokens = Long.valueOf(this.completionTokens);
		this.totalTokens = totalTokens != null ? totalTokens
				: calculateTotalTokens(this.promptTokens, this.completionTokens);
		this.nativeUsage = (nativeUsage != null) ? nativeUsage : getDefaultNativeUsage();
	}

	/**
	 * Create a new DefaultUsage with promptTokens and completionTokens.
	 * @param promptTokens the number of tokens in the prompt, or {@code null} if not
	 * available
	 * @param completionTokens the number of tokens in the generation, or {@code null} if
	 * not available
	 */
	public DefaultUsage(Integer promptTokens, Integer completionTokens) {
		this(promptTokens, completionTokens, null);
	}

	/**
	 * Create a new DefaultUsage with promptTokens, completionTokens, and totalTokens.
	 * @param promptTokens the number of tokens in the prompt, or {@code null} if not
	 * available
	 * @param completionTokens the number of tokens in the generation, or {@code null} if
	 * not available
	 * @param totalTokens the total number of tokens, or {@code null} to calculate from
	 * promptTokens and completionTokens
	 */
	@JsonCreator
	public DefaultUsage(@JsonProperty("promptTokens") Integer promptTokens,
			@JsonProperty("completionTokens") Integer completionTokens,
			@JsonProperty("totalTokens") Integer totalTokens) {
		this(promptTokens, completionTokens, totalTokens, null);
	}

	@Override
	@JsonProperty("promptTokens")
	public Integer getPromptTokens() {
		return this.promptTokens;
	}

	@Override
	@JsonProperty("completionTokens")
	public Integer getCompletionTokens() {
		return this.completionTokens;
	}

	@Override
	@JsonProperty("totalTokens")
	public Integer getTotalTokens() {
		return this.totalTokens;
	}

	@Override
	@JsonIgnore
	public Object getNativeUsage() {
		return this.nativeUsage;
	}

	/**
	 * By default, return the Map of prompt, completion and total tokens.
	 * @return map containing the prompt, completion and total tokens.
	 */
	private Map<String, Integer> getDefaultNativeUsage() {
		Map<String, Integer> usage = new HashMap<>();
		usage.put("promptTokens", this.promptTokens);
		usage.put("completionTokens", this.completionTokens);
		usage.put("totalTokens", this.totalTokens);
		return usage;
	}

	private Integer calculateTotalTokens(Integer promptTokens, Integer completionTokens) {
		return promptTokens + completionTokens;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		DefaultUsage that = (DefaultUsage) o;
		return Objects.equals(this.promptTokens, that.promptTokens)
				&& Objects.equals(this.completionTokens, that.completionTokens)
				&& Objects.equals(this.totalTokens, that.totalTokens);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.promptTokens, this.completionTokens, this.totalTokens);
	}

	@Override
	public String toString() {
		return "DefaultUsage{" + "promptTokens=" + this.promptTokens + ", completionTokens=" + this.completionTokens
				+ ", totalTokens=" + this.totalTokens + '}';
	}

}
