/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.openai.responses;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.openai.core.JsonField;
import com.openai.core.JsonValue;
import com.openai.core.ObjectMappers;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseError;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputContent;
import com.openai.models.responses.ResponseInputFile;
import com.openai.models.responses.ResponseInputImage;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseInputText;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.ResponseReasoningItem;
import com.openai.models.responses.ResponseStatus;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.part.MediaPart;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.OpaquePayload;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolCallPart;
import org.springframework.ai.chat.messages.part.ToolResultPart;
import org.springframework.ai.chat.messages.part.UnknownPart;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

/**
 * Converts between Spring AI's {@link Message} list and the typed items a Responses
 * request and response are built from.
 * <p>
 * A Responses reply is not one message but an ordered list of typed items - a reasoning
 * item, then the function calls it produced, then a text message. That list maps onto the
 * assistant message's {@link MessagePart}s one-to-one, in order, so the parts list is a
 * faithful mirror of the transcript and replay is order-correct by construction. The
 * mapping is:
 * <table border="1">
 * <caption>Output item to message part</caption>
 * <tr>
 * <th>Item</th>
 * <th>Part</th>
 * </tr>
 * <tr>
 * <td>{@code reasoning}</td>
 * <td>{@link ReasoningPart}, the encrypted content in its {@link ReasoningPart#payload()
 * payload}</td>
 * </tr>
 * <tr>
 * <td>{@code message}</td>
 * <td>{@link TextPart}</td>
 * </tr>
 * <tr>
 * <td>{@code function_call}</td>
 * <td>{@link ToolCallPart}</td>
 * </tr>
 * <tr>
 * <td>{@code image_generation_call} with a result</td>
 * <td>{@link MediaPart}</td>
 * </tr>
 * <tr>
 * <td>anything else</td>
 * <td>{@link UnknownPart}, holding the item verbatim</td>
 * </tr>
 * </table>
 * <p>
 * Three rules carry the correctness of the whole integration:
 * <ul>
 * <li>An assistant turn is replayed as the items OpenAI produced, in the original order,
 * so that a reasoning item still precedes the function calls it produced and its
 * encrypted content survives into the next round. Each part carries what its item cannot
 * be rebuilt without: the encrypted reasoning blob, the item id, the item status and the
 * message {@code phase}.
 * <li>Only {@code function_call} items become {@link ToolCall}s. Tools OpenAI ran itself
 * - web search, code interpreter, MCP - must not, or {@code ToolCallingAdvisor} would
 * look for a local callback named {@code web_search}, fail to find one, and abort the
 * turn. They become {@link UnknownPart}s instead, which replay verbatim through
 * schema-agnostic JSON, so an item type this SDK version does not know still round-trips.
 * <li>Reasoning is replayed only when it carries an OpenAI payload. The Responses API
 * does not accept unsigned reasoning, so a reasoning part from another provider, or one
 * whose payload was dropped on the way through, is skipped rather than sent and rejected.
 * </ul>
 * Two things are deliberately not replayed: citation annotations on a prior assistant
 * text turn, which the application reads from the generation metadata and the model has
 * no use for, and generated media, for which no input item exists.
 *
 * @author Dimitar Proynov
 * @since 2.1.0
 */
final class ResponsesItemMapper {

	/**
	 * What separates the reasoning summaries of two different items in the flattened
	 * {@link OpenAiResponsesMetadata#REASONING_CONTENT} view. Shared with
	 * {@link ResponsesStreamAssembler}, which accumulates the same value from deltas and
	 * has to agree with the non-streaming path.
	 */
	static final String REASONING_ITEM_SEPARATOR = "\n";

	private static final MimeType IMAGE_WEBP = MimeType.valueOf("image/webp");

	private static final Log logger = LogFactory.getLog(ResponsesItemMapper.class);

	/**
	 * The SDK's own mapper: the Responses types use custom Jackson 2 serializers, and it
	 * is configured to keep unknown fields rather than drop them.
	 */
	private static final JsonMapper jsonMapper = ObjectMappers.jsonMapper();

