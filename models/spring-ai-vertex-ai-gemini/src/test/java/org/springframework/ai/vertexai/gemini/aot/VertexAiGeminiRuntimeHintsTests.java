/*
 * Copyright 2024-2024 the original author or authors.
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
package org.springframework.ai.vertexai.gemini.aot;

import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatClient;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.reflection;

/**
 * @author Christian Tzolov
 * @since 0.8.1
 */
class VertexAiGeminiRuntimeHintsTests {

	@Test
	void registerHints() {
		RuntimeHints runtimeHints = new RuntimeHints();
		VertexAiGeminiRuntimeHints vertexAiGeminiRuntimeHints = new VertexAiGeminiRuntimeHints();
		vertexAiGeminiRuntimeHints.registerHints(runtimeHints, null);
		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage(VertexAiGeminiChatClient.class);
		for (TypeReference jsonAnnotatedClass : jsonAnnotatedClasses) {
			assertThat(runtimeHints).matches(reflection().onType(jsonAnnotatedClass));
		}
	}

}
