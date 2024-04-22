package org.springframework.ai.zhipuai.aot;

import org.junit.jupiter.api.Test;
import org.springframework.ai.zhipuai.api.ZhipuAiApi;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.reflection;

class ZhipuAiRuntimeHintsTests {

	@Test
	void registerHints() {
		var runtimeHints = new RuntimeHints();
		var zhipuAiRuntimeHints = new ZhipuAiRuntimeHints();
		zhipuAiRuntimeHints.registerHints(runtimeHints, null);

		var jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage(ZhipuAiApi.class);
		for (TypeReference jsonAnnotatedClass : jsonAnnotatedClasses) {
			assertThat(runtimeHints).matches(reflection().onType(jsonAnnotatedClass));
		}
	}

}
