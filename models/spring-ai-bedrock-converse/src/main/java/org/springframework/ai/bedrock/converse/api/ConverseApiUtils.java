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

package org.springframework.ai.bedrock.converse.api;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.SdkField;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStart;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStartEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetadataEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetrics;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamOutput.EventType;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler.Visitor;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamTrace;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStartEvent;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlockStart;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.ModelOptions;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Amazon Bedrock Converse API utils.
 *
 * @author Wei Jiang
 * @author Christian Tzolov
 * @since 1.0.0
 */
public final class ConverseApiUtils {

	public static final ChatResponse EMPTY_CHAT_RESPONSE = ChatResponse.builder()
		.withGenerations(List.of())
		.withMetadata("empty", true)
		.build();

	private ConverseApiUtils() {

	}

	public static boolean isToolUseStart(ConverseStreamOutput event) {
		if (event == null || event.sdkEventType() == null || event.sdkEventType() != EventType.CONTENT_BLOCK_START) {
			return false;
		}

		return ContentBlockStart.Type.TOOL_USE == ((ContentBlockStartEvent) event).start().type();
	}

	public static boolean isToolUseFinish(ConverseStreamOutput event) {
		if (event == null || event.sdkEventType() == null || event.sdkEventType() != EventType.METADATA) {
			return false;
		}
		return true;
	}

