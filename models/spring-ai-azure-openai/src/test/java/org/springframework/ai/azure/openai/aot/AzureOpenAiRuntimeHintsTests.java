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

package org.springframework.ai.azure.openai.aot;

import java.util.Set;

import com.azure.ai.openai.OpenAIAsyncClient;
import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.ChatChoice;
import org.junit.jupiter.api.Test;

import org.springframework.ai.aot.AiRuntimeHints;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.reflection;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.resource;

class AzureOpenAiRuntimeHintsTests {

	@Test
	void registerHints() {
		RuntimeHints runtimeHints = new RuntimeHints();
		AzureOpenAiRuntimeHints openAiRuntimeHints = new AzureOpenAiRuntimeHints();
		openAiRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> azureModelTypes = AiRuntimeHints.findClassesInPackage(ChatChoice.class.getPackageName(),
				(metadataReader, metadataReaderFactory) -> true);
		for (TypeReference modelType : azureModelTypes) {
			assertThat(runtimeHints).matches(reflection().onType(modelType));
		}
		assertThat(runtimeHints).matches(reflection().onType(OpenAIClient.class));
		assertThat(runtimeHints).matches(reflection().onType(OpenAIAsyncClient.class));

		assertThat(runtimeHints).matches(resource().forResource("/azure-ai-openai.properties"));
	}

}
