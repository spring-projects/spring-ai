package org.springframework.ai.aot;

import org.springframework.ai.chat.messages.*;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.core.io.ClassPathResource;

import java.util.Set;

public class SpringAiCoreRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {

		var chatTypes = Set.of(AbstractMessage.class, AssistantMessage.class, ChatMessage.class, FunctionMessage.class,
				Message.class, MessageType.class, UserMessage.class, SystemMessage.class);
		for (var c : chatTypes) {
			hints.reflection().registerType(c);
		}

		for (var r : Set.of("antlr4/org/springframework/ai/vectorstore/filter/antlr4/Filters.g4",
				"embedding/embedding-model-dimensions.properties"))
			hints.resources().registerResource(new ClassPathResource(r));

	}

}