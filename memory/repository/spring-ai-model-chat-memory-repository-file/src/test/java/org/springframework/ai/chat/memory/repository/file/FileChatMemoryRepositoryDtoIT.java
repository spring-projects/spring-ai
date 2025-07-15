/*
 * Copyright 2025-2025 the original author or authors.
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
package org.springframework.ai.chat.memory.repository.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author John Dahle
 */
class FileChatMemoryRepositoryDtoIT {

	private static final String SAMPLE_JSON = """
			[
			  {
			    "kind": "assistant",
			    "text": "Hello from assistant"
			  },
			  {
			    "kind": "user",
			    "text": "Hello from user"
			  },
			  {
			    "kind": "system",
			    "text": "System initialization"
			  }
			]
			""";

	@Test
	void loadSampleMemoryIncludingSystem(@TempDir Path tempDir) throws Exception {
		String conversationId = "sample-convo";
		Path file = tempDir.resolve(conversationId + ".json");
		Files.writeString(file, SAMPLE_JSON);
		ChatMemoryRepository repo = new FileChatMemoryRepository(tempDir, new ObjectMapper());
		List<String> ids = repo.findConversationIds();
		assertThat(ids).containsExactly(conversationId);
		List<Message> messages = repo.findByConversationId(conversationId);
		assertThat(messages).hasSize(3);
		assertThat(messages.get(0)).isInstanceOf(AssistantMessage.class)
			.extracting(Message::getText)
			.isEqualTo("Hello from assistant");
		assertThat(messages.get(1)).isInstanceOf(UserMessage.class)
			.extracting(Message::getText)
			.isEqualTo("Hello from user");
		assertThat(messages.get(2)).isInstanceOf(SystemMessage.class)
			.extracting(Message::getText)
			.isEqualTo("System initialization");
	}

}
