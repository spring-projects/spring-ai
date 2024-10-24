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

package org.springframework.ai.bedrock.aot;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.ai.bedrock.anthropic.api.AnthropicChatBedrockApi;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi;
import org.springframework.ai.bedrock.jurassic2.api.Ai21Jurassic2ChatBedrockApi;
import org.springframework.ai.bedrock.llama.api.LlamaChatBedrockApi;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi;
import org.springframework.ai.bedrock.titan.api.TitanEmbeddingBedrockApi;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.reflection;

class BedrockRuntimeHintsTests {

	@Test
	void registerHints() {
		RuntimeHints runtimeHints = new RuntimeHints();
		BedrockRuntimeHints bedrockRuntimeHints = new BedrockRuntimeHints();
		bedrockRuntimeHints.registerHints(runtimeHints, null);

		List<Class> classList = Arrays.asList(Ai21Jurassic2ChatBedrockApi.class, CohereChatBedrockApi.class,
				CohereEmbeddingBedrockApi.class, LlamaChatBedrockApi.class, TitanChatBedrockApi.class,
				TitanEmbeddingBedrockApi.class, AnthropicChatBedrockApi.class);

		for (Class aClass : classList) {
			Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage(aClass);
			for (TypeReference jsonAnnotatedClass : jsonAnnotatedClasses) {
				assertThat(runtimeHints).matches(reflection().onType(jsonAnnotatedClass));
			}
		}

	}

}