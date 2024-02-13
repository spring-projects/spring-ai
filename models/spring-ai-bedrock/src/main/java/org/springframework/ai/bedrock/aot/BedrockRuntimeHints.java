package org.springframework.ai.bedrock.aot;

import org.springframework.ai.bedrock.anthropic.api.AnthropicChatBedrockApi;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi;
import org.springframework.ai.bedrock.jurassic2.api.Ai21Jurassic2ChatBedrockApi;
import org.springframework.ai.bedrock.llama2.api.Llama2ChatBedrockApi;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi;
import org.springframework.ai.bedrock.titan.api.TitanEmbeddingBedrockApi;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClasses;

/**
 * The BedrockRuntimeHints class is responsible for registering runtime hints for Bedrock
 * AI API classes.
 *
 * @author Josh Long
 * @author Christian Tzolov
 * @author Mark Pollack
 */
public class BedrockRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		var mcs = MemberCategory.values();
		for (var tr : findJsonAnnotatedClasses(Ai21Jurassic2ChatBedrockApi.class))
			hints.reflection().registerType(tr, mcs);
		for (var tr : findJsonAnnotatedClasses(CohereChatBedrockApi.class))
			hints.reflection().registerType(tr, mcs);
		for (var tr : findJsonAnnotatedClasses(CohereEmbeddingBedrockApi.class))
			hints.reflection().registerType(tr, mcs);
		for (var tr : findJsonAnnotatedClasses(Llama2ChatBedrockApi.class))
			hints.reflection().registerType(tr, mcs);
		for (var tr : findJsonAnnotatedClasses(TitanChatBedrockApi.class))
			hints.reflection().registerType(tr, mcs);
		for (var tr : findJsonAnnotatedClasses(TitanEmbeddingBedrockApi.class))
			hints.reflection().registerType(tr, mcs);
		for (var tr : findJsonAnnotatedClasses(AnthropicChatBedrockApi.class))
			hints.reflection().registerType(tr, mcs);
	}

}
