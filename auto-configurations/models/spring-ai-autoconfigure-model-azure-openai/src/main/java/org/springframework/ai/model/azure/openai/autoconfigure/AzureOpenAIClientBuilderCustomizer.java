/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.model.azure.openai.autoconfigure;

import com.azure.ai.openai.OpenAIClientBuilder;

/**
 * Callback interface that can be implemented by beans wishing to customize the
 * {@link OpenAIClientBuilder} whilst retaining the default auto-configuration.
 *
 * @author Manuel Andreo Garcia
 * @since 1.0.0-M6
 */
@FunctionalInterface
public interface AzureOpenAIClientBuilderCustomizer {

	/**
	 * Customize the {@link OpenAIClientBuilder}.
	 * @param clientBuilder the {@link OpenAIClientBuilder} to customize
	 */
	void customize(OpenAIClientBuilder clientBuilder);

}
