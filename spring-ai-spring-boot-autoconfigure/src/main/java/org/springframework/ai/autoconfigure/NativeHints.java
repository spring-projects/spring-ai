package org.springframework.ai.autoconfigure;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import java.util.Set;

import org.springframework.aot.hint.RuntimeHintsRegistrar;

public class NativeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		hints.proxies().registerJdkProxy(TypeReference.of("com.theokanning.openai.OpenAiApi"));
		for (var className : Set.of("com.theokanning.openai.Usage",
				"com.theokanning.openai.completion.chat.ChatCompletionChoice",
				"com.theokanning.openai.completion.chat.ChatCompletionRequest",
				"com.theokanning.openai.completion.chat.ChatCompletionResult",
				"com.theokanning.openai.completion.chat.ChatMessage"))
			hints.reflection().registerType(TypeReference.of(className), MemberCategory.values());
	}

}