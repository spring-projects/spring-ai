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

package org.springframework.ai.openaiofficial;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Context configuration for OpenAI official SDK tests.
 *
 * @author Julien Dubois
 */
@SpringBootConfiguration
public class OpenAiOfficialTestConfiguration {

	@Bean
	public OpenAiOfficialEmbeddingModel openAiEmbeddingModel() {
		return new OpenAiOfficialEmbeddingModel();
	}

	@Bean
	public OpenAiOfficialImageModel openAiImageModel() {
		return new OpenAiOfficialImageModel();
	}

	@Bean
	public OpenAiOfficialChatModel openAiChatModel() {
		return new OpenAiOfficialChatModel();
	}

}
