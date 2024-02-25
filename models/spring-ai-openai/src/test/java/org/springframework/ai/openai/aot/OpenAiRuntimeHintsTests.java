package org.springframework.ai.openai.aot;

import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.reflection;

class OpenAiRuntimeHintsTests {

	@Test
	void registerHints() {
		RuntimeHints runtimeHints = new RuntimeHints();
		OpenAiRuntimeHints openAiRuntimeHints = new OpenAiRuntimeHints();
		openAiRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage(OpenAiApi.class);
		for (TypeReference jsonAnnotatedClass : jsonAnnotatedClasses) {
			assertThat(runtimeHints).matches(reflection().onType(jsonAnnotatedClass));
		}
	}

}
