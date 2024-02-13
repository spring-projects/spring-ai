package org.springframework.ai.aot;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.*;

import static org.assertj.core.api.Assertions.assertThat;

class AiRuntimeHintsTests {

	@Test
	void registerHints() {
		RuntimeHints runtimeHints = new RuntimeHints();
		AiRuntimeHints aiRuntimeHints = new AiRuntimeHints();
		aiRuntimeHints.registerHints(runtimeHints, null);
		assertThat(runtimeHints).matches(resource().forResource("embedding/embedding-model-dimensions.properties"));
		assertThat(runtimeHints).matches(resource().forResource("com/knuddels/jtokkit/cl100k_base.tiktoken"));
	}

}
