package org.springframework.ai.chat.memory.neo4j;

import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionContext;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.content.Media;
import org.springframework.ai.content.MediaContent;
import org.springframework.util.MimeType;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * An implementation of {@link ChatMemoryRepository} for Neo4J
 *
 * @author Enrico Rampazzo
 * @author Michael J. Simons
 * @since 1.0.0
 */

public final class Neo4jChatMemoryRepository implements ChatMemoryRepository {

	private final Neo4jChatMemoryRepositoryConfig config;

	public Neo4jChatMemoryRepository(Neo4jChatMemoryRepositoryConfig config) {
		this.config = config;
	}

	@Override
	public List<String> findConversationIds() {
		return config.getDriver()
			.executableQuery("MATCH (conversation:$($sessionLabel)) RETURN conversation.id")
			.withParameters(Map.of("sessionLabel", config.getSessionLabel()))
			.execute(Collectors.mapping(r -> r.get("conversation.id").asString(), Collectors.toList()));
	}

	@Override
	public List<Message> findByConversationId(String conversationId) {
		String statement = """
				MATCH (s:$($sessionLabel) {id:$conversationId})-[r:HAS_MESSAGE]->(m:$($messageLabel))
				WITH m
				OPTIONAL MATCH (m)-[:HAS_METADATA]->(metadata:$($metadataLabel))
				OPTIONAL MATCH (m)-[:HAS_MEDIA]->(media:$($mediaLabel)) WITH m, metadata, media ORDER BY media.idx ASC
				OPTIONAL MATCH (m)-[:HAS_TOOL_RESPONSE]-(tr:$($toolResponseLabel)) WITH m, metadata, media, tr ORDER BY tr.idx ASC
				OPTIONAL MATCH (m)-[:HAS_TOOL_CALL]->(tc:$($toolCallLabel))
				WITH m, metadata, media, tr, tc ORDER BY tc.idx ASC
				RETURN m, metadata, collect(tr) as toolResponses, collect(tc) as toolCalls, collect(media) as medias
				ORDER BY m.idx ASC
				""";

		return this.config.getDriver()
			.executableQuery(statement)
			.withParameters(Map.of("conversationId", conversationId, "sessionLabel", this.config.getSessionLabel(),
					"messageLabel", this.config.getMessageLabel(), "metadataLabel", this.config.getMetadataLabel(),
					"mediaLabel", this.config.getMediaLabel(), "toolResponseLabel", this.config.getToolResponseLabel(),
					"toolCallLabel", this.config.getToolCallLabel()))
			.execute(Collectors.mapping(record -> {
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
					SystemMessage.Builder systemMessageBuilder = SystemMessage.builder()
						.text(messageMap.get(MessageAttributes.TEXT_CONTENT.getValue()).toString());
					if (!record.get("metadata").isNull()) {
						Map<String, Object> retrievedMetadata = record.get("metadata").asMap();
						systemMessageBuilder.metadata(retrievedMetadata);
					}
					message = systemMessageBuilder.build();
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
			}, Collectors.toList()));

	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		// First delete existing messages for this conversation
		deleteByConversationId(conversationId);

		// Then add the new messages
		try (Session s = this.config.getDriver().session()) {
			s.executeWriteWithoutResult(tx -> {
				for (Message m : messages) {
					addMessageToTransaction(tx, conversationId, m);
				}
			});
		}
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		// First delete all messages and related nodes
		String deleteMessagesStatement = """
				MATCH (s:%s {id:$conversationId})-[r:HAS_MESSAGE]->(m:%s)
				OPTIONAL MATCH (m)-[:HAS_METADATA]->(metadata:%s)
				OPTIONAL MATCH (m)-[:HAS_MEDIA]->(media:%s)
				OPTIONAL MATCH (m)-[:HAS_TOOL_RESPONSE]-(tr:%s)
				OPTIONAL MATCH (m)-[:HAS_TOOL_CALL]->(tc:%s)
				DETACH DELETE m, metadata, media, tr, tc
				""".formatted(this.config.getSessionLabel(), this.config.getMessageLabel(),
				this.config.getMetadataLabel(), this.config.getMediaLabel(), this.config.getToolResponseLabel(),
				this.config.getToolCallLabel());

		// Then delete the conversation node itself
		String deleteConversationStatement = """
				MATCH (s:%s {id:$conversationId})
				DETACH DELETE s
				""".formatted(this.config.getSessionLabel());

		try (Session s = this.config.getDriver().session()) {
			try (Transaction t = s.beginTransaction()) {
				// First delete messages
				t.run(deleteMessagesStatement, Map.of("conversationId", conversationId));
				// Then delete the conversation node
				t.run(deleteConversationStatement, Map.of("conversationId", conversationId));
				t.commit();
			}
		}
	}

