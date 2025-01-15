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

package org.springframework.ai.chat.memory.neo4j;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Transaction;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.model.Media;
import org.springframework.ai.model.MediaContent;
import org.springframework.util.MimeType;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.*;

/**
 * Chat memory implementation using Neo4j.
 *
 * @author Enrico Rampazzo
 */
public class Neo4jChatMemory implements ChatMemory {

	private final Neo4jChatMemoryConfig config;

	private final Driver driver;

	public Neo4jChatMemory(Neo4jChatMemoryConfig config) {
		this.config = config;
		this.driver = config.getDriver();
	}

	public static Neo4jChatMemory create(Neo4jChatMemoryConfig config) {
		return new Neo4jChatMemory(config);
	}

	@Override
	public void add(String conversationId, Message message) {
		add(conversationId, List.of(message));
	}

	@Override
	public void add(String conversationId, List<Message> messages) {
		try (Transaction t = driver.session().beginTransaction()) {
			for (Message m : messages) {
				addMessageToTransaction(t, conversationId, m);
			}
			t.commit();
		}
	}

	@Override
	public List<Message> get(String conversationId, int lastN) {
		String statementBuilder = """
				MATCH (s:%s {id:$conversationId})-[r:HAS_MESSAGE]->(m:%s)
				WITH m ORDER BY m.idx DESC LIMIT $lastN
				OPTIONAL MATCH (m)-[:HAS_METADATA]->(metadata:%s)
				OPTIONAL MATCH (m)-[:HAS_MEDIA]->(media:%s) WITH m, metadata, media ORDER BY media.idx ASC
				OPTIONAL MATCH (m)-[:HAS_TOOL_RESPONSE]-(tr:%s) WITH m, metadata, media, tr ORDER BY tr.idx ASC
				OPTIONAL MATCH (m)-[:HAS_TOOL_CALL]->(tc:%s)
				WITH m, metadata, media, tr, tc ORDER BY tc.idx ASC
				RETURN m, metadata, collect(tr) as toolResponses, collect(tc) as toolCalls, collect(media) as medias
				""".formatted(config.getSessionLabel(), config.getMessageLabel(), config.getMetadataLabel(),
				config.getMediaLabel(), config.getToolResponseLabel(), config.getToolCallLabel());
		Result res = this.driver.session()
			.run(statementBuilder, Map.of("conversationId", conversationId, "lastN", lastN));
		return res.list(record -> {
			Map<String, Object> messageMap = record.get("m").asMap();
			String msgType = messageMap.get(MessageAttributes.MESSAGE_TYPE.getValue()).toString();
			Message message = null;
			List<Media> mediaList = List.of();
			if (!record.get("medias").isNull()) {
				mediaList = getMedia(record);
			}
			if (msgType.equals(MessageType.USER.getValue())) {
				message = buildUserMessage(record, messageMap, mediaList);
			}
			if (msgType.equals(MessageType.ASSISTANT.getValue())) {
				message = buildAssistantMessage(record, messageMap, mediaList);
			}
			if (msgType.equals(MessageType.SYSTEM.getValue())) {
				message = new SystemMessage(messageMap.get(MessageAttributes.TEXT_CONTENT.getValue()).toString());
			}
			if (msgType.equals(MessageType.TOOL.getValue())) {
				message = buildToolMessage(record);
			}
			if (message == null) {
				throw new IllegalArgumentException("%s messages are not supported"
					.formatted(record.get(MessageAttributes.MESSAGE_TYPE.getValue()).asString()));
			}
			message.getMetadata().put("messageType", message.getMessageType());
			return message;
		});

	}

	public Neo4jChatMemoryConfig getConfig() {
		return config;
	}

	@Override
	public void clear(String conversationId) {
		String statementBuilder = """
				MATCH (s:%s {id:$conversationId})-[r:HAS_MESSAGE]->(m:%s)
				OPTIONAL MATCH (m)-[:HAS_METADATA]->(metadata:%s)
				OPTIONAL MATCH (m)-[:HAS_MEDIA]->(media:%s)
				OPTIONAL MATCH (m)-[:HAS_TOOL_RESPONSE]-(tr:%s)
				OPTIONAL MATCH (m)-[:HAS_TOOL_CALL]->(tc:%s)
				DETACH DELETE m, metadata, media, tr, tc
				""".formatted(config.getSessionLabel(), config.getMessageLabel(), config.getMetadataLabel(),
				config.getMediaLabel(), config.getToolResponseLabel(), config.getToolCallLabel());
		try (Transaction t = driver.session().beginTransaction()) {
			t.run(statementBuilder, Map.of("conversationId", conversationId));
			t.commit();
		}
	}

