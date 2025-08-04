package org.springframework.ai.qwen.aot;

import org.junit.jupiter.api.Test;
import org.springframework.ai.aot.AiRuntimeHints;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.reflection;

public class QwenRuntimeHintsTests {

	@Test
	void registerHints() {
		RuntimeHints runtimeHints = new RuntimeHints();
		QwenRuntimeHints qwenRuntimeHints = new QwenRuntimeHints();
		qwenRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> qwenModelTypes = AiRuntimeHints.findClassesInPackage(
				com.alibaba.dashscope.Version.class.getPackageName(), (metadataReader, metadataReaderFactory) -> true);
		assertThat(qwenModelTypes.size()).isGreaterThan(100);
		for (TypeReference modelType : qwenModelTypes) {
			assertThat(runtimeHints).matches(reflection().onType(modelType));
		}
	}

}
