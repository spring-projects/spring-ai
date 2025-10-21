/*
 * Copyright 2025 the original author or authors.
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

package org.springframework.ai.openaiofficial;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.ai.model.ApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

@SpringBootConfiguration
public class OpenAiOfficialTestConfiguration {

	private ApiKey getApiKey() {
		String apiKey = System.getenv("OPENAI_API_KEY");
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException(
					"You must provide an API key.  Put it in an environment variable under the name OPENAI_API_KEY");
		}
		return new SimpleApiKey(apiKey);
	}

	@Bean
	public OpenAIClient openAIClient() {
		return OpenAIOkHttpClient.fromEnv();
	}

	@Bean
	public OpenAiOfficialEmbeddingModel openAiEmbeddingModel(OpenAIClient client) {
		return new OpenAiOfficialEmbeddingModel(client);
	}

}