	private ResponsesItemMapper() {
	}

	// ---------- outbound: Prompt -> request ----------

	static Input toInput(List<Message> messages) {
		String instructions = null;
		List<ResponseInputItem> items = new ArrayList<>();
		for (Message message : messages) {
			switch (message.getMessageType()) {
				case SYSTEM -> {
					// The first system message becomes the top-level instructions field;
					// any further one becomes a developer item, so none is ever dropped.
					if (instructions == null) {
						instructions = message.getText();
					}
					else if (StringUtils.hasText(message.getText())) {
						items.add(developerItem(message.getText()));
					}
				}
				case USER -> items.add(userItem((UserMessage) message));
				case ASSISTANT -> items.addAll(assistantItems((AssistantMessage) message));
				case TOOL -> items.addAll(toolOutputItems((ToolResponseMessage) message));
				default -> throw new IllegalArgumentException("Unsupported message type: " + message.getMessageType());
			}
		}
		return new Input(instructions, items);
	}

	private static ResponseInputItem developerItem(String text) {
		return ResponseInputItem
			.ofEasyInputMessage(EasyInputMessage.builder().role(EasyInputMessage.Role.DEVELOPER).content(text).build());
	}

	private static ResponseInputItem userItem(UserMessage message) {
		String text = message.getText();
		if (CollectionUtils.isEmpty(message.getMedia())) {
			return ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
				.role(EasyInputMessage.Role.USER)
				.content(text != null ? text : "")
				.build());
		}

