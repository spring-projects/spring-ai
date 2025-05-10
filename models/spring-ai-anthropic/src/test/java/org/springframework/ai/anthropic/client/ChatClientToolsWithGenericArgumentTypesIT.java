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

package org.springframework.ai.anthropic.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.anthropic.AnthropicTestConfiguration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AnthropicTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class ChatClientToolsWithGenericArgumentTypesIT {

	private static final Logger logger = LoggerFactory.getLogger(ChatClientToolsWithGenericArgumentTypesIT.class);

	public static Map<String, Object> arguments = new ConcurrentHashMap<>();

	public static AtomicLong callCounter = new AtomicLong(0);

	@BeforeEach
	void beforeEach() {
		arguments.clear();
	}

	@Autowired
	ChatModel chatModel;

	@Test
	void toolWithGenericArgumentTypes() {
		// @formatter:off
		String response = ChatClient.create(this.chatModel).prompt()
				.user("Turn light red in the living room and the kitchen. Please group the romms with the same color in a single tool call.")
				.tools(new TestToolProvider())				
				.call()
				.content();
		// @formatter:on

		logger.info("Response: {}", response);

		assertThat(arguments).containsEntry("living room", LightColor.RED);
		assertThat(arguments).containsEntry("kitchen", LightColor.RED);

		assertThat(callCounter.get()).isEqualTo(1);
	}

	record Room(String name) {
	}

	enum LightColor {

		RED, GREEN, BLUE

	}

	public static class TestToolProvider {

		@Tool(description = "Change the lamp color in a room.")
		public void changeRoomLightColor(
				@ToolParam(description = "List of rooms to change the ligth color for") List<Room> rooms,
				@ToolParam(description = "light color to change to") LightColor color) {

			logger.info("Change light color in rooms: {} to color: {}", rooms, color);

			for (Room room : rooms) {
				arguments.put(room.name(), color);
			}
			callCounter.incrementAndGet();
		}

	}

}