	public static Flux<ChatResponse> toChatResponse(Flux<ConverseStreamOutput> responses) {

		AtomicBoolean isInsideTool = new AtomicBoolean(false);

		return responses.map(event -> {
			if (ConverseApiUtils.isToolUseStart(event)) {
				isInsideTool.set(true);
			}
			return event;
		}).windowUntil(event -> { // Group all chunks belonging to the same function call.
			if (isInsideTool.get() && ConverseApiUtils.isToolUseFinish(event)) {
				isInsideTool.set(false);
				return true;
			}
			return !isInsideTool.get();
		}).concatMapIterable(window -> { // Merging the window chunks into a single chunk.
			Mono<ConverseStreamOutput> monoChunk = window.reduce(new ToolUseAggregationEvent(),
					ConverseApiUtils::mergeToolUseEvents);
			return List.of(monoChunk);
		}).flatMap(mono -> mono).scanWith(() -> new Aggregation(), (lastAggregation, nextEvent) -> {

			// System.out.println(nextEvent);
			if (nextEvent instanceof ToolUseAggregationEvent toolUseAggregationEvent) {

				if (CollectionUtils.isEmpty(toolUseAggregationEvent.toolUseEntries())) {
					return new Aggregation();
				}

				List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();

				for (ToolUseAggregationEvent.ToolUseEntry toolUseEntry : toolUseAggregationEvent.toolUseEntries()) {
					var functionCallId = toolUseEntry.id();
					var functionName = toolUseEntry.name();
					var functionArguments = toolUseEntry.input();
					toolCalls.add(
							new AssistantMessage.ToolCall(functionCallId, "function", functionName, functionArguments));
				}

				AssistantMessage assistantMessage = new AssistantMessage("", Map.of(), toolCalls);
				Generation toolCallGeneration = new Generation(assistantMessage,
						ChatGenerationMetadata.from("tool_use", null));

				var chatResponseMetaData = ChatResponseMetadata.builder()
					.withUsage(toolUseAggregationEvent.usage)
					.build();

				return new Aggregation(
						MetadataAggregation.builder().copy(lastAggregation.metadataAggregation()).build(),
						new ChatResponse(List.of(toolCallGeneration), chatResponseMetaData));

			}
			else if (nextEvent instanceof MessageStartEvent messageStartEvent) {
				var newMeta = MetadataAggregation.builder()
					.copy(lastAggregation.metadataAggregation())
					.withRole(messageStartEvent.role().toString())
					.build();
				return new Aggregation(newMeta, ConverseApiUtils.EMPTY_CHAT_RESPONSE);
			}
			else if (nextEvent instanceof MessageStopEvent messageStopEvent) {
				var newMeta = MetadataAggregation.builder()
					.copy(lastAggregation.metadataAggregation())
					.withStopReason(messageStopEvent.stopReasonAsString())
					.withAdditionalModelResponseFields(messageStopEvent.additionalModelResponseFields())
					.build();
				return new Aggregation(newMeta, ConverseApiUtils.EMPTY_CHAT_RESPONSE);
			}
			else if (nextEvent instanceof ContentBlockStartEvent contentBlockStartEvent) {
				// TODO ToolUse support
				return new Aggregation();
			}
			else if (nextEvent instanceof ContentBlockDeltaEvent contentBlockDeltaEvent) {
				if (contentBlockDeltaEvent.delta().type().equals(ContentBlockDelta.Type.TEXT)) {

					var generation = new Generation(
							new AssistantMessage(contentBlockDeltaEvent.delta().text(), Map.of()),
							ChatGenerationMetadata.from(lastAggregation.metadataAggregation().stopReason(), null));

					return new Aggregation(
							MetadataAggregation.builder().copy(lastAggregation.metadataAggregation()).build(),
							new ChatResponse(List.of(generation)));
				}
				else if (contentBlockDeltaEvent.delta().type().equals(ContentBlockDelta.Type.TOOL_USE)) {
					// TODO ToolUse support
				}
				return new Aggregation();
			}
			else if (nextEvent instanceof ContentBlockStopEvent contentBlockStopEvent) {
				// TODO ToolUse support
				return new Aggregation();
			}
			else if (nextEvent instanceof ConverseStreamMetadataEvent metadataEvent) {
				// return new Aggregation();
				var newMeta = MetadataAggregation.builder()
					.copy(lastAggregation.metadataAggregation())
					.withTokenUsage(metadataEvent.usage())
					.withMetrics(metadataEvent.metrics())
					.withTrace(metadataEvent.trace())
					.build();

				DefaultUsage usage = new DefaultUsage(metadataEvent.usage().inputTokens().longValue(),
						metadataEvent.usage().outputTokens().longValue(),
						metadataEvent.usage().totalTokens().longValue());

				// TODO
				Document modelResponseFields = lastAggregation.metadataAggregation().additionalModelResponseFields();
				ConverseStreamMetrics metrics = metadataEvent.metrics();

				var chatResponseMetaData = ChatResponseMetadata.builder().withUsage(usage).build();

				return new Aggregation(newMeta, new ChatResponse(List.of(), chatResponseMetaData));
			}
			else {
				return new Aggregation();
			}
		})
			// .skip(1)
			.map(aggregation -> aggregation.chatResponse())
			.filter(chatResponse -> chatResponse != ConverseApiUtils.EMPTY_CHAT_RESPONSE);
	}

	public static ConverseStreamOutput mergeToolUseEvents(ConverseStreamOutput previousEvent,
			ConverseStreamOutput event) {

		ToolUseAggregationEvent toolUseEventAggregator = (ToolUseAggregationEvent) previousEvent;

		if (event.sdkEventType() == EventType.CONTENT_BLOCK_START) {

			ContentBlockStartEvent contentBlockStart = (ContentBlockStartEvent) event;

			if (ContentBlockStart.Type.TOOL_USE.equals(contentBlockStart.start().type())) {
				ToolUseBlockStart cbToolUse = contentBlockStart.start().toolUse();

				return toolUseEventAggregator.withIndex(contentBlockStart.contentBlockIndex())
					.withId(cbToolUse.toolUseId())
					.withName(cbToolUse.name())
					.appendPartialJson(""); // CB START always has empty JSON.
			}
		}
		else if (event.sdkEventType() == EventType.CONTENT_BLOCK_DELTA) {
			ContentBlockDeltaEvent contentBlockDelta = (ContentBlockDeltaEvent) event;
			if (ContentBlockDelta.Type.TOOL_USE == contentBlockDelta.delta().type()) {
				return toolUseEventAggregator.appendPartialJson(contentBlockDelta.delta().toolUse().input());
			}
		}
		else if (event.sdkEventType() == EventType.CONTENT_BLOCK_STOP) {
			return toolUseEventAggregator;
		}
		else if (event.sdkEventType() == EventType.MESSAGE_STOP) {
			return toolUseEventAggregator;
		}
		else if (event.sdkEventType() == EventType.METADATA) {
			ConverseStreamMetadataEvent metadataEvent = (ConverseStreamMetadataEvent) event;
			DefaultUsage usage = new DefaultUsage(metadataEvent.usage().inputTokens().longValue(),
					metadataEvent.usage().outputTokens().longValue(), metadataEvent.usage().totalTokens().longValue());
			toolUseEventAggregator.withUsage(usage);
			// TODO
			if (!toolUseEventAggregator.isEmpty()) {
				toolUseEventAggregator.squashIntoContentBlock();
				return toolUseEventAggregator;
			}
		}

		return event;
	}

