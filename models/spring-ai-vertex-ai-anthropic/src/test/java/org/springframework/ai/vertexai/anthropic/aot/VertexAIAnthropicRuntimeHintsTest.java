package org.springframework.ai.vertexai.anthropic.aot;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vertexai.anthropic.VertexAiAnthropicChatModel;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.reflection;

class VertexAiAnthropicRuntimeHintsTest {

	@Test
	void registerHints() {
		RuntimeHints runtimeHints = new RuntimeHints();
		VertexAiAnthropicRuntimeHints vertexAIAnthropicRuntimeHints = new VertexAiAnthropicRuntimeHints();
		vertexAIAnthropicRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage(VertexAiAnthropicChatModel.class);
		for (TypeReference jsonAnnotatedClass : jsonAnnotatedClasses) {
			assertThat(runtimeHints).matches(reflection().onType(jsonAnnotatedClass));
		}
	}

}