/*
 * Copyright 2023 - 2024 the original author or authors.
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
package org.springframework.ai.openai;

public class OpenAiImageOptionsBuilder {

	private final OpenAiImageOptionsImpl options;

	private OpenAiImageOptionsBuilder() {
		this.options = new OpenAiImageOptionsImpl();
	}

	public static OpenAiImageOptionsBuilder builder() {
		return new OpenAiImageOptionsBuilder();
	}

	public OpenAiImageOptionsBuilder withN(Integer n) {
		options.setN(n);
		return this;
	}

	public OpenAiImageOptionsBuilder withModel(String model) {
		options.setModel(model);
		return this;
	}

	public OpenAiImageOptionsBuilder withQuality(String quality) {
		options.setQuality(quality);
		return this;
	}

	public OpenAiImageOptionsBuilder withResponseFormat(String responseFormat) {
		options.setResponseFormat(responseFormat);
		return this;
	}

	public OpenAiImageOptionsBuilder withWidth(Integer width) {
		options.setWidth(width);
		return this;
	}

	public OpenAiImageOptionsBuilder withHeight(Integer height) {
		options.setHeight(height);
		return this;
	}

	public OpenAiImageOptionsBuilder withStyle(String style) {
		options.setStyle(style);
		return this;
	}

	public OpenAiImageOptionsBuilder withUser(String user) {
		options.setUser(user);
		return this;
	}

	public OpenAiImageOptions build() {
		return options;
	}

}
