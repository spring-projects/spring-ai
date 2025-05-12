/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.deepseek.aot;

import org.junit.jupiter.api.Test;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.reflection;

/**
 * @author Geng Rong
 */
class DeepSeekRuntimeHintsTests {

	@Test
	void registerHints() {
		RuntimeHints runtimeHints = new RuntimeHints();
		DeepSeekRuntimeHints deepSeekRuntimeHints = new DeepSeekRuntimeHints();
		deepSeekRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage(DeepSeekApi.class);
		for (TypeReference jsonAnnotatedClass : jsonAnnotatedClasses) {
			assertThat(runtimeHints).matches(reflection().onType(jsonAnnotatedClass));
		}
	}

}
