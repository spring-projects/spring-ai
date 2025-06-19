/*
 * Copyright 2024-2025 the original author or authors.
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

package org.springframework.ai.chat.observation.trace;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LangfuseMessageFormatter}.
 *
 * @author tingchuan.li
 */
class LangfuseMessageFormatterTest {

	private static final String TEXT = "Hello World!";

	private static final String ID = "test_id";

	private static final String TYPE = "test_type";

	private static final String NAME = "test_name";

	private static final String ARGUMENTS = "test_arguments";

	private static final String RESPONSE_DATA = "test_response_data";

	private static final MessageFormatter FORMATTER = new LangfuseMessageFormatter();

	@Test
	void systemFormat() {
		Message userMessage = new SystemMessage(TEXT);
		assertThat(FORMATTER.format(userMessage)).isEqualTo("{\"role\":\"system\",\"content\":\"Hello World!\"}");
	}

	@Test
	void userFormat() {
		Message userMessage = new UserMessage(TEXT);
		assertThat(FORMATTER.format(userMessage)).isEqualTo("{\"role\":\"user\",\"content\":\"Hello World!\"}");
	}

	@Test
	void assistantFormat() {
		Message assistantMessage = new AssistantMessage(TEXT);
		assertThat(FORMATTER.format(assistantMessage))
			.isEqualTo("{\"role\":\"assistant\",\"content\":\"Hello World!\"}");
	}

	@Test
	void assistantToolcallFormat() {
		Message assistantMessage = new AssistantMessage("", Map.of(),
				List.of(new AssistantMessage.ToolCall(ID, TYPE, NAME, ARGUMENTS)));
		assertThat(FORMATTER.format(assistantMessage)).isEqualTo(
				"{\"role\":\"assistant\",\"content\":[{\"id\":\"test_id\",\"type\":\"test_type\",\"name\":\"test_name\",\"arguments\":\"test_arguments\"}]}");
	}

	@Test
	void toolResponseFormat() {
		Message toolResponseMessage = new ToolResponseMessage(
				List.of(new ToolResponseMessage.ToolResponse(ID, NAME, RESPONSE_DATA)));
		assertThat(FORMATTER.format(toolResponseMessage)).isEqualTo(
				"{\"role\":\"tool\",\"content\":[{\"id\":\"test_id\",\"name\":\"test_name\",\"responseData\":\"test_response_data\"}]}");
	}

}
