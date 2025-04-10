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

package org.springframework.ai.vectorstore.aot;

import org.junit.jupiter.api.Test;

import org.springframework.aot.hint.RuntimeHints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.resource;

public class VectorStoreRuntimeHintsTests {

	@Test
	void vectorStoreRuntimeHints() {
		var runtimeHints = new RuntimeHints();
		var vectorStoreHints = new VectorStoreRuntimeHints();
		vectorStoreHints.registerHints(runtimeHints, null);
		assertThat(runtimeHints)
			.matches(resource().forResource("antlr4/org/springframework/ai/vectorstore/filter/antlr4/Filters.g4"));
	}

}
