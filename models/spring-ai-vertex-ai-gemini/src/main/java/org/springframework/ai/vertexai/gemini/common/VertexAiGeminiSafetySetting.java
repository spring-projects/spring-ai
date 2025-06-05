/*
 * Copyright 2024-2025 the original author or authors.
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

package org.springframework.ai.vertexai.gemini.common;

public class VertexAiGeminiSafetySetting {

	/**
	 * Enum representing different threshold levels for blocking harmful content.
	 */
	public enum HarmBlockThreshold {

		HARM_BLOCK_THRESHOLD_UNSPECIFIED(0), BLOCK_LOW_AND_ABOVE(1), BLOCK_MEDIUM_AND_ABOVE(2), BLOCK_ONLY_HIGH(3),
		BLOCK_NONE(4), OFF(5);

		private final int value;

		HarmBlockThreshold(int value) {
			this.value = value;
		}

		public int getValue() {
			return this.value;
		}

	}

	/**
	 * Enum representing methods for evaluating harmful content.
	 */
	public enum HarmBlockMethod {

		HARM_BLOCK_METHOD_UNSPECIFIED(0), SEVERITY(1), PROBABILITY(2);

		private final int value;

		HarmBlockMethod(int value) {
			this.value = value;
		}

		public int getValue() {
			return this.value;
		}

	}

	/**
	 * Enum representing different categories of harmful content.
	 */
	public enum HarmCategory {

		HARM_CATEGORY_UNSPECIFIED(0), HARM_CATEGORY_HATE_SPEECH(1), HARM_CATEGORY_DANGEROUS_CONTENT(2),
		HARM_CATEGORY_HARASSMENT(3), HARM_CATEGORY_SEXUALLY_EXPLICIT(4);

		private final int value;

		HarmCategory(int value) {
			this.value = value;
		}

		public int getValue() {
			return this.value;
		}

	}

	private HarmCategory category;

	private HarmBlockThreshold threshold;

	private HarmBlockMethod method;

	// Default constructor
	public VertexAiGeminiSafetySetting() {
		this.category = HarmCategory.HARM_CATEGORY_UNSPECIFIED;
		this.threshold = HarmBlockThreshold.HARM_BLOCK_THRESHOLD_UNSPECIFIED;
		this.method = HarmBlockMethod.HARM_BLOCK_METHOD_UNSPECIFIED;
	}

	// Constructor with all fields
	public VertexAiGeminiSafetySetting(HarmCategory category, HarmBlockThreshold threshold, HarmBlockMethod method) {
		this.category = category;
		this.threshold = threshold;
		this.method = method;
	}

	// Getters and setters
	public HarmCategory getCategory() {
		return this.category;
	}

	public void setCategory(HarmCategory category) {
		this.category = category;
	}

	public HarmBlockThreshold getThreshold() {
		return this.threshold;
	}

	public void setThreshold(HarmBlockThreshold threshold) {
		this.threshold = threshold;
	}

	public HarmBlockMethod getMethod() {
		return this.method;
	}

	public void setMethod(HarmBlockMethod method) {
		this.method = method;
	}

	@Override
	public String toString() {
		return "SafetySetting{" + "category=" + this.category + ", threshold=" + this.threshold + ", method="
				+ this.method + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		VertexAiGeminiSafetySetting that = (VertexAiGeminiSafetySetting) o;

		if (this.category != that.category) {
			return false;
		}
		if (this.threshold != that.threshold) {
			return false;
		}
		return this.method == that.method;
	}

	@Override
	public int hashCode() {
		int result = this.category != null ? this.category.hashCode() : 0;
		result = 31 * result + (this.threshold != null ? this.threshold.hashCode() : 0);
		result = 31 * result + (this.method != null ? this.method.hashCode() : 0);
		return result;
	}

	public static class Builder {

		private HarmCategory category = HarmCategory.HARM_CATEGORY_UNSPECIFIED;

		private HarmBlockThreshold threshold = HarmBlockThreshold.HARM_BLOCK_THRESHOLD_UNSPECIFIED;

		private HarmBlockMethod method = HarmBlockMethod.HARM_BLOCK_METHOD_UNSPECIFIED;

		public Builder withCategory(HarmCategory category) {
			this.category = category;
			return this;
		}

		public Builder withThreshold(HarmBlockThreshold threshold) {
			this.threshold = threshold;
			return this;
		}

		public Builder withMethod(HarmBlockMethod method) {
			this.method = method;
			return this;
		}

		public VertexAiGeminiSafetySetting build() {
			return new VertexAiGeminiSafetySetting(this.category, this.threshold, this.method);
		}

	}

}