		List<ResponseInputContent> parts = new ArrayList<>();
		if (StringUtils.hasText(text)) {
			parts.add(ResponseInputContent.ofInputText(ResponseInputText.builder().text(text).build()));
		}
		message.getMedia().forEach(media -> parts.add(inputContent(media)));
		return ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
			.role(EasyInputMessage.Role.USER)
			.contentOfResponseInputMessageContentList(parts)
			.build());
	}

	private static ResponseInputContent inputContent(Media media) {
		String mimeType = media.getMimeType().toString();
		if (mimeType.startsWith("image/")) {
			return ResponseInputContent.ofInputImage(ResponseInputImage.builder()
				.detail(ResponseInputImage.Detail.AUTO)
				.imageUrl(urlOrDataUri(media))
				.build());
		}
		if (mimeType.startsWith("audio/")) {
			throw new IllegalArgumentException(
					"Audio input is not supported by the OpenAI Responses API. Use OpenAiChatModel for audio input.");
		}
		return ResponseInputContent
			.ofInputFile(ResponseInputFile.builder().fileData(urlOrDataUri(media)).filename(filename(media)).build());
	}

	/**
	 * OpenAI infers the type of inline file from the extension of the filename that
	 * accompanies it, and {@link Media} generates a default name without one -
	 * {@code media-pdf-<uuid>} - whenever the caller did not set one explicitly. So the
	 * extension is derived from the MIME subtype unless the name already carries it.
	 */
	private static String filename(Media media) {
		String extension = "." + media.getMimeType().getSubtype();
		String name = media.getName();
		if (!StringUtils.hasText(name)) {
			return "file" + extension;
		}
		return name.endsWith(extension) ? name : name + extension;
	}

	private static String urlOrDataUri(Media media) {
		Object data = media.getData();
		if (data instanceof byte[] bytes) {
			return "data:" + media.getMimeType() + ";base64," + Base64.getEncoder().encodeToString(bytes);
		}
		// Media stores a URL as a URI, but its builder turns some of them into Strings on
		// the way through, so both have to be accepted here.
		if (data instanceof URI || data instanceof String) {
			return data.toString();
		}
		throw new IllegalArgumentException("Unsupported media data type: " + data.getClass().getSimpleName());
	}

	/**
	 * The replay rule: one input item per part, in part order, rebuilt from what the part
	 * carries. Dispatch is an {@code instanceof} chain ending in a throw, so a part type
	 * added to the sealed hierarchy later cannot be dropped silently.
	 */
	private static List<ResponseInputItem> assistantItems(AssistantMessage message) {
		List<ResponseInputItem> items = new ArrayList<>();
		for (MessagePart part : message.getParts()) {
			if (part instanceof TextPart textPart) {
				// An assistant turn of tool calls only ends in an empty text part, which
				// has no item to replay.
				if (StringUtils.hasText(textPart.text())) {
					items.add(assistantMessageItem(textPart));
				}
			}
			else if (part instanceof ReasoningPart reasoningPart) {
				reasoningItem(reasoningPart).ifPresent(items::add);
			}
			else if (part instanceof ToolCallPart toolCallPart) {
				items.add(functionCallItem(toolCallPart));
			}
			else if (part instanceof UnknownPart unknownPart) {
				unknownItem(unknownPart).ifPresent(items::add);
			}
			else if (part instanceof MediaPart) {
				// Media on an assistant turn is something the model generated, from the
				// image generation tool. There is no input item that replays a generated
				// image, and the model does not need its own output handed back.
				logger.debug("Not replaying generated media on an assistant turn");
			}
			else if (part instanceof ToolResultPart) {
				// Reachable from application code: AssistantMessage.Builder.part() is
				// public, so this rejects a caller-supplied argument rather than reporting
				// a broken invariant of this class.
				throw new IllegalArgumentException(
						"Tool results belong in a tool response message, not an assistant message");
			}
			else {
				throw new IllegalStateException("Unhandled message part type: " + part.getClass().getName());
			}
		}
		return items;
	}

	/**
	 * Rebuild the {@code message} item this part was mapped from, or synthesize an
	 * assistant message when the part did not come from this API - a plain
	 * {@link AssistantMessage}, or a history another provider produced.
	 */
	private static ResponseInputItem assistantMessageItem(TextPart part) {
		Map<String, String> attributes = part.attributes();
		String itemId = attributes.get(OpenAiResponsesMetadata.ITEM_ID_ATTRIBUTE);
		if (itemId == null) {
			return ResponseInputItem.ofEasyInputMessage(
					EasyInputMessage.builder().role(EasyInputMessage.Role.ASSISTANT).content(part.text()).build());
		}
		String status = Objects.requireNonNullElse(attributes.get(OpenAiResponsesMetadata.ITEM_STATUS_ATTRIBUTE),
				"completed");
		ResponseOutputMessage.Builder item = ResponseOutputMessage.builder()
			.id(itemId)
			// Annotations are not replayed: they are the model's own citations, which it
			// does not need back, and the application reads them from the generation
			// metadata.
			.addContent(ResponseOutputText.builder().text(part.text()).annotations(List.of()).build())
			.status(ResponseOutputMessage.Status.of(status));
		String phase = attributes.get(OpenAiResponsesMetadata.PHASE_ATTRIBUTE);
		if (phase != null) {
			// GPT-5.5 and later mark an intermediate update apart from a final answer; a
			// turn replayed without it can be mistaken for a final answer.
			item.phase(ResponseOutputMessage.Phase.of(phase));
		}
		return ResponseInputItem.ofResponseOutputMessage(item.build());
	}

	/**
	 * Rebuild the {@code reasoning} item from the part's encrypted content, item id and
	 * summary.
	 * <p>
	 * A part without an OpenAI payload is skipped: the Responses API does not accept
	 * reasoning without its encrypted content, so replaying reasoning produced by another
	 * provider, or reasoning whose payload was lost on the way through a component that
	 * does not preserve it, would fail the request rather than merely degrade it.
	 */
	private static Optional<ResponseInputItem> reasoningItem(ReasoningPart part) {
		OpaquePayload payload = part.payload();
		if (payload == null || !part.replayableTo(OpenAiResponsesMetadata.PROVIDER)) {
			logger.debug("Skipping a reasoning part from provider " + (payload == null ? "none" : payload.provider())
					+ ": the OpenAI Responses API does not accept reasoning without its encrypted content. "
					+ "Reasoning continuity is lost for this turn.");
			return Optional.empty();
		}
		String itemId = part.attributes().get(OpenAiResponsesMetadata.ITEM_ID_ATTRIBUTE);
		if (itemId == null) {
			logger.debug("Skipping a reasoning part without an item id, which a reasoning item cannot be rebuilt "
					+ "without. Reasoning continuity is lost for this turn.");
			return Optional.empty();
		}
		ResponseReasoningItem.Builder item = ResponseReasoningItem.builder()
			.id(itemId)
			.encryptedContent(payload.data())
			.summary(summaryOf(part));
		String text = part.text();
		if (text != null) {
			item.content(List.of(ResponseReasoningItem.Content.builder().text(text).build()));
		}
		String status = part.attributes().get(OpenAiResponsesMetadata.ITEM_STATUS_ATTRIBUTE);
		if (status != null) {
			item.status(ResponseReasoningItem.Status.of(status));
		}
		return Optional.of(ResponseInputItem.ofReasoning(item.build()));
	}

	private static List<ResponseReasoningItem.Summary> summaryOf(ReasoningPart part) {
		String summary = part.summary();
		return summary != null ? List.of(ResponseReasoningItem.Summary.builder().text(summary).build()) : List.of();
	}

	private static ResponseInputItem functionCallItem(ToolCallPart part) {
		ToolCall toolCall = part.toolCall();
		ResponseFunctionToolCall.Builder item = ResponseFunctionToolCall.builder()
			// ToolCall.id carries the call_id, which is what a result references
			.callId(toolCall.id())
			.name(toolCall.name())
			.arguments(toolCall.arguments());
		String itemId = part.attributes().get(OpenAiResponsesMetadata.ITEM_ID_ATTRIBUTE);
		if (itemId != null) {
			item.id(itemId);
		}
		String status = part.attributes().get(OpenAiResponsesMetadata.ITEM_STATUS_ATTRIBUTE);
		if (status != null) {
			item.status(ResponseFunctionToolCall.Status.of(status));
		}
		return ResponseInputItem.ofFunctionCall(item.build());
	}

	/**
	 * Replay an item this mapper does not model - a tool OpenAI ran itself, or an item
	 * type this SDK version does not know - from the JSON it arrived as. Conversion goes
	 * through schema-agnostic JSON, so an unknown item type still round-trips.
	 */
	private static Optional<ResponseInputItem> unknownItem(UnknownPart part) {
		if (!OpenAiResponsesMetadata.PROVIDER.equals(part.provider())) {
			logger.debug("Skipping an unknown part of kind " + part.kind() + " produced by " + part.provider());
			return Optional.empty();
		}
		try {
			JsonValue item = jsonMapper.readValue(part.rawJson(), JsonValue.class);
			return Optional.ofNullable(item.convert(ResponseInputItem.class));
		}
		catch (Exception ex) {
			// Better a turn missing one item than a failed turn.
			logger.warn("Could not replay the OpenAI Responses item of kind " + part.kind() + "; dropping it", ex);
			return Optional.empty();
		}
	}

	private static List<ResponseInputItem> toolOutputItems(ToolResponseMessage message) {
		return message.getResponses()
			.stream()
			// ToolResponse.id is echoed from ToolCall.id by DefaultToolCallingManager, so
			// it is the call_id the model expects back
			.map(response -> ResponseInputItem.ofFunctionCallOutput(ResponseInputItem.FunctionCallOutput.builder()
				.callId(response.id())
				.output(response.responseData())
				.build()))
			.toList();
	}

	// ---------- inbound: Response -> ChatResponse ----------

	static Generation toGeneration(Response response) {
		return toGeneration(response, response.output());
	}

	/**
	 * Map a response onto a {@link Generation}, deriving every value - parts, finish
	 * reason and metadata - from {@code outputItems} rather than from
	 * {@link Response#output()}.
	 * <p>
	 * The streaming path needs this: a terminal event normally repeats the whole output,
	 * but when one does not, the transcript collected from the stream has to stand in for
	 * it consistently. Taking the transcript for some values and the response for others
	 * is what produces a turn that reports {@code STOP} while carrying tool calls.
	 */
	static Generation toGeneration(Response response, List<ResponseOutputItem> outputItems) {
		List<MessagePart> parts = outputItems.stream().map(ResponsesItemMapper::toPart).toList();
		return new Generation(AssistantMessage.builder().parts(parts).build(),
				toGenerationMetadata(response, outputItems));
	}

	/**
	 * The generation metadata of a turn, derived from {@code outputItems}.
	 */
	static ChatGenerationMetadata toGenerationMetadata(Response response, List<ResponseOutputItem> outputItems) {
		return toGenerationMetadata(response, outputItems, null, null);
	}

	/**
	 * The generation metadata of a turn.
	 * <p>
	 * {@code streamedReasoning} and {@code streamedRefusal} are what a stream delivered
	 * for the two fields the terminal response object does not always repeat; {@code null}
	 * means "derive it from the items", which is what the non-streaming path does.
	 */
	static ChatGenerationMetadata toGenerationMetadata(Response response, List<ResponseOutputItem> outputItems,
			@Nullable String streamedReasoning, @Nullable String streamedRefusal) {
		ReportedValues reported = reportedValues(outputItems);
		String status = response.status().map(ResponseStatus::asString).orElse("completed");

		ChatGenerationMetadata.Builder metadata = ChatGenerationMetadata.builder()
			.finishReason(finishReason(response, status, reported.hasToolCalls()))
			.metadata(OpenAiResponsesMetadata.STATUS, status)
			.metadata(OpenAiResponsesMetadata.REFUSAL, Objects.requireNonNullElse(streamedRefusal, reported.refusal()))
			.metadata(OpenAiResponsesMetadata.ANNOTATIONS, reported.annotations())
			.metadata(OpenAiResponsesMetadata.REASONING_CONTENT,
					Objects.requireNonNullElse(streamedReasoning, reported.reasoning()))
			.metadata(OpenAiResponsesMetadata.HOSTED_TOOL_CALLS, reported.hostedToolCalls());
		response.incompleteDetails()
			.flatMap(Response.IncompleteDetails::reason)
			.ifPresent(reason -> metadata.metadata(OpenAiResponsesMetadata.INCOMPLETE_REASON, reason.asString()));

		return metadata.build();
	}

	/**
	 * What a turn reports beyond its {@link MessagePart}s, read off the output items: a
	 * flattened view of several items, or a description of activity inside the request.
	 * Every field ends up on the generation metadata, {@code hasToolCalls} by way of the
	 * finish reason.
	 *
	 * @param refusal the concatenated refusal text, or an empty string
	 * @param reasoning the reasoning summaries, one item per line, or an empty string
	 * @param annotations the citations attached to the generated text
	 * @param hostedToolCalls a {@code {type, id, status}} map per server-executed tool
	 * @param hasToolCalls whether any item was a {@code function_call}
	 */
	private record ReportedValues(String refusal, String reasoning, List<Map<String, Object>> annotations,
			List<Map<String, Object>> hostedToolCalls, boolean hasToolCalls) {
	}

	private static ReportedValues reportedValues(List<ResponseOutputItem> outputItems) {
		StringBuilder refusal = new StringBuilder();
		StringBuilder reasoning = new StringBuilder();
		List<Map<String, Object>> annotations = new ArrayList<>();
		List<Map<String, Object>> hostedToolCalls = new ArrayList<>();
		boolean hasToolCalls = false;

		for (ResponseOutputItem item : outputItems) {
			Optional<ResponseOutputMessage> message = item.message();
			Optional<ResponseReasoningItem> reasoningItem = item.reasoning();

			if (message.isPresent()) {
				for (ResponseOutputMessage.Content content : message.get().content()) {
					content.outputText()
						.ifPresent(outputText -> outputText.annotations()
							.forEach(annotation -> annotations.add(toMap(annotation))));
					content.refusal().ifPresent(value -> refusal.append(value.refusal()));
				}
			}
			else if (reasoningItem.isPresent()) {
				String summary = summaryText(reasoningItem.get());
				if (summary != null) {
					// One line per reasoning item, matching what the streaming path
					// accumulates across items
					reasoning.append(reasoning.isEmpty() ? "" : REASONING_ITEM_SEPARATOR).append(summary);
				}
			}
			else if (item.functionCall().isPresent()) {
				hasToolCalls = true;
			}
			else {
				// Anything else is a tool OpenAI already executed, or an item type this
				// SDK version does not model. Never a ToolCall.
				hostedToolCalls.add(summarize(item));
			}
		}
		return new ReportedValues(refusal.toString(), reasoning.toString(), annotations, hostedToolCalls, hasToolCalls);
	}

	/**
	 * Map one output item onto its part. Shared with the streaming path, which maps items
	 * one at a time as they complete.
	 */
	static MessagePart toPart(ResponseOutputItem item) {
		Optional<ResponseOutputMessage> message = item.message();
		if (message.isPresent()) {
			return textPart(message.get());
		}
		Optional<ResponseReasoningItem> reasoningItem = item.reasoning();
		if (reasoningItem.isPresent()) {
			return reasoningPart(reasoningItem.get());
		}
		Optional<ResponseFunctionToolCall> functionCall = item.functionCall();
		if (functionCall.isPresent()) {
			return toolCallPart(functionCall.get());
		}
		return hostedToolPart(item);
	}

	/**
	 * The text of a {@code message} item, with what the item cannot be rebuilt without in
	 * its attributes. Several {@code output_text} blocks in one item are joined, so that
	 * one part always corresponds to one item.
	 */
	static TextPart textPart(ResponseOutputMessage message) {
		StringBuilder text = new StringBuilder();
		message.content().forEach(content -> content.outputText().ifPresent(value -> text.append(value.text())));
		Map<String, String> attributes = new LinkedHashMap<>();
		attributes.put(OpenAiResponsesMetadata.ITEM_ID_ATTRIBUTE, message.id());
		attributes.put(OpenAiResponsesMetadata.ITEM_STATUS_ATTRIBUTE, message.status().asString());
		message.phase().ifPresent(phase -> attributes.put(OpenAiResponsesMetadata.PHASE_ATTRIBUTE, phase.asString()));
		return new TextPart(text.toString(), null, attributes);
	}

	/**
	 * A {@code reasoning} item: the encrypted blob in the payload, the summary and any
	 * reasoning text for display, the item id in the attributes.
	 */
	static ReasoningPart reasoningPart(ResponseReasoningItem item) {
		String text = join(item.content().orElse(List.of()).stream().map(ResponseReasoningItem.Content::text));
		String summary = summaryText(item);
		Map<String, String> attributes = new LinkedHashMap<>();
		attributes.put(OpenAiResponsesMetadata.ITEM_ID_ATTRIBUTE, item.id());
		item.status()
			.ifPresent(status -> attributes.put(OpenAiResponsesMetadata.ITEM_STATUS_ATTRIBUTE, status.asString()));
		OpaquePayload payload = item.encryptedContent()
			.map(encrypted -> new OpaquePayload(OpenAiResponsesMetadata.PROVIDER,
					OpenAiResponsesMetadata.ENCRYPTED_CONTENT_KIND, encrypted))
			.orElse(null);
		return new ReasoningPart(text, summary, payload, attributes);
	}

	static ToolCallPart toolCallPart(ResponseFunctionToolCall call) {
		Map<String, String> attributes = new LinkedHashMap<>();
		// The item id, which is the transcript position, not the call_id the ToolCall
		// carries
		call.id().ifPresent(id -> attributes.put(OpenAiResponsesMetadata.ITEM_ID_ATTRIBUTE, id));
		call.status()
			.ifPresent(status -> attributes.put(OpenAiResponsesMetadata.ITEM_STATUS_ATTRIBUTE, status.asString()));
		return new ToolCallPart(new ToolCall(call.callId(), "function", call.name(), call.arguments()), null,
				attributes);
	}

	/**
	 * A tool OpenAI executed inside the request, or an item type this SDK version does
	 * not model, kept verbatim as an {@link UnknownPart} so that it still replays.
	 * <p>
	 * Image generation is the one hosted tool whose result the application wants back, so
	 * a call that produced an image becomes a {@link MediaPart} instead. That part is not
	 * replayed: a generated image is output rather than context.
	 */
	static MessagePart hostedToolPart(ResponseOutputItem item) {
		return item.imageGenerationCall()
			.flatMap(call -> call.result().map(result -> toImageMedia(call, result)))
			.<MessagePart>map(MediaPart::of)
			.orElseGet(() -> new UnknownPart(OpenAiResponsesMetadata.PROVIDER, itemType(item), toJson(item), null,
					Map.of()));
	}

	/**
	 * The summary entries of one {@code reasoning} item, concatenated without a separator
	 * the way their deltas arrive on the stream, or {@code null} when the item has none.
	 */
	private static @Nullable String summaryText(ResponseReasoningItem item) {
		return join(item.summary().stream().map(ResponseReasoningItem.Summary::text));
	}

	private static @Nullable String join(Stream<String> texts) {
		List<String> values = texts.toList();
		return values.isEmpty() ? null : String.join("", values);
	}

	/**
	 * Map the response lifecycle onto the finish reasons the rest of the framework
	 * understands. A {@code failed} response never reaches here: the model raises
	 * instead.
	 */
	static String finishReason(Response response, String status, boolean hasToolCalls) {
		if ("incomplete".equals(status)) {
			String reason = response.incompleteDetails()
				.flatMap(Response.IncompleteDetails::reason)
				.map(Response.IncompleteDetails.Reason::asString)
				.orElse("");
			return switch (reason) {
				case "max_output_tokens" -> "LENGTH";
				case "content_filter" -> "CONTENT_FILTER";
				default -> "INCOMPLETE";
			};
		}
		if ("completed".equals(status)) {
			return hasToolCalls ? "TOOL_CALLS" : "STOP";
		}
		return status.toUpperCase(Locale.ROOT);
	}

	private static Media toImageMedia(ResponseOutputItem.ImageGenerationCall call, String base64Result) {
		return Media.builder()
			.mimeType(imageMimeType(call))
			.data(new ByteArrayResource(Base64.getDecoder().decode(base64Result)))
			.id(call.id())
			.build();
	}

	/**
	 * The MIME type of a generated image, from the {@code output_format} the item echoes
	 * back. The SDK does not type that field, so it is read from the item's additional
	 * properties; PNG is both the API default and the fallback for a format this method
	 * does not know.
	 */
	private static MimeType imageMimeType(ResponseOutputItem.ImageGenerationCall call) {
		JsonValue outputFormat = call._additionalProperties().get("output_format");
		String format = outputFormat != null ? fields(outputFormat).asString().orElse("") : "";
		return switch (format) {
			case "jpeg", "jpg" -> MimeTypeUtils.IMAGE_JPEG;
			case "webp" -> IMAGE_WEBP;
			default -> MimeTypeUtils.IMAGE_PNG;
		};
	}

	static String toJson(Object item) {
		try {
			return jsonMapper.writeValueAsString(item);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Could not serialize the OpenAI Responses item", ex);
		}
	}

	/**
	 * The item's {@code type} discriminator, read generically so an item type this SDK
	 * version does not model still reports its own name.
	 */
	private static String itemType(ResponseOutputItem item) {
		JsonValue type = fields(item).asObject().orElse(Map.of()).get("type");
		return type != null ? fields(type).asString().orElse("unknown") : "unknown";
	}

	/**
	 * Summarize an item generically, so an item type this SDK version does not model
	 * still shows up in metadata.
	 */
	private static Map<String, Object> summarize(ResponseOutputItem item) {
		Map<String, JsonValue> fields = fields(item).asObject().orElse(Map.of());
		Map<String, Object> summary = new LinkedHashMap<>();
		for (String field : List.of("type", "id", "status")) {
			JsonValue value = fields.get(field);
			if (value != null) {
				fields(value).asString().ifPresent(text -> summary.put(field, text));
			}
		}
		return summary;
	}

	private static Map<String, Object> toMap(ResponseOutputText.Annotation annotation) {
		Map<String, Object> result = new LinkedHashMap<>();
		fields(annotation).asObject()
			.orElse(Map.of())
			.forEach((key, value) -> result.put(key, jsonMapper.convertValue(value, Object.class)));
		return result;
	}

	/**
	 * {@code JsonValue} extends a raw {@code JsonField}, which erases the generics of
	 * every inherited accessor. Going through a wildcard-typed reference keeps them.
	 */
	private static JsonField<?> fields(Object sdkValue) {
		return JsonValue.from(sdkValue);
	}

	// ---------- inbound: response metadata ----------

	static Usage toUsage(Response response) {
		return response.usage().<Usage>map(usage -> {
			// Read through the raw fields rather than the typed accessors, all the way up
			// to the details object itself: every typed accessor here is a required field
			// that throws when absent, and an OpenAI-compatible backend may omit the whole
			// input_tokens_details object rather than just a token-details field of it.
			var details = usage._inputTokensDetails().asKnown().orElse(null);
			Long cacheRead = (details != null) ? optionalLong(details._cachedTokens()) : null;
			Long cacheWrite = (details != null) ? optionalLong(details._cacheWriteTokens()) : null;
			// Reasoning tokens are only reachable through the native usage object: the
			// portable Usage type has no field for them.
			return new DefaultUsage(Math.toIntExact(usage.inputTokens()), Math.toIntExact(usage.outputTokens()),
					Math.toIntExact(usage.totalTokens()), usage, cacheRead, cacheWrite);
		}).orElseGet(EmptyUsage::new);
	}

	private static @Nullable Long optionalLong(JsonField<Long> field) {
		return field.asNumber().map(Number::longValue).orElse(null);
	}

	static ChatResponseMetadata toResponseMetadata(Response response, Usage usage) {
		ChatResponseMetadata.Builder metadata = ChatResponseMetadata.builder()
			.id(response.id())
			.model(fields(response.model()).asString().orElse(""))
			.usage(usage)
			.keyValue(OpenAiResponsesMetadata.CREATED_AT, (long) response.createdAt())
			.keyValue(OpenAiResponsesMetadata.STATUS, response.status().map(ResponseStatus::asString).orElse(""));
		response.incompleteDetails()
			.flatMap(Response.IncompleteDetails::reason)
			.ifPresent(reason -> metadata.keyValue(OpenAiResponsesMetadata.INCOMPLETE_REASON, reason.asString()));
		return metadata.build();
	}

	/**
	 * A {@code failed} response means the request reached the model and the run did not
	 * complete. Never swallowed: the caller raises this.
	 */
	static OpenAiResponsesException failure(Response response) {
		return new OpenAiResponsesException(response.error().map(error -> error.code().asString()).orElse(null),
				response.error().map(ResponseError::message).orElse("The OpenAI response failed"));
	}

	/**
	 * A converted prompt: the top-level instruction string, plus the input items.
	 */
	record Input(@Nullable String instructions, List<ResponseInputItem> items) {
	}

}
