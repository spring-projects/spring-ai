package org.springframework.ai.wenxin.aot;

import org.junit.jupiter.api.Test;
import org.springframework.ai.wenxin.api.WenxinApi;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import java.util.Set;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.reflection;

public class WenxinRuntimeHintsIT {

	@Test
	void registerHints() {
		RuntimeHints runtimeHints = new RuntimeHints();
		WenxinRuntimeHints wenxinRuntimeHints = new WenxinRuntimeHints();
		wenxinRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage(WenxinApi.class);
		for (TypeReference jsonAnnotatedClass : jsonAnnotatedClasses) {
			assertThat(runtimeHints).matches(reflection().onType(jsonAnnotatedClass));
		}
	}

}