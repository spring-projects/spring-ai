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

package org.springframework.ai.google.genai;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.genai.types.ImageConfig;

/**
 * Google GenAI ImageConfig
 *
 * @author 楚孔响
 * @version 1.0.0
 * @date 2025-10-10 15:33:20
 */
public class GoogleGenAiChatOptionsImageConfig {

	@JsonProperty("aspectRatio")
	private String aspectRatio;

	public String getAspectRatio() {
		return this.aspectRatio;
	}

	public void setAspectRatio(String aspectRatio) {
		this.aspectRatio = aspectRatio;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof GoogleGenAiChatOptionsImageConfig that)) {
			return false;
		}

		return Objects.equals(this.aspectRatio, that.aspectRatio);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.aspectRatio);
	}

	@Override
	public String toString() {
		return "GoogleGenAiChatOptionsImageConfig{" + "aspectRatio='" + this.aspectRatio + '\'' + '}';
	}

	public ImageConfig convert() {
		return ImageConfig.builder().aspectRatio(this.aspectRatio).build();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private GoogleGenAiChatOptionsImageConfig imageConfig = new GoogleGenAiChatOptionsImageConfig();

		public Builder aspectRatio(String aspectRatio) {
			this.imageConfig.setAspectRatio(aspectRatio);
			return this;
		}

		public GoogleGenAiChatOptionsImageConfig build() {
			return this.imageConfig;
		}

	}

}
