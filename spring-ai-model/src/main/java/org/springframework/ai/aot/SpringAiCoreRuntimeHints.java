/*
 * Copyright 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.aot;

import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOP_FallbackServiceProvider;
import org.slf4j.helpers.SubstituteServiceProvider;

import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Content;
import org.springframework.ai.content.MediaContent;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.core.io.ClassPathResource;

public class SpringAiCoreRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {

		var chatTypes = Set.of(AbstractMessage.class, AssistantMessage.class, ToolResponseMessage.class, Message.class,
				ToolCallback.class, ToolDefinition.class, AssistantMessage.ToolCall.class, MessageType.class,
				UserMessage.class, SystemMessage.class, Content.class, MediaContent.class);

		var memberCategories = MemberCategory.values();

		for (var c : chatTypes) {
			hints.reflection().registerType(c, memberCategories);
			var innerClassesFor = AiRuntimeHints.findInnerClassesFor(c);
			for (var cc : innerClassesFor) {
				hints.reflection().registerType(cc, memberCategories);
			}
		}

		for (var r : Set.of("embedding/embedding-model-dimensions.properties")) {
			hints.resources().registerResource(new ClassPathResource(r));
		}

		// Register SLF4J types for Java 22 native compilation compatibility
		var slf4jTypes = Set.of(NOP_FallbackServiceProvider.class, SubstituteServiceProvider.class,
				LoggerFactory.class);
		for (var c : slf4jTypes) {
			hints.reflection()
				.registerType(TypeReference.of(c), MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
						MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.DECLARED_FIELDS);
		}

	}

}
