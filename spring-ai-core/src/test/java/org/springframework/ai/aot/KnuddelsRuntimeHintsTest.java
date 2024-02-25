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

}