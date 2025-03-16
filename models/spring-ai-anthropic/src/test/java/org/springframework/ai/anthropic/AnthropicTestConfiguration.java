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

package org.springframework.ai.anthropic;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

@SpringBootConfiguration
public class AnthropicTestConfiguration {

	@Bean
	public AnthropicApi anthropicApi() {
		return new AnthropicApi(getApiKey());
	}

	private String getApiKey() {
		String apiKey = System.getenv("ANTHROPIC_API_KEY");
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException(
					"You must provide an API key.  Put it in an environment variable under the name ANTHROPIC_API_KEY");
		}
		return apiKey;
	}

	@Bean
	public AnthropicChatModel anthropicChatModel(AnthropicApi api) {
		return AnthropicChatModel.builder().anthropicApi(api).build();
	}

}
