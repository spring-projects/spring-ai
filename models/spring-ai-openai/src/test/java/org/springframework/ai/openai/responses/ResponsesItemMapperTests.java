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
import java.util.List;
import java.util.Map;

import com.openai.core.ObjectMappers;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseInputItem;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.part.MediaPart;
import org.springframework.ai.chat.messages.part.OpaquePayload;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolCallPart;
import org.springframework.ai.chat.messages.part.ToolResultPart;
import org.springframework.ai.chat.messages.part.UnknownPart;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Dimitar Proynov
 */
class ResponsesItemMapperTests {

	private static List<String> itemTypes(List<ResponseInputItem> items) {
		return items.stream().map(item -> {
			try {
				return ObjectMappers.jsonMapper()
					.readTree(ObjectMappers.jsonMapper().writeValueAsString(item))
					.path("type")
					.asText("message");
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		}).toList();
	}

	private static String toJson(ResponseInputItem item) {
		try {
			return ObjectMappers.jsonMapper().writeValueAsString(item);
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	@Test
	void firstSystemMessageBecomesInstructionsAndFurtherOnesBecomeItems() {
		var input = ResponsesItemMapper.toInput(List.of(new SystemMessage("Be terse."),
				new SystemMessage("Answer in English."), new UserMessage("Hi")));

		assertThat(input.instructions()).isEqualTo("Be terse.");
		assertThat(itemTypes(input.items())).containsExactly("message", "message");
		assertThat(toJson(input.items().get(0))).contains("\"role\":\"developer\"").contains("Answer in English.");
		assertThat(toJson(input.items().get(1))).contains("\"role\":\"user\"");
	}

	@Test
	void userMessageWithImageUrlBecomesInputImagePart() {
		var message = UserMessage.builder()
			.text("What is this?")
			.media(List.of(Media.builder()
				.mimeType(MimeTypeUtils.IMAGE_PNG)
				.data(URI.create("https://example.com/cat.png"))
				.build()))
			.build();

		String json = toJson(ResponsesItemMapper.toInput(List.of(message)).items().get(0));

		assertThat(json).contains("\"type\":\"input_text\"")
			.contains("What is this?")
			.contains("\"type\":\"input_image\"")
			.contains("https://example.com/cat.png");
	}

	@Test
	void userMessageWithImageBytesBecomesDataUri() {
		var message = UserMessage.builder()
			.text("What is this?")
			.media(List.of(Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(new byte[] { 1, 2, 3 }).build()))
			.build();

		assertThat(toJson(ResponsesItemMapper.toInput(List.of(message)).items().get(0)))
			.contains("data:image/png;base64,AQID");
	}

	/**
	 * OpenAI infers the file type from the filename extension, and Media's generated
	 * default name does not have one.
	 */
	@Test
	void pdfInputBecomesAnInputFileWithAnExtensionOnItsFilename() {
		var message = UserMessage.builder()
			.text("Summarize this")
			.media(List.of(Media.builder()
				.mimeType(MimeTypeUtils.parseMimeType("application/pdf"))
				.data(new byte[] { 1, 2, 3 })
				.build()))
			.build();

		String json = toJson(ResponsesItemMapper.toInput(List.of(message)).items().get(0));

		assertThat(json).contains("\"type\":\"input_file\"").contains("data:application/pdf;base64,AQID");
		assertThat(filenameIn(json)).endsWith(".pdf");
	}

	@Test
	void anExplicitFilenameThatAlreadyHasTheExtensionIsLeftAlone() {
		var message = UserMessage.builder()
			.text("Summarize this")
			.media(List.of(Media.builder()
				.mimeType(MimeTypeUtils.parseMimeType("application/pdf"))
				.data(new byte[] { 1 })
				.name("report.pdf")
				.build()))
			.build();

		assertThat(filenameIn(toJson(ResponsesItemMapper.toInput(List.of(message)).items().get(0))))
			.isEqualTo("report.pdf");
	}

	@Test
	void audioInputIsRejectedWithAClearMessage() {
		var message = UserMessage.builder()
			.text("Transcribe this")
			.media(List
				.of(Media.builder().mimeType(MimeTypeUtils.parseMimeType("audio/mp3")).data(new byte[] { 1 }).build()))
			.build();

		assertThatThrownBy(() -> ResponsesItemMapper.toInput(List.of(message)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Audio input is not supported");
	}

	/**
	 * The assertion that catches every regression in this design: after a tool round, the
	 * reasoning item has to be replayed ahead of the function call it produced, with its
	 * encrypted content intact, and the tool result has to reference the call id.
	 */
	@Test
	void assistantTurnIsReplayedInOriginalOrderWithItsEncryptedReasoning() {
		Generation generation = ResponsesItemMapper.toGeneration(fixture("reasoning-with-function-call.json"));
		AssistantMessage assistant = generation.getOutput();

		List<Message> afterToolExecution = List.of(new SystemMessage("Be terse."), new UserMessage("Weather in Paris?"),
				assistant,
				ToolResponseMessage.builder()
					.responses(List.of(new ToolResponseMessage.ToolResponse("call_abc123", "getCurrentWeather", "18C")))
					.build());

		var input = ResponsesItemMapper.toInput(afterToolExecution);

		assertThat(input.instructions()).isEqualTo("Be terse.");
		assertThat(itemTypes(input.items())).containsExactly("message", "reasoning", "function_call",
				"function_call_output");
		assertThat(toJson(input.items().get(1))).contains("gAAAAABopaque-encrypted-reasoning-blob");
		assertThat(toJson(input.items().get(3))).contains("\"call_id\":\"call_abc123\"").contains("18C");
	}

	/**
	 * The transcript is the parts list, so what the mapper produced inbound is exactly
	 * what it consumes outbound: a reasoning part carrying the encrypted blob, then the
	 * tool call it justified.
	 */
	@Test
	void anAssistantTurnBecomesOneOrderedPartPerOutputItem() {
		Generation generation = ResponsesItemMapper.toGeneration(fixture("reasoning-with-function-call.json"));

		assertThat(generation.getOutput().getParts()).satisfiesExactly(first -> {
			ReasoningPart reasoning = (ReasoningPart) first;
			assertThat(reasoning.summary())
				.isEqualTo("The user is asking about the weather, so the weather tool has to run first.");
			assertThat(reasoning.payload()).isEqualTo(new OpaquePayload(OpenAiResponsesMetadata.PROVIDER,
					OpenAiResponsesMetadata.ENCRYPTED_CONTENT_KIND, "gAAAAABopaque-encrypted-reasoning-blob"));
			assertThat(reasoning.attributes()).containsEntry(OpenAiResponsesMetadata.ITEM_ID_ATTRIBUTE, "rs_1");
		}, second -> {
			ToolCallPart toolCall = (ToolCallPart) second;
			assertThat(toolCall.toolCall().id()).isEqualTo("call_abc123");
			// The item id is the transcript position, kept apart from the call id
			assertThat(toolCall.attributes()).containsEntry(OpenAiResponsesMetadata.ITEM_ID_ATTRIBUTE, "fc_1");
		});
	}

	/**
	 * The message item's {@code phase} marks a final answer apart from an intermediate
	 * update, so it has to survive onto the part and back onto the replayed item.
	 */
	@Test
	void theMessageItemsIdStatusAndPhaseTravelOnThePartAndAreReplayed() {
		Generation generation = ResponsesItemMapper.toGeneration(fixture("text-with-reasoning-summary.json"));

		TextPart text = (TextPart) generation.getOutput().getParts().get(1);
		assertThat(text.attributes()).containsEntry(OpenAiResponsesMetadata.ITEM_ID_ATTRIBUTE, "msg_1")
			.containsEntry(OpenAiResponsesMetadata.ITEM_STATUS_ATTRIBUTE, "completed")
			.containsEntry(OpenAiResponsesMetadata.PHASE_ATTRIBUTE, "final_answer");

		var input = ResponsesItemMapper.toInput(List.of(generation.getOutput()));

		assertThat(toJson(input.items().get(1))).contains("\"id\":\"msg_1\"")
			.contains("\"phase\":\"final_answer\"")
			.contains("It is 18 degrees in Paris.");
	}

	/**
	 * A reasoning part is only replayable to the provider that signed it: the Responses
	 * API rejects reasoning without its own encrypted content, so a foreign one is
	 * dropped rather than sent.
	 */
	@Test
	void reasoningFromAnotherProviderIsSkippedOnReplay() {
		AssistantMessage foreign = AssistantMessage.builder()
			.part(new ReasoningPart("Let me think", null, new OpaquePayload("anthropic", "signature", "EqQBCkYIBBgC"),
					Map.of()))
			.content("Hello")
			.build();

		var input = ResponsesItemMapper.toInput(List.of(foreign));

		assertThat(input.items()).hasSize(1);
		assertThat(toJson(input.items().get(0))).contains("\"role\":\"assistant\"").contains("Hello");
	}

	/**
	 * A reasoning part that lost its payload on the way through a component that does not
	 * preserve one cannot be replayed either, and must not fail the turn.
	 */
	@Test
	void reasoningWithoutAPayloadIsSkippedOnReplay() {
		AssistantMessage withoutPayload = AssistantMessage.builder()
			.reasoning(ReasoningPart.of("Let me think"))
			.content("Hello")
			.build();

		assertThat(itemTypes(ResponsesItemMapper.toInput(List.of(withoutPayload)).items())).containsExactly("message");
	}

	/**
	 * The item id is what addresses a reasoning item in the transcript, and the API will
	 * not take the item without it.
	 */
	@Test
	void reasoningWithoutAnItemIdIsSkippedOnReplay() {
		AssistantMessage withoutItemId = AssistantMessage.builder()
			.part(new ReasoningPart(null, "Thought about it",
					new OpaquePayload(OpenAiResponsesMetadata.PROVIDER, OpenAiResponsesMetadata.ENCRYPTED_CONTENT_KIND,
							"gAAAAAB"),
					Map.of()))
			.content("Hello")
			.build();

		assertThat(itemTypes(ResponsesItemMapper.toInput(List.of(withoutItemId)).items())).containsExactly("message");
	}

	/**
	 * Image generation is the one hosted tool whose result the application wants back, so
	 * it becomes media rather than an opaque item. A generated image is output, not
	 * context, so it is not handed back to the model.
	 */
	@Test
	void aGeneratedImageBecomesMediaAndIsNotReplayed() {
		Generation generation = ResponsesItemMapper.toGeneration(fixture("image-generation-then-message.json"));
		AssistantMessage assistant = generation.getOutput();

		assertThat(assistant.getParts().get(0)).isInstanceOf(MediaPart.class);
		assertThat(assistant.getMedia()).singleElement().satisfies(media -> {
			assertThat(media.getId()).isEqualTo("ig_1");
			assertThat(media.getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_PNG);
		});

		assertThat(itemTypes(ResponsesItemMapper.toInput(List.of(assistant)).items())).containsExactly("message");
	}

	/**
	 * The image generation tool can be asked for JPEG or WebP, and the item echoes the
	 * format back. Labelling those bytes {@code image/png} would have the application
	 * write a file no viewer opens.
	 */
	@Test
	void aGeneratedImageIsLabelledWithTheOutputFormatTheItemReports() {
		Generation generation = ResponsesItemMapper.toGeneration(fixture("image-generation-jpeg.json"));

		assertThat(generation.getOutput().getMedia()).singleElement()
			.satisfies(media -> assertThat(media.getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_JPEG));
	}

	/**
	 * Replay must not fail a turn over one item it cannot parse.
	 */
	@Test
	void anUnparseableHostedToolItemIsDroppedRatherThanFailingTheTurn() {
		AssistantMessage broken = AssistantMessage.builder()
			.part(new UnknownPart(OpenAiResponsesMetadata.PROVIDER, "web_search_call", "not json", null, Map.of()))
			.content("Hello")
			.build();

		assertThat(itemTypes(ResponsesItemMapper.toInput(List.of(broken)).items())).containsExactly("message");
	}

	/**
	 * A tool result on an assistant turn is a caller mistake, not a broken invariant of
	 * the mapper: {@code AssistantMessage.Builder.part()} is public, so this rejects an
	 * argument.
	 */
	@Test
	void aToolResultOnAnAssistantTurnIsRejectedAsAnIllegalArgument() {
		AssistantMessage misplaced = AssistantMessage.builder()
			.part(ToolResultPart.of(new ToolResponseMessage.ToolResponse("call_1", "getCurrentWeather", "18C")))
			.build();

		assertThatThrownBy(() -> ResponsesItemMapper.toInput(List.of(misplaced)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("tool response message");
	}

	/**
	 * An unknown part another provider's adapter produced is its own to replay, never
	 * ours.
	 */
	@Test
	void anUnknownPartFromAnotherProviderIsSkippedOnReplay() {
		AssistantMessage foreign = AssistantMessage.builder()
			.part(new UnknownPart("anthropic", "server_tool_use", "{\"type\":\"server_tool_use\"}", null, Map.of()))
			.content("Hello")
			.build();

		assertThat(itemTypes(ResponsesItemMapper.toInput(List.of(foreign)).items())).containsExactly("message");
	}

	@Test
	void plainAssistantMessageIsSynthesizedFromTextAndToolCalls() {
		AssistantMessage plain = AssistantMessage.builder()
			.content("Let me check.")
			.toolCalls(List.of(new ToolCall("call_1", "function", "getCurrentWeather", "{\"location\":\"Rome\"}")))
			.build();

		var input = ResponsesItemMapper.toInput(List.of(plain));

		assertThat(itemTypes(input.items())).containsExactly("message", "function_call");
		assertThat(toJson(input.items().get(1))).contains("\"call_id\":\"call_1\"");
	}

	@Test
	void parallelToolResponsesBecomeOneOutputItemEach() {
		var toolResponses = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("call_1", "weather", "18C"),
					new ToolResponseMessage.ToolResponse("call_2", "weather", "25C")))
			.build();

		var input = ResponsesItemMapper.toInput(List.of(toolResponses));

		assertThat(itemTypes(input.items())).containsExactly("function_call_output", "function_call_output");
		assertThat(toJson(input.items().get(0))).contains("call_1");
		assertThat(toJson(input.items().get(1))).contains("call_2");
	}

	@Test
	void functionCallsBecomeToolCallsUsingTheCallIdNotTheItemId() {
		Generation generation = ResponsesItemMapper.toGeneration(fixture("reasoning-with-function-call.json"));

		assertThat(generation.getOutput().getToolCalls())
			.containsExactly(new ToolCall("call_abc123", "function", "getCurrentWeather", "{\"location\":\"Paris\"}"));
		assertThat(generation.getMetadata().getFinishReason()).isEqualTo("TOOL_CALLS");
	}

	@Test
	void reasoningSummariesSurfaceAsReasoningContentAndTextIsConcatenated() {
		Generation generation = ResponsesItemMapper.toGeneration(fixture("text-with-reasoning-summary.json"));

		assertThat(generation.getOutput().getText()).isEqualTo("It is 18 degrees in Paris.");
		assertThat(generation.getMetadata().<String>get(OpenAiResponsesMetadata.REASONING_CONTENT))
			.isEqualTo("Report the temperature the tool returned.");
		assertThat(generation.getMetadata().getFinishReason()).isEqualTo("STOP");
	}

	/**
	 * Everything a turn reports beyond its parts is on the generation metadata. The
	 * assistant message carries none of it: message metadata is persisted by some chat
	 * memory repositories and dropped by others, and nothing on the replay path reads it.
	 */
	@Test
	void theAssistantMessageCarriesNoMetadataOfItsOwn() {
		Generation generation = ResponsesItemMapper.toGeneration(fixture("web-search-then-message.json"));

		// AbstractMessage always stamps the message type; nothing else is added
		assertThat(generation.getOutput().getMetadata()).containsOnlyKeys(AbstractMessage.MESSAGE_TYPE);
		assertThat(generation.getMetadata().keySet()).contains(OpenAiResponsesMetadata.STATUS,
				OpenAiResponsesMetadata.REFUSAL, OpenAiResponsesMetadata.ANNOTATIONS,
				OpenAiResponsesMetadata.REASONING_CONTENT, OpenAiResponsesMetadata.HOSTED_TOOL_CALLS);
	}

	/**
	 * A tool OpenAI ran itself must never look like a tool call, or
	 * {@code ToolCallingAdvisor} would try to execute {@code web_search} locally.
	 */
	@Test
	void hostedToolCallsAreReportedInMetadataAndNeverAsToolCalls() {
		Generation generation = ResponsesItemMapper.toGeneration(fixture("web-search-then-message.json"));

		assertThat(generation.getOutput().hasToolCalls()).isFalse();
		assertThat(hostedToolCalls(generation)).singleElement()
			.satisfies(summary -> assertThat(summary).containsEntry("type", "web_search_call")
				.containsEntry("id", "ws_1")
				.containsEntry("status", "completed"));
		assertThat(maps(generation, OpenAiResponsesMetadata.ANNOTATIONS)).singleElement()
			.satisfies(annotation -> assertThat(annotation).containsEntry("url", "https://spring.io/blog"));
	}

	@Test
	void hostedToolItemsStillRoundTripOnReplay() {
		Generation generation = ResponsesItemMapper.toGeneration(fixture("web-search-then-message.json"));

		var input = ResponsesItemMapper.toInput(List.of(generation.getOutput()));

		assertThat(itemTypes(input.items())).containsExactly("web_search_call", "message");
	}

	@Test
	void incompleteResponseMapsToTheMatchingFinishReason() {
		Generation generation = ResponsesItemMapper.toGeneration(fixture("incomplete-max-output-tokens.json"));

		assertThat(generation.getMetadata().getFinishReason()).isEqualTo("LENGTH");
		assertThat(generation.getMetadata().<String>get(OpenAiResponsesMetadata.INCOMPLETE_REASON))
			.isEqualTo("max_output_tokens");
	}

	@Test
	void usageMapsInputAndOutputTokensAndExposesTheNativeUsage() {
		var usage = ResponsesItemMapper.toUsage(fixture("reasoning-with-function-call.json"));

		assertThat(usage.getPromptTokens()).isEqualTo(120);
		assertThat(usage.getCompletionTokens()).isEqualTo(40);
		assertThat(usage.getTotalTokens()).isEqualTo(160);
		assertThat(usage.getCacheReadInputTokens()).isEqualTo(20);
		assertThat(usage.getNativeUsage()).isNotNull();
	}

	/**
	 * {@code input_tokens_details} is a required field of the usage object, so the SDK's
	 * typed accessor for it throws when it is absent. An OpenAI-compatible backend may
	 * leave the whole object out, and that must cost the turn its cache token counts, not
	 * the turn.
	 */
	@Test
	void usageWithoutTokenDetailsReportsNoCacheTokensRatherThanFailing() {
		var usage = ResponsesItemMapper.toUsage(fixture("usage-without-token-details.json"));

		assertThat(usage.getPromptTokens()).isEqualTo(120);
		assertThat(usage.getTotalTokens()).isEqualTo(160);
		assertThat(usage.getCacheReadInputTokens()).isNull();
	}

	@Test
	void responseMetadataCarriesTheResponseIdModelAndStatus() {
		Response response = fixture("text-with-reasoning-summary.json");

		var metadata = ResponsesItemMapper.toResponseMetadata(response, ResponsesItemMapper.toUsage(response));

		assertThat(metadata.getId()).isEqualTo("resp_text_1");
		assertThat(metadata.getModel()).isEqualTo("gpt-5-mini");
		assertThat(metadata.<String>get(OpenAiResponsesMetadata.STATUS)).isEqualTo("completed");
	}

	private static String filenameIn(String itemJson) {
		try {
			return ObjectMappers.jsonMapper().readTree(itemJson).findValue("filename").asText();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static Response fixture(String name) {
		return ResponsesTestFixtures.response(name);
	}

	private static List<Map<String, Object>> maps(Generation generation, String metadataKey) {
		return generation.getMetadata().get(metadataKey);
	}

	private static List<Map<String, Object>> hostedToolCalls(Generation generation) {
		return generation.getMetadata().get(OpenAiResponsesMetadata.HOSTED_TOOL_CALLS);
	}

}