	@SuppressWarnings("unchecked")
	public static Document getChatOptionsAdditionalModelRequestFields(ChatOptions defaultOptions,
			ModelOptions promptOptions) {
		if (defaultOptions == null && promptOptions == null) {
			return null;
		}

		Map<String, Object> attributes = new HashMap<>();

		if (defaultOptions != null) {
			attributes.putAll(ModelOptionsUtils.objectToMap(defaultOptions));
		}

		if (promptOptions != null) {
			if (promptOptions instanceof ChatOptions runtimeOptions) {
				attributes.putAll(ModelOptionsUtils.objectToMap(runtimeOptions));
			}
			else {
				throw new IllegalArgumentException(
						"Prompt options are not of type ChatOptions:" + promptOptions.getClass().getSimpleName());
			}
		}

		attributes.remove("model");
		attributes.remove("proxyToolCalls");
		attributes.remove("functions");
		attributes.remove("toolContext");
		attributes.remove("functionCallbacks");

		attributes.remove("temperature");
		attributes.remove("topK");
		attributes.remove("stopSequences");
		attributes.remove("maxTokens");
		attributes.remove("topP");

		return convertObjectToDocument(attributes);
	}

	@SuppressWarnings("unchecked")
	public static Document convertObjectToDocument(Object value) {
		if (value == null) {
			return Document.fromNull();
		}
		else if (value instanceof String stringValue) {
			return Document.fromString(stringValue);
		}
		else if (value instanceof Boolean booleanValue) {
			return Document.fromBoolean(booleanValue);
		}
		else if (value instanceof Integer integerValue) {
			return Document.fromNumber(integerValue);
		}
		else if (value instanceof Long longValue) {
			return Document.fromNumber(longValue);
		}
		else if (value instanceof Float floatValue) {
			return Document.fromNumber(floatValue);
		}
		else if (value instanceof Double doubleValue) {
			return Document.fromNumber(doubleValue);
		}
		else if (value instanceof BigDecimal bigDecimalValue) {
			return Document.fromNumber(bigDecimalValue);
		}
		else if (value instanceof BigInteger bigIntegerValue) {
			return Document.fromNumber(bigIntegerValue);
		}
		else if (value instanceof List listValue) {
			return Document.fromList(listValue.stream().map(v -> convertObjectToDocument(v)).toList());
		}
		else if (value instanceof Map mapValue) {
			return convertMapToDocument(mapValue);
		}
		else {
			throw new IllegalArgumentException("Unsupported value type:" + value.getClass().getSimpleName());
		}
	}

	private static Document convertMapToDocument(Map<String, Object> value) {
		Map<String, Document> attr = value.entrySet()
			.stream()
			.collect(Collectors.toMap(e -> e.getKey(), e -> convertObjectToDocument(e.getValue())));

		return Document.fromMap(attr);
	}

	public record Aggregation(MetadataAggregation metadataAggregation, ChatResponse chatResponse) {
		public Aggregation() {
			this(MetadataAggregation.builder().build(), EMPTY_CHAT_RESPONSE);
		}
	}