	public Neo4jChatMemoryRepositoryConfig getConfig() {
		return this.config;
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
		message = UserMessage.builder()
			.text(messageMap.get(MessageAttributes.TEXT_CONTENT.getValue()).toString())
			.media(mediaList)
			.metadata(metadata)
			.build();
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
				mediaBuilder.data(URI.create(stringData));
			}
			else if (mediaMap.get(MediaAttributes.DATA.getValue()).getClass().isArray()) {
				mediaBuilder.data(mediaMap.get(MediaAttributes.DATA.getValue()));
			}
			return mediaBuilder.build();

		});
		return mediaList;
	}

	private void addMessageToTransaction(TransactionContext t, String conversationId, Message message) {
		Map<String, Object> queryParameters = new HashMap<>();
		queryParameters.put("conversationId", conversationId);
		StringBuilder statementBuilder = new StringBuilder();
		statementBuilder.append("""
				MERGE (s:$($sessionLabel) {id:$conversationId}) WITH s
				OPTIONAL MATCH (s)-[:HAS_MESSAGE]->(countMsg:$($messageLabel))
				WITH coalesce(count(countMsg), 0) as totalMsg, s
				CREATE (s)-[:HAS_MESSAGE]->(msg:$($messageLabel)) SET msg = $messageProperties
				SET msg.idx = totalMsg + 1
				""");
		Map<String, Object> attributes = new HashMap<>();

		attributes.put(MessageAttributes.MESSAGE_TYPE.getValue(), message.getMessageType().getValue());
		attributes.put(MessageAttributes.TEXT_CONTENT.getValue(), message.getText());
		attributes.put("id", UUID.randomUUID().toString());
		queryParameters.put("messageProperties", attributes);
		queryParameters.put("sessionLabel", this.config.getSessionLabel());
		queryParameters.put("messageLabel", this.config.getMessageLabel());

		if (!Optional.ofNullable(message.getMetadata()).orElse(Map.of()).isEmpty()) {
			statementBuilder.append("""
					WITH msg
					CREATE (metadataNode:$($metadataLabel))
					CREATE (msg)-[:HAS_METADATA]->(metadataNode)
					SET metadataNode = $metadata
					""");
			Map<String, Object> metadataCopy = new HashMap<>(message.getMetadata());
			metadataCopy.remove("messageType");
			queryParameters.put("metadata", metadataCopy);
			queryParameters.put("metadataLabel", this.config.getMetadataLabel());
		}
		if (message instanceof AssistantMessage assistantMessage) {
			if (assistantMessage.hasToolCalls()) {
				statementBuilder.append("""
						WITH msg
						FOREACH(tc in $toolCalls | CREATE (toolCall:$($toolLabel)) SET toolCall = tc
						CREATE (msg)-[:HAS_TOOL_CALL]->(toolCall))
						""");
				queryParameters.put("toolLabel", this.config.getToolCallLabel());
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
					FOREACH(tr IN $toolResponses | CREATE (tm:$($toolResponseLabel))
					SET tm = tr
					MERGE (msg)-[:HAS_TOOL_RESPONSE]->(tm))
					""");
			queryParameters.put("toolResponses", toolResponseMaps);
			queryParameters.put("toolResponseLabel", this.config.getToolResponseLabel());
		}
		if (message instanceof MediaContent messageWithMedia && !messageWithMedia.getMedia().isEmpty()) {
			List<Map<String, Object>> mediaNodes = convertMediaToMap(messageWithMedia.getMedia());
			statementBuilder.append("""
					WITH msg
					UNWIND $media AS m
					CREATE (media:$($mediaLabel)) SET media = m
					WITH msg, media CREATE (msg)-[:HAS_MEDIA]->(media)
					""");
			queryParameters.put("media", mediaNodes);
			queryParameters.put("mediaLabel", this.config.getMediaLabel());
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

}
