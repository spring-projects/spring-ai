package org.springframework.ai.watsonx.aot;

import org.junit.jupiter.api.Test;
import org.springframework.ai.watsonx.api.WatsonxAIApi;
import org.springframework.ai.watsonx.api.WatsonxAIOptions;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.reflection;

/**
 * @author Pablo Sanchidrian Herrera
 * @author John Jairo Moreno Rojas
 */
public class WatsonxAIRuntimeHintsTest {

	@Test
	void registerHints() {
		RuntimeHints runtimeHints = new RuntimeHints();
		WatsonxAIRuntimeHints watsonxAIRuntimeHintsTest = new WatsonxAIRuntimeHints();
		watsonxAIRuntimeHintsTest.registerHints(runtimeHints, null);

		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage(WatsonxAIApi.class);
		for (TypeReference jsonAnnotatedClass : jsonAnnotatedClasses) {
			assertThat(runtimeHints).matches(reflection().onType(jsonAnnotatedClass));
		}

		jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage(WatsonxAIOptions.class);
		for (TypeReference jsonAnnotatedClass : jsonAnnotatedClasses) {
			assertThat(runtimeHints).matches(reflection().onType(jsonAnnotatedClass));
		}
	}

}
