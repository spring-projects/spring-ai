package org.springframework.ai.aot;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.resource;

class SpringAiCoreRuntimeHintsTest {

	@Test
	void core() {
		var runtimeHints = new RuntimeHints();
		var knuddels = new SpringAiCoreRuntimeHints();
		knuddels.registerHints(runtimeHints, null);
		assertThat(runtimeHints).matches(resource().forResource("embedding/embedding-model-dimensions.properties"));
	}

}