	private void addMessageToTransaction(Transaction t, String conversationId, Message message) {
		Map<String, Object> queryParameters = new HashMap<>();
		queryParameters.put("conversationId", conversationId);
		StringBuilder statementBuilder = new StringBuilder();
		statementBuilder.append("""
				MERGE (s:%s {id:$conversationId}) WITH s
				OPTIONAL MATCH (s)-[:HAS_MESSAGE]->(countMsg:%s) WITH coalesce(count(countMsg), 0) as totalMsg, s
				CREATE (s)-[:HAS_MESSAGE]->(msg:%s) SET msg = $messageProperties
				SET msg.idx = totalMsg + 1
				""".formatted(config.getSessionLabel(), config.getMessageLabel(), config.getMessageLabel()));
		Map<String, Object> attributes = new HashMap<>();

		attributes.put(MessageAttributes.MESSAGE_TYPE.getValue(), message.getMessageType().getValue());
		attributes.put(MessageAttributes.TEXT_CONTENT.getValue(), message.getText());
		attributes.put("id", UUID.randomUUID().toString());
		queryParameters.put("messageProperties", attributes);

		if (!Optional.ofNullable(message.getMetadata()).orElse(Map.of()).isEmpty()) {
			statementBuilder.append("""
					WITH msg
					CREATE (metadataNode:%s)
					CREATE (msg)-[:HAS_METADATA]->(metadataNode)
					SET metadataNode = $metadata
					""".formatted(config.getMetadataLabel()));
			Map<String, Object> metadataCopy = new HashMap<>(message.getMetadata());
			metadataCopy.remove("messageType");
			queryParameters.put("metadata", metadataCopy);
		}
		if (message instanceof AssistantMessage assistantMessage) {
			if (assistantMessage.hasToolCalls()) {
				statementBuilder.append("""
						WITH msg
						FOREACH(tc in $toolCalls | CREATE (toolCall:%s) SET toolCall = tc
						CREATE (msg)-[:HAS_TOOL_CALL]->(toolCall))
						""".formatted(config.getToolCallLabel()));
				List<Map<String, Object>> toolCallMaps = new ArrayList<>();
				for (int i = 0; i < assistantMessage.getToolCalls().size(); i++) {
					AssistantMessage.ToolCall tc = assistantMessage.getToolCalls().get(i);
					toolCallMaps
						.add(Map.of(ToolCallAttributes.ID.getValue(), tc.id(), ToolCallAttributes.NAME.getValue(),
								tc.name(), ToolCallAttributes.ARGUMENTS.getValue(), tc.arguments(),
								ToolCallAttributes.TYPE.getValue(), tc.type(), ToolCallAttributes.IDX.getValue(), i));
				}
				queryParameters.put("toolCalls", toolCallMaps);
			}
		}
		if (message instanceof ToolResponseMessage toolResponseMessage) {
			List<ToolResponseMessage.ToolResponse> toolResponses = toolResponseMessage.getResponses();
			List<Map<String, String>> toolResponseMaps = new ArrayList<>();
			for (int i = 0; i < Optional.ofNullable(toolResponses).orElse(List.of()).size(); i++) {
				var toolResponse = toolResponses.get(i);
				Map<String, String> toolResponseMap = Map.of(ToolResponseAttributes.ID.getValue(), toolResponse.id(),
						ToolResponseAttributes.NAME.getValue(), toolResponse.name(),
						ToolResponseAttributes.RESPONSE_DATA.getValue(), toolResponse.responseData(),
						ToolResponseAttributes.IDX.getValue(), Integer.toString(i));
				toolResponseMaps.add(toolResponseMap);
			}
			statementBuilder.append("""
					WITH msg
					FOREACH(tr IN $toolResponses | CREATE (tm:%s)
					SET tm = tr
					MERGE (msg)-[:HAS_TOOL_RESPONSE]->(tm))
					""".formatted(config.getToolResponseLabel()));
			queryParameters.put("toolResponses", toolResponseMaps);
		}
		if (message instanceof MediaContent messageWithMedia && !messageWithMedia.getMedia().isEmpty()) {
			List<Map<String, Object>> mediaNodes = convertMediaToMap(messageWithMedia.getMedia());
			statementBuilder.append("""
					WITH msg
					UNWIND $media AS m
					CREATE (media:%s) SET media = m
					WITH msg, media CREATE (msg)-[:HAS_MEDIA]->(media)
					""".formatted(config.getMediaLabel()));
			queryParameters.put("media", mediaNodes);
		}
		t.run(statementBuilder.toString(), queryParameters);
	}

