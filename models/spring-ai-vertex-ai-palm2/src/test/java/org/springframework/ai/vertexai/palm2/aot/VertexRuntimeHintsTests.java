package org.springframework.ai.vertexai.palm2.aot;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vertexai.palm2.api.VertexAiApi;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.reflection;

class VertexRuntimeHintsTests {

	@Test
	void registerHints() {
		RuntimeHints runtimeHints = new RuntimeHints();
		VertexRuntimeHints vertexRuntimeHints = new VertexRuntimeHints();
		vertexRuntimeHints.registerHints(runtimeHints, null);
		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage(VertexAiApi.class);
		for (TypeReference jsonAnnotatedClass : jsonAnnotatedClasses) {
			assertThat(runtimeHints).matches(reflection().onType(jsonAnnotatedClass));
		}
	}

}
