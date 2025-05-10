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

package org.springframework.ai.model.chat.memory.repository.neo4j.autoconfigure;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.neo4j.Neo4jChatMemoryRepository;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.ai.chat.memory.neo4j.Neo4jChatMemoryRepositoryConfig;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.MimeType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Mick Semb Wever
 * @author Jihoon Kim
 * @author Enrico Rampazzo
 * @since 1.0.0
 */
@Testcontainers
class Neo4jChatMemoryRepositoryAutoConfigurationIT {

	static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("neo4j");

	@SuppressWarnings({ "rawtypes", "resource" })
	@Container
	static Neo4jContainer neo4jContainer = (Neo4jContainer) new Neo4jContainer(DEFAULT_IMAGE_NAME.withTag("5"))
		.withoutAuthentication()
		.withExposedPorts(7474, 7687);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withConfiguration(
			AutoConfigurations.of(Neo4jChatMemoryRepositoryAutoConfiguration.class, Neo4jAutoConfiguration.class));

	@Test
	void addAndGet() {
		this.contextRunner.withPropertyValues("spring.neo4j.uri=" + neo4jContainer.getBoltUrl()).run(context -> {
			ChatMemoryRepository memory = context.getBean(ChatMemoryRepository.class);

			String sessionId = UUID.randomUUID().toString();
			assertThat(memory.findByConversationId(sessionId)).isEmpty();

			UserMessage userMessage = new UserMessage("test question");

			memory.saveAll(sessionId, List.of(userMessage));
			List<Message> messages = memory.findByConversationId(sessionId);
			assertThat(messages).hasSize(1);
			assertThat(messages.get(0)).usingRecursiveAssertion().isEqualTo(userMessage);

			memory.deleteByConversationId(sessionId);
			assertThat(memory.findByConversationId(sessionId)).isEmpty();

			AssistantMessage assistantMessage = new AssistantMessage("test answer", Map.of(),
					List.of(new AssistantMessage.ToolCall("id", "type", "name", "arguments")));

			memory.saveAll(sessionId, List.of(userMessage, assistantMessage));
			messages = memory.findByConversationId(sessionId);
			assertThat(messages).hasSize(2);
			assertThat(messages.get(0)).isEqualTo(userMessage);

			assertThat(messages.get(1)).isEqualTo(assistantMessage);
			memory.deleteByConversationId(sessionId);
			MimeType textPlain = MimeType.valueOf("text/plain");
			List<Media> media = List.of(
					Media.builder()
						.name("some media")
						.id(UUID.randomUUID().toString())
						.mimeType(textPlain)
						.data("hello".getBytes(StandardCharsets.UTF_8))
						.build(),
					Media.builder().data(URI.create("http://www.google.com")).mimeType(textPlain).build());
			UserMessage userMessageWithMedia = UserMessage.builder().text("Message with media").media(media).build();
			memory.saveAll(sessionId, List.of(userMessageWithMedia));

			messages = memory.findByConversationId(sessionId);
			assertThat(messages.size()).isEqualTo(1);
			assertThat(messages.get(0)).isEqualTo(userMessageWithMedia);
			assertThat(((UserMessage) messages.get(0)).getMedia()).hasSize(2);
			assertThat(((UserMessage) messages.get(0)).getMedia()).usingRecursiveFieldByFieldElementComparator()
				.isEqualTo(media);
			memory.deleteByConversationId(sessionId);
			ToolResponseMessage toolResponseMessage = new ToolResponseMessage(
					List.of(new ToolResponse("id", "name", "responseData"),
							new ToolResponse("id2", "name2", "responseData2")),
					Map.of("id", "id", "metadataKey", "metadata"));
			memory.saveAll(sessionId, List.of(toolResponseMessage));
			messages = memory.findByConversationId(sessionId);
			assertThat(messages.size()).isEqualTo(1);
			assertThat(messages.get(0)).isEqualTo(toolResponseMessage);

			memory.deleteByConversationId(sessionId);
			SystemMessage sm = new SystemMessage("this is a System message");
			memory.saveAll(sessionId, List.of(sm));
			messages = memory.findByConversationId(sessionId);
			assertThat(messages).hasSize(1);
			assertThat(messages.get(0)).usingRecursiveAssertion().isEqualTo(sm);
		});
	}

	@Test
	void setCustomConfiguration() {
		final String sessionLabel = "LabelSession";
		final String toolCallLabel = "LabelToolCall";
		final String metadataLabel = "LabelMetadata";
		final String messageLabel = "LabelMessage";
		final String toolResponseLabel = "LabelToolResponse";
		final String mediaLabel = "LabelMedia";

		final String propertyBase = "spring.ai.chat.memory.neo4j.%s=%s";
		this.contextRunner
			.withPropertyValues("spring.neo4j.uri=" + neo4jContainer.getBoltUrl(),
					propertyBase.formatted("sessionlabel", sessionLabel),
					propertyBase.formatted("toolcallLabel", toolCallLabel),
					propertyBase.formatted("metadatalabel", metadataLabel),
					propertyBase.formatted("messagelabel", messageLabel),
					propertyBase.formatted("toolresponselabel", toolResponseLabel),
					propertyBase.formatted("medialabel", mediaLabel))
			.run(context -> {
				Neo4jChatMemoryRepository chatMemory = context.getBean(Neo4jChatMemoryRepository.class);
				Neo4jChatMemoryRepositoryConfig config = chatMemory.getConfig();
				assertThat(config.getMessageLabel()).isEqualTo(messageLabel);
				assertThat(config.getMediaLabel()).isEqualTo(mediaLabel);
				assertThat(config.getMetadataLabel()).isEqualTo(metadataLabel);
				assertThat(config.getSessionLabel()).isEqualTo(sessionLabel);
				assertThat(config.getToolResponseLabel()).isEqualTo(toolResponseLabel);
				assertThat(config.getToolCallLabel()).isEqualTo(toolCallLabel);
			});
	}

}
