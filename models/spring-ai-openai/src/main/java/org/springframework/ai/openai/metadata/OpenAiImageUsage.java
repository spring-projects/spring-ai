/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.openai.metadata;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Token usage reported by the OpenAI Images API.
 *
 * @since 2.0.1
 */
public final class OpenAiImageUsage {

	private final long inputTokens;

	private final long outputTokens;

	private final long totalTokens;

	private final long inputTextTokens;

	private final long inputImageTokens;

	private final @Nullable Long outputTextTokens;

	private final @Nullable Long outputImageTokens;

	OpenAiImageUsage(long inputTokens, long outputTokens, long totalTokens, long inputTextTokens, long inputImageTokens,
			@Nullable Long outputTextTokens, @Nullable Long outputImageTokens) {
		this.inputTokens = inputTokens;
		this.outputTokens = outputTokens;
		this.totalTokens = totalTokens;
		this.inputTextTokens = inputTextTokens;
		this.inputImageTokens = inputImageTokens;
		this.outputTextTokens = outputTextTokens;
		this.outputImageTokens = outputImageTokens;
	}

	/**
	 * Returns the number of tokens in the image generation input.
	 * @return the input token count
	 * @since 2.0.1
	 */
	public long getInputTokens() {
		return this.inputTokens;
	}

	/**
	 * Returns the number of tokens in the image generation output.
	 * @return the output token count
	 * @since 2.0.1
	 */
	public long getOutputTokens() {
		return this.outputTokens;
	}

	/**
	 * Returns the total number of tokens used for image generation.
	 * @return the total token count
	 * @since 2.0.1
	 */
	public long getTotalTokens() {
		return this.totalTokens;
	}

	/**
	 * Returns the number of text tokens in the image generation input.
	 * @return the input text token count
	 * @since 2.0.1
	 */
	public long getInputTextTokens() {
		return this.inputTextTokens;
	}

	/**
	 * Returns the number of image tokens in the image generation input.
	 * @return the input image token count
	 * @since 2.0.1
	 */
	public long getInputImageTokens() {
		return this.inputImageTokens;
	}

	/**
	 * Returns the number of text tokens in the image generation output.
	 * @return the output text token count, or {@code null} if not available
	 * @since 2.0.1
	 */
	public @Nullable Long getOutputTextTokens() {
		return this.outputTextTokens;
	}

	/**
	 * Returns the number of image tokens in the image generation output.
	 * @return the output image token count, or {@code null} if not available
	 * @since 2.0.1
	 */
	public @Nullable Long getOutputImageTokens() {
		return this.outputImageTokens;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof OpenAiImageUsage that)) {
			return false;
		}
		return this.inputTokens == that.inputTokens && this.outputTokens == that.outputTokens
				&& this.totalTokens == that.totalTokens && this.inputTextTokens == that.inputTextTokens
				&& this.inputImageTokens == that.inputImageTokens
				&& Objects.equals(this.outputTextTokens, that.outputTextTokens)
				&& Objects.equals(this.outputImageTokens, that.outputImageTokens);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.inputTokens, this.outputTokens, this.totalTokens, this.inputTextTokens,
				this.inputImageTokens, this.outputTextTokens, this.outputImageTokens);
	}

	@Override
	public String toString() {
		return "OpenAiImageUsage{" + "inputTokens=" + this.inputTokens + ", outputTokens=" + this.outputTokens
				+ ", totalTokens=" + this.totalTokens + ", inputTextTokens=" + this.inputTextTokens
				+ ", inputImageTokens=" + this.inputImageTokens + ", outputTextTokens=" + this.outputTextTokens
				+ ", outputImageTokens=" + this.outputImageTokens + '}';
	}

}