	private List<Map<String, Object>> convertMediaToMap(List<Media> media) {
		List<Map<String, Object>> mediaMaps = new ArrayList<>();
		for (int i = 0; i < media.size(); i++) {
			Map<String, Object> mediaMap = new HashMap<>();
			Media m = media.get(i);
			mediaMap.put(MediaAttributes.ID.getValue(), m.getId());
			mediaMap.put(MediaAttributes.MIME_TYPE.getValue(), m.getMimeType().toString());
			mediaMap.put(MediaAttributes.NAME.getValue(), m.getName());
			mediaMap.put(MediaAttributes.DATA.getValue(), m.getData());
			mediaMap.put(MediaAttributes.IDX.getValue(), i);
			mediaMaps.add(mediaMap);
		}
		return mediaMaps;
	}

	private Message buildToolMessage(org.neo4j.driver.Record record) {
		Message message;
		message = new ToolResponseMessage(record.get("toolResponses").asList(v -> {
			Map<String, Object> trMap = v.asMap();
			return new ToolResponseMessage.ToolResponse((String) trMap.get(ToolResponseAttributes.ID.getValue()),
					(String) trMap.get(ToolResponseAttributes.NAME.getValue()),
					(String) trMap.get(ToolResponseAttributes.RESPONSE_DATA.getValue()));
		}), record.get("metadata").asMap());
		return message;
	}

	private Message buildAssistantMessage(org.neo4j.driver.Record record, Map<String, Object> messageMap,
			List<Media> mediaList) {
		Message message;
		message = new AssistantMessage(messageMap.get(MessageAttributes.TEXT_CONTENT.getValue()).toString(),
				record.get("metadata").asMap(Map.of()), record.get("toolCalls").asList(v -> {
					var toolCallMap = v.asMap();
					return new AssistantMessage.ToolCall((String) toolCallMap.get("id"),
							(String) toolCallMap.get("type"), (String) toolCallMap.get("name"),
							(String) toolCallMap.get("arguments"));
				}), mediaList);
		return message;
	}

	private Message buildUserMessage(org.neo4j.driver.Record record, Map<String, Object> messageMap,
			List<Media> mediaList) {
		Message message;
		Map<String, Object> metadata = record.get("metadata").asMap();
		message = new UserMessage(messageMap.get(MessageAttributes.TEXT_CONTENT.getValue()).toString(), mediaList,
				metadata);
		return message;
	}

	private List<Media> getMedia(org.neo4j.driver.Record record) {
		List<Media> mediaList;
		mediaList = record.get("medias").asList(v -> {
			Map<String, Object> mediaMap = v.asMap();
			var mediaBuilder = Media.builder()
				.name((String) mediaMap.get(MediaAttributes.NAME.getValue()))
				.id(Optional.ofNullable(mediaMap.get(MediaAttributes.ID.getValue())).map(Object::toString).orElse(null))
				.mimeType(MimeType.valueOf(mediaMap.get(MediaAttributes.MIME_TYPE.getValue()).toString()));
			if (mediaMap.get(MediaAttributes.DATA.getValue()) instanceof String stringData) {
				try {
					mediaBuilder.data(URI.create(stringData).toURL());
				}
				catch (MalformedURLException e) {
					throw new IllegalArgumentException("Media data contains an invalid URL");
				}
			}
			else if (mediaMap.get(MediaAttributes.DATA.getValue()).getClass().isArray()) {
				mediaBuilder.data(mediaMap.get(MediaAttributes.DATA.getValue()));
			}
			return mediaBuilder.build();

		});
		return mediaList;
	}

}