	/**
	 * Special event used to aggregate multiple tool use events into a single event with
	 * list of aggregated ContentBlockToolUse.
	 */
	public static class ToolUseAggregationEvent implements ConverseStreamOutput {

		private Integer index;

		private String id;

		private String name;

		private String partialJson = "";

		private List<ToolUseEntry> toolUseEntries = new ArrayList<>();

		private DefaultUsage usage;

		public List<ToolUseEntry> toolUseEntries() {
			return this.toolUseEntries;
		}

		public boolean isEmpty() {
			return (this.index == null || this.id == null || this.name == null
					|| !StringUtils.hasText(this.partialJson));
		}

		ToolUseAggregationEvent withIndex(Integer index) {
			this.index = index;
			return this;
		}

		ToolUseAggregationEvent withId(String id) {
			this.id = id;
			return this;
		}

		ToolUseAggregationEvent withName(String name) {
			this.name = name;
			return this;
		}

		ToolUseAggregationEvent withUsage(DefaultUsage usage) {
			this.usage = usage;
			return this;
		}

		ToolUseAggregationEvent appendPartialJson(String partialJson) {
			this.partialJson = this.partialJson + partialJson;
			return this;
		}

		void squashIntoContentBlock() {
			this.toolUseEntries.add(new ToolUseEntry(this.index, this.id, this.name, this.partialJson));
			this.index = null;
			this.id = null;
			this.name = null;
			this.partialJson = "";
			this.usage = null;
		}

		@Override
		public String toString() {
			return "EventToolUseBuilder [index=" + this.index + ", id=" + this.id + ", name=" + this.name
					+ ", partialJson=" + this.partialJson + ", toolUseMap=" + "]";
		}

		@Override
		public List<SdkField<?>> sdkFields() {
			return List.of();
		}

		@Override
		public void accept(Visitor visitor) {
			throw new UnsupportedOperationException();
		}

		public record ToolUseEntry(Integer index, String id, String name, String input) {
		}

	}

	public record MetadataAggregation(String role, String stopReason, Document additionalModelResponseFields,
			TokenUsage tokenUsage, ConverseStreamMetrics metrics, ConverseStreamTrace trace) {

		public static Builder builder() {
			return new Builder();
		}

		public final static class Builder {

			private String role;

			private String stopReason;

			private Document additionalModelResponseFields;

			private TokenUsage tokenUsage;

			private ConverseStreamMetrics metrics;

			private ConverseStreamTrace trace;

			private Builder() {
			}

			public Builder copy(MetadataAggregation metadataAggregation) {
				this.role = metadataAggregation.role;
				this.stopReason = metadataAggregation.stopReason;
				this.additionalModelResponseFields = metadataAggregation.additionalModelResponseFields;
				this.tokenUsage = metadataAggregation.tokenUsage;
				this.metrics = metadataAggregation.metrics;
				this.trace = metadataAggregation.trace;
				return this;
			}

			public Builder withRole(String role) {
				this.role = role;
				return this;
			}

			public Builder withStopReason(String stopReason) {
				this.stopReason = stopReason;
				return this;
			}

			public Builder withAdditionalModelResponseFields(Document additionalModelResponseFields) {
				this.additionalModelResponseFields = additionalModelResponseFields;
				return this;
			}

			public Builder withTokenUsage(TokenUsage tokenUsage) {
				this.tokenUsage = tokenUsage;
				return this;
			}

			public Builder withMetrics(ConverseStreamMetrics metrics) {
				this.metrics = metrics;
				return this;
			}

			public Builder withTrace(ConverseStreamTrace trace) {
				this.trace = trace;
				return this;
			}

			public MetadataAggregation build() {
				return new MetadataAggregation(this.role, this.stopReason, this.additionalModelResponseFields,
						this.tokenUsage, this.metrics, this.trace);
			}

		}
	}

}
