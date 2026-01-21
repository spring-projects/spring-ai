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

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jspecify.annotations.Nullable;

/**
 * Default implementation of the {@link Usage} interface.
 *
 * @author Mark Pollack
 * @author Ilayaperumal Gopinathan
 * @since 1.0.0
 */
@JsonPropertyOrder({ "promptTokens", "completionTokens", "totalTokens", "nativeUsage" })
public class DefaultUsage implements Usage {

	private final Integer promptTokens;

	private final Integer completionTokens;

	private final int totalTokens;

	private final @Nullable Object nativeUsage;

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
	public DefaultUsage(@Nullable Integer promptTokens, @Nullable Integer completionTokens,
			@Nullable Integer totalTokens, @Nullable Object nativeUsage) {
		this.promptTokens = promptTokens != null ? promptTokens : 0;
		this.completionTokens = completionTokens != null ? completionTokens : 0;
		this.totalTokens = totalTokens != null ? totalTokens
				: calculateTotalTokens(this.promptTokens, this.completionTokens);
		this.nativeUsage = nativeUsage;
	}

	/**
	 * Create a new DefaultUsage with promptTokens and completionTokens.
	 * @param promptTokens the number of tokens in the prompt, or {@code null} if not
	 * available
	 * @param completionTokens the number of tokens in the generation, or {@code null} if
	 * not available
	 */
	public DefaultUsage(Integer promptTokens, Integer completionTokens) {
		this(promptTokens, completionTokens, null, null);
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
	public DefaultUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
		this(promptTokens, completionTokens, totalTokens, null);
	}

	/**
	 * Create a new DefaultUsage with promptTokens, completionTokens, and totalTokens.
	 * This constructor is used for JSON deserialization and handles both the new format
	 * with completionTokens and the legacy format with generationTokens.
	 * @param promptTokens the number of tokens in the prompt
	 * @param completionTokens the number of tokens in the completion (new format)
	 * @param totalTokens the total number of tokens
	 * @param nativeUsage the native usage object
	 * @return a new DefaultUsage instance
	 */
	@JsonCreator
	public static DefaultUsage fromJson(@JsonProperty("promptTokens") Integer promptTokens,
			@JsonProperty("completionTokens") Integer completionTokens,
			@JsonProperty("totalTokens") Integer totalTokens, @JsonProperty("nativeUsage") Object nativeUsage) {
		return new DefaultUsage(promptTokens, completionTokens, totalTokens, nativeUsage);
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
	@JsonProperty("nativeUsage")
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public @Nullable Object getNativeUsage() {
		return this.nativeUsage;
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
		return this.totalTokens == that.totalTokens && Objects.equals(this.promptTokens, that.promptTokens)
				&& Objects.equals(this.completionTokens, that.completionTokens)
				&& Objects.equals(this.nativeUsage, that.nativeUsage);
	}

	@Override
	public int hashCode() {
		int result = Objects.hashCode(this.promptTokens);
		result = 31 * result + Objects.hashCode(this.completionTokens);
		result = 31 * result + this.totalTokens;
		result = 31 * result + Objects.hashCode(this.nativeUsage);
		return result;
	}

	@Override
	public String toString() {
		return "DefaultUsage{" + "promptTokens=" + this.promptTokens + ", completionTokens=" + this.completionTokens
				+ ", totalTokens=" + this.totalTokens + '}';
	}

}
