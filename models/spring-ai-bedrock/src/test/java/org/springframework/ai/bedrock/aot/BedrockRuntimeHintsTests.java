package org.springframework.ai.bedrock.aot;

import org.junit.jupiter.api.Test;
import org.springframework.ai.bedrock.anthropic.api.AnthropicChatBedrockApi;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi;
import org.springframework.ai.bedrock.jurassic2.api.Ai21Jurassic2ChatBedrockApi;
import org.springframework.ai.bedrock.llama2.api.Llama2ChatBedrockApi;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi;
import org.springframework.ai.bedrock.titan.api.TitanEmbeddingBedrockApi;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import java.util.List;
import java.util.Set;
import java.util.Arrays;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.reflection;

class BedrockRuntimeHintsTests {

	@Test
	void registerHints() {
		RuntimeHints runtimeHints = new RuntimeHints();
		BedrockRuntimeHints bedrockRuntimeHints = new BedrockRuntimeHints();
		bedrockRuntimeHints.registerHints(runtimeHints, null);

		List<Class> classList = Arrays.asList(Ai21Jurassic2ChatBedrockApi.class, CohereChatBedrockApi.class,
				CohereEmbeddingBedrockApi.class, Llama2ChatBedrockApi.class, TitanChatBedrockApi.class,
				TitanEmbeddingBedrockApi.class, AnthropicChatBedrockApi.class);

		for (Class aClass : classList) {
			Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage(aClass);
			for (TypeReference jsonAnnotatedClass : jsonAnnotatedClasses) {
				assertThat(runtimeHints).matches(reflection().onType(jsonAnnotatedClass));
			}
		}

	}

}