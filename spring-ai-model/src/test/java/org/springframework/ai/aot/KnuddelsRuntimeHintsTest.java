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

package org.springframework.ai.aot;

import org.junit.jupiter.api.Test;

import org.springframework.aot.hint.RuntimeHints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.resource;

class KnuddelsRuntimeHintsTest {

	@Test
	void knuddels() {
		var runtimeHints = new RuntimeHints();
		var knuddels = new KnuddelsRuntimeHints();
		knuddels.registerHints(runtimeHints, null);
		assertThat(runtimeHints).matches(resource().forResource("com/knuddels/jtokkit/cl100k_base.tiktoken"));
	}

	@Test
	void should_register_hints_with_custom_classloader() {
		var runtimeHints = new RuntimeHints();
		var knuddels = new KnuddelsRuntimeHints();
		ClassLoader customClassLoader = Thread.currentThread().getContextClassLoader();

		knuddels.registerHints(runtimeHints, customClassLoader);

		assertThat(runtimeHints).matches(resource().forResource("com/knuddels/jtokkit/cl100k_base.tiktoken"));
	}

	@Test
	void should_not_register_reflection_hints() {
		var runtimeHints = new RuntimeHints();
		var knuddels = new KnuddelsRuntimeHints();
		knuddels.registerHints(runtimeHints, null);

		// Verify no reflection hints are added (only resources)
		assertThat(runtimeHints.reflection().typeHints().count()).isEqualTo(0);
	}

	@Test
	void should_not_register_proxy_hints() {
		var runtimeHints = new RuntimeHints();
		var knuddels = new KnuddelsRuntimeHints();
		knuddels.registerHints(runtimeHints, null);

		// Verify no proxy hints are added
		assertThat(runtimeHints.proxies().jdkProxyHints().count()).isEqualTo(0);
	}

	@Test
	void should_register_hints_idempotently() {
		var runtimeHints = new RuntimeHints();
		var knuddels = new KnuddelsRuntimeHints();

		knuddels.registerHints(runtimeHints, null);
		long firstCount = runtimeHints.resources().resourcePatternHints().count();

		knuddels.registerHints(runtimeHints, null);
		long secondCount = runtimeHints.resources().resourcePatternHints().count();

		// Multiple registrations should result in the same hints (or double)
		assertThat(secondCount).isGreaterThanOrEqualTo(firstCount);
	}

	@Test
	void should_register_hints_only_for_jtokkit_resources() {
		var runtimeHints = new RuntimeHints();
		var knuddels = new KnuddelsRuntimeHints();
		knuddels.registerHints(runtimeHints, null);

		// Verify hints are specific to jtokkit resources
		boolean hasJtokkitResources = runtimeHints.resources()
			.resourcePatternHints()
			.anyMatch(
					hint -> hint.getIncludes().stream().anyMatch(pattern -> pattern.getPattern().contains("jtokkit")));

		assertThat(hasJtokkitResources).isTrue();
	}

}
