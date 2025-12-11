/*
 * Copyright 2023-2025 the original author or authors.
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

import org.junit.jupiter.api.Test;
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
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SpringAiCoreRuntimeHints}.
 *
 * @author Hyunjoon Park
 */
class SpringAiCoreRuntimeHintsTests {

	@Test
	void registerHints() {
		RuntimeHints runtimeHints = new RuntimeHints();
		SpringAiCoreRuntimeHints springAiCoreRuntimeHints = new SpringAiCoreRuntimeHints();
		springAiCoreRuntimeHints.registerHints(runtimeHints, null);

		// Verify chat message types are registered
		assertThat(runtimeHints.reflection().typeHints())
			.anySatisfy(typeHint -> assertThat(typeHint.getType()).isEqualTo(TypeReference.of(AbstractMessage.class)));
		assertThat(runtimeHints.reflection().typeHints())
			.anySatisfy(typeHint -> assertThat(typeHint.getType()).isEqualTo(TypeReference.of(AssistantMessage.class)));
		assertThat(runtimeHints.reflection().typeHints())
			.anySatisfy(typeHint -> assertThat(typeHint.getType()).isEqualTo(TypeReference.of(Message.class)));
		assertThat(runtimeHints.reflection().typeHints())
			.anySatisfy(typeHint -> assertThat(typeHint.getType()).isEqualTo(TypeReference.of(MessageType.class)));
		assertThat(runtimeHints.reflection().typeHints())
			.anySatisfy(typeHint -> assertThat(typeHint.getType()).isEqualTo(TypeReference.of(SystemMessage.class)));
		assertThat(runtimeHints.reflection().typeHints()).anySatisfy(
				typeHint -> assertThat(typeHint.getType()).isEqualTo(TypeReference.of(ToolResponseMessage.class)));
		assertThat(runtimeHints.reflection().typeHints())
			.anySatisfy(typeHint -> assertThat(typeHint.getType()).isEqualTo(TypeReference.of(UserMessage.class)));

		// Verify tool types are registered
		assertThat(runtimeHints.reflection().typeHints())
			.anySatisfy(typeHint -> assertThat(typeHint.getType()).isEqualTo(TypeReference.of(ToolCallback.class)));
		assertThat(runtimeHints.reflection().typeHints())
			.anySatisfy(typeHint -> assertThat(typeHint.getType()).isEqualTo(TypeReference.of(ToolDefinition.class)));

		// Verify SLF4J types are registered for Java 22 compatibility
		assertThat(runtimeHints.reflection().typeHints()).anySatisfy(typeHint -> assertThat(typeHint.getType())
			.isEqualTo(TypeReference.of(NOP_FallbackServiceProvider.class)));
		assertThat(runtimeHints.reflection().typeHints()).anySatisfy(typeHint -> assertThat(typeHint.getType())
			.isEqualTo(TypeReference.of(SubstituteServiceProvider.class)));
		assertThat(runtimeHints.reflection().typeHints())
			.anySatisfy(typeHint -> assertThat(typeHint.getType()).isEqualTo(TypeReference.of(LoggerFactory.class)));

		// Verify resources are registered
		assertThat(runtimeHints.resources().resourcePatternHints()).anySatisfy(hint -> assertThat(hint.getIncludes())
			.anyMatch(include -> include.getPattern().contains("embedding-model-dimensions.properties")));
	}

}
