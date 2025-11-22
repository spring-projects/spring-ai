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

package org.springframework.ai.cohere.aot;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;

/**
 * The CohereRuntimeHints class is responsible for registering runtime hints for Cohere AI
 * API classes.
 *
 * @author Ricken Bazolo
 */
public class CohereRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(final RuntimeHints hints, final ClassLoader classLoader) {
		var mcs = MemberCategory.values();

		for (var tr : findJsonAnnotatedClassesInPackage("org.springframework.ai.cohere")) {
			hints.reflection().registerType(tr, mcs);
		}
	}

}
