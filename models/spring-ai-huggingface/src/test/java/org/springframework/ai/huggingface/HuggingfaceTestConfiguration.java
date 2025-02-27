/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.huggingface;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

@SpringBootConfiguration
public class HuggingfaceTestConfiguration {

	@Bean
	public HuggingfaceChatModel huggingfaceChatModel() {
		String apiKey = System.getenv("HUGGINGFACE_API_KEY");
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException(
					"You must provide an API key.  Put it in an environment variable under the name HUGGINGFACE_API_KEY");
		}
		// Created aws-mistral-7b-instruct and update the HUGGINGFACE_CHAT_URL
		HuggingfaceChatModel huggingfaceChatModel = new HuggingfaceChatModel(apiKey,
				System.getenv("HUGGINGFACE_CHAT_URL"));
		return huggingfaceChatModel;
	}

	@Bean
	public HuggingfaceImageModel huggingfaceImageModel() {
		String apiKey = System.getenv("HUGGINGFACE_API_KEY");
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException(
					"You must provide an API key.  Put it in an environment variable under the name HUGGINGFACE_API_KEY");
		}
		HuggingfaceImageModel huggingfaceImageModel = new HuggingfaceImageModel(apiKey,
				System.getenv("HUGGINGFACE_CHAT_URL"));
		return huggingfaceImageModel;
	}

}
