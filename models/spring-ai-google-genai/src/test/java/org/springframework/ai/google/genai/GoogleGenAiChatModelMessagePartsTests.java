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

package org.springframework.ai.google.genai;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.ResponseStream;
import com.google.genai.types.Blob;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.ExecutableCode;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.ToolCall;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.part.MediaPart;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.OpaquePayload;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.StreamingParts;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolCallPart;
import org.springframework.ai.chat.messages.part.ToolResultPart;
import org.springframework.ai.chat.messages.part.UnknownPart;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * Tests for the {@link MessagePart} mapping of {@link GoogleGenAiChatModel}: Gemini parts
 * to ordered message parts and back, thought signatures as part payloads, and the
 * streaming contract with synthesized part indices.
 *
 * @author Christian Tzolov
 * @author Dimitar Proynov
 */
class GoogleGenAiChatModelMessagePartsTests {

	private static final byte[] SIGNATURE = "sig-bytes".getBytes(StandardCharsets.UTF_8);

	private static final byte[] OTHER_SIGNATURE = "other-sig".getBytes(StandardCharsets.UTF_8);

	private static final OpaquePayload SIGNATURE_PAYLOAD = signaturePayload(SIGNATURE);

	@Mock
	private Client client;

	private TestGoogleGenAiGeminiChatModel chatModel;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
			.model(GoogleGenAiChatModel.ChatModel.GEMINI_2_5_FLASH)
			.includeThoughts(true)
			.build();
		this.chatModel = new TestGoogleGenAiGeminiChatModel(this.client, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	// -- inbound, non-streaming --

	@Test
	void thoughtAndTextCandidateIsOneGenerationWithOrderedParts() {
		this.chatModel
			.setMockGenerateContentResponse(response(thoughtPart("thinking", null), textPart("answer", null)));

		ChatResponse response = this.chatModel.call(new Prompt("Explain"));

		assertThat(response.getResults()).hasSize(1);
		AssistantMessage message = response.getResult().getOutput();
		assertThat(message.getParts()).containsExactly(ReasoningPart.of("thinking"), TextPart.of("answer"));
		assertThat(message.getText()).isEqualTo("answer");
		assertThat(message.getReasoning()).containsExactly(ReasoningPart.of("thinking"));
		assertThat(message.getMetadata()).doesNotContainKey("isThought");
		assertThat(response.getMetadata().getId()).isEqualTo("resp_call");
	}

	@Test
	void functionCallKeepsItsIdAndSignature() {
		this.chatModel.setMockGenerateContentResponse(response(thoughtPart("thinking", null),
				functionCallPart("call_1", "getWeather", Map.of("city", "Paris"), SIGNATURE)));

		AssistantMessage message = this.chatModel.call(new Prompt("Weather?")).getResult().getOutput();

		assertThat(message.getParts()).hasSize(2);
		assertThat(message.getParts().get(0)).isEqualTo(ReasoningPart.of("thinking"));
		ToolCallPart toolCallPart = (ToolCallPart) message.getParts().get(1);
		assertThat(toolCallPart.toolCall().id()).isEqualTo("call_1");
		assertThat(toolCallPart.toolCall().name()).isEqualTo("getWeather");
		assertThat(toolCallPart.toolCall().arguments()).contains("Paris");
		assertThat(toolCallPart.payload()).isEqualTo(SIGNATURE_PAYLOAD);
		assertThat(message.hasToolCalls()).isTrue();
		// Still written for chat memories that keep metadata but not parts
		assertThat(message.getMetadata().get("thoughtSignatures")).isEqualTo(List.of(SIGNATURE));
	}

	@Test
	void textBeforeAFunctionCallIsKept() {
		this.chatModel.setMockGenerateContentResponse(response(textPart("Let me check.", null),
				functionCallPart(null, "getWeather", Map.of("city", "Paris"), null)));

		AssistantMessage message = this.chatModel.call(new Prompt("Weather?")).getResult().getOutput();

		assertThat(message.getText()).isEqualTo("Let me check.");
		assertThat(message.getParts().get(0)).isEqualTo(TextPart.of("Let me check."));
		assertThat(message.getToolCalls()).hasSize(1);
	}

	@Test
	void signatureOnTextPartIsKeptAsPayload() {
		this.chatModel.setMockGenerateContentResponse(response(textPart("answer", SIGNATURE)));

		AssistantMessage message = this.chatModel.call(new Prompt("Explain")).getResult().getOutput();

		assertThat(message.getParts()).containsExactly(new TextPart("answer", SIGNATURE_PAYLOAD, Map.of()));
	}

	@Test
	void inlineDataBecomesMediaPart() {
		byte[] image = { 1, 2, 3 };
		Part imagePart = Part.builder()
			.inlineData(Blob.builder().mimeType("image/png").data(image).build())
			.thoughtSignature(SIGNATURE)
			.build();
		this.chatModel.setMockGenerateContentResponse(response(imagePart));

		AssistantMessage message = this.chatModel.call(new Prompt("Draw")).getResult().getOutput();

		MediaPart mediaPart = (MediaPart) message.getParts().get(0);
		assertThat(mediaPart.media().getMimeType().toString()).isEqualTo("image/png");
		assertThat(mediaPart.media().getDataAsByteArray()).isEqualTo(image);
		assertThat(mediaPart.payload()).isEqualTo(SIGNATURE_PAYLOAD);
		assertThat(message.getMedia()).hasSize(1);
	}

	@Test
	void serverSideToolPartsBecomeUnknownParts() {
		Part serverToolCall = Part.builder()
			.toolCall(ToolCall.builder().id("srv_1").args(Map.of("q", "x")).build())
			.build();
		this.chatModel.setMockGenerateContentResponse(response(serverToolCall, textPart("answer", null)));

		AssistantMessage message = this.chatModel.call(new Prompt("Search")).getResult().getOutput();

		assertThat(message.getParts()).hasSize(2);
		UnknownPart unknown = (UnknownPart) message.getParts().get(0);
		assertThat(unknown.provider()).isEqualTo(GoogleGenAiChatModel.GOOGLE_PROVIDER);
		assertThat(unknown.kind()).isEqualTo("toolCall");
		assertThat(unknown.rawJson()).contains("srv_1");
		assertThat(message.hasToolCalls()).isFalse();
		assertThat(message.getText()).isEqualTo("answer");
		assertThat(message.getMetadata()).containsKey("serverSideToolInvocations");
	}

	@Test
	void candidateWithoutContentHasEmptyText() {
		Candidate candidate = Candidate.builder().build();
		this.chatModel
			.setMockGenerateContentResponse(GenerateContentResponse.builder().candidates(List.of(candidate)).build());

		ChatResponse response = this.chatModel.call(new Prompt("Explain"));

		assertThat(response.getResult().getOutput().getText()).isEmpty();
		assertThat(response.getMetadata().getModel()).isEmpty();
	}

	// -- outbound --

	@Test
	void everySignatureIsReplayedOnThePartThatCarriedIt() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(new ReasoningPart("thinking", null, signaturePayload(OTHER_SIGNATURE), Map.of()))
			.part(new ToolCallPart(
					new AssistantMessage.ToolCall("call_1", "function", "getWeather", "{\"city\":\"Paris\"}"),
					SIGNATURE_PAYLOAD, Map.of()))
			.part(ToolCallPart.of(new AssistantMessage.ToolCall("", "function", "getTime", "{}")))
			.build();

		List<Part> parts = this.chatModel.messageToGeminiParts(assistant);

		assertThat(parts).hasSize(3);
		assertThat(parts.get(0).thought()).contains(true);
		assertThat(parts.get(0).text()).contains("thinking");
		assertThat(parts.get(0).thoughtSignature()).contains(OTHER_SIGNATURE);
		assertThat(parts.get(1).functionCall().get().id()).contains("call_1");
		assertThat(parts.get(1).functionCall().get().name()).contains("getWeather");
		assertThat(parts.get(1).thoughtSignature()).contains(SIGNATURE);
		assertThat(parts.get(2).functionCall().get().id()).isEmpty();
		assertThat(parts.get(2).thoughtSignature()).isEmpty();
	}

	@Test
	void responseRoundTripsThroughTheModelUnchanged() {
		byte[] image = { 1, 2, 3 };
		Part serverToolCall = Part.builder()
			.toolCall(ToolCall.builder().id("srv_1").args(Map.of("q", "x")).build())
			.thoughtSignature(OTHER_SIGNATURE)
			.build();
		Part executableCode = Part.builder().executableCode(ExecutableCode.builder().code("print(1)").build()).build();
		Part imagePart = Part.builder()
			.inlineData(Blob.builder().mimeType("image/png").data(image).build())
			.thoughtSignature(SIGNATURE)
			.build();
		List<Part> original = List.of(thoughtPart("thinking", SIGNATURE), serverToolCall, executableCode, imagePart,
				textPart("answer", OTHER_SIGNATURE),
				functionCallPart("call_1", "getWeather", Map.of("city", "Paris"), SIGNATURE));
		this.chatModel.setMockGenerateContentResponse(response(original.toArray(Part[]::new)));
		AssistantMessage assistant = this.chatModel.call(new Prompt("Weather?")).getResult().getOutput();

		List<Part> parts = this.chatModel.messageToGeminiParts(assistant);

		// Compared as wire JSON: Part equality compares signature arrays by identity
		assertThat(json(parts)).isEqualTo(json(original));
	}

	@Test
	void signatureOnlyPartRoundTripsThroughTheModel() {
		Part signatureOnly = Part.builder().thoughtSignature(SIGNATURE).build();
		this.chatModel.setMockGenerateContentResponse(response(textPart("answer", null), signatureOnly));
		AssistantMessage assistant = this.chatModel.call(new Prompt("Explain")).getResult().getOutput();

		List<Part> parts = this.chatModel.messageToGeminiParts(assistant);

		assertThat(parts).hasSize(2);
		assertThat(parts.get(0).text()).contains("answer");
		assertThat(parts.get(1).text()).contains("");
		assertThat(parts.get(1).thoughtSignature()).contains(SIGNATURE);
	}

	@Test
	void emptyTextWithoutSignatureIsNotReplayed() {
		AssistantMessage assistant = AssistantMessage.builder()
			.content("")
			.toolCalls(List.of(new AssistantMessage.ToolCall("", "function", "getWeather", "{\"city\":\"Paris\"}")))
			.build();

		List<Part> parts = this.chatModel.messageToGeminiParts(assistant);

		assertThat(parts).hasSize(1);
		assertThat(parts.get(0).functionCall()).isPresent();
	}

	@Test
	void foreignPayloadsAreNotReplayed() {
		OpaquePayload openAi = new OpaquePayload("openai", "encrypted_content", "enc");
		AssistantMessage assistant = AssistantMessage.builder()
			.part(new ReasoningPart("openai thoughts", null, openAi, Map.of()))
			.part(new UnknownPart("openai", "web_search_call", "{\"type\":\"web_search_call\"}", null, Map.of()))
			.part(new TextPart("answer", openAi, Map.of()))
			.part(new ToolCallPart(new AssistantMessage.ToolCall("", "function", "getWeather", "{}"), openAi, Map.of()))
			.build();

		List<Part> parts = this.chatModel.messageToGeminiParts(assistant);

		assertThat(parts).hasSize(2);
		assertThat(parts.get(0).text()).contains("answer");
		assertThat(parts.get(0).thoughtSignature()).isEmpty();
		assertThat(parts.get(1).functionCall()).isPresent();
		assertThat(parts.get(1).thoughtSignature()).isEmpty();
	}

	@Test
	void reasoningWithoutPayloadIsReplayedAsThought() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(ReasoningPart.of("thinking"))
			.part(TextPart.of("answer"))
			.build();

		List<Part> parts = this.chatModel.messageToGeminiParts(assistant);

		assertThat(parts).containsExactly(Part.builder().thought(true).text("thinking").build(),
				Part.builder().text("answer").build());
	}

	@Test
	void legacyThoughtSignaturesMetadataIsReplayedOnTheFunctionCalls() {
		AssistantMessage assistant = AssistantMessage.builder()
			.content("")
			.toolCalls(List.of(new AssistantMessage.ToolCall("", "function", "getWeather", "{\"city\":\"Paris\"}"),
					new AssistantMessage.ToolCall("", "function", "getTime", "{}")))
			.properties(Map.of("thoughtSignatures", List.of(SIGNATURE, OTHER_SIGNATURE)))
			.build();

		List<Part> parts = this.chatModel.messageToGeminiParts(assistant);

		assertThat(parts).hasSize(2);
		assertThat(parts.get(0).thoughtSignature()).contains(SIGNATURE);
		assertThat(parts.get(1).thoughtSignature()).contains(OTHER_SIGNATURE);
	}

	@Test
	void legacyThoughtSignaturesAreIgnoredWhenAPartCarriesAGooglePayload() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(new ToolCallPart(new AssistantMessage.ToolCall("", "function", "getWeather", "{}"), SIGNATURE_PAYLOAD,
					Map.of()))
			.part(ToolCallPart.of(new AssistantMessage.ToolCall("", "function", "getTime", "{}")))
			.properties(Map.of("thoughtSignatures", List.of(SIGNATURE)))
			.build();

		List<Part> parts = this.chatModel.messageToGeminiParts(assistant);

		assertThat(parts.get(0).thoughtSignature()).contains(SIGNATURE);
		assertThat(parts.get(1).thoughtSignature()).isEmpty();
	}

	@Test
	void toolResultPartOnAssistantMessageIsRejected() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(ToolResultPart.of(new ToolResponseMessage.ToolResponse("call_1", "getWeather", "{}")))
			.build();

		assertThatIllegalArgumentException().isThrownBy(() -> this.chatModel.messageToGeminiParts(assistant));
	}

	@Test
	void unknownPartThatCannotBeParsedIsDropped() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(new UnknownPart(GoogleGenAiChatModel.GOOGLE_PROVIDER, "toolCall", "not json", null, Map.of()))
			.part(TextPart.of("answer"))
			.build();

		List<Part> parts = this.chatModel.messageToGeminiParts(assistant);

		assertThat(parts).containsExactly(Part.builder().text("answer").build());
	}

	@Test
	void signaturesAreReplayedInTheRequestOfTheNextToolCallRound() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(new ToolCallPart(new AssistantMessage.ToolCall("", "function", "getWeather", "{\"city\":\"Paris\"}"),
					SIGNATURE_PAYLOAD, Map.of()))
			.build();
		Prompt prompt = new Prompt(
				List.<Message>of(new UserMessage("Weather?"), assistant, toolResponse("getWeather", "{\"tempC\":15}")),
				this.chatModel.getOptions());

		List<Part> parts = assistantParts(this.chatModel.createGeminiRequest(prompt));

		assertThat(parts).hasSize(1);
		assertThat(parts.get(0).thoughtSignature()).contains(SIGNATURE);
	}

	// -- streaming --

	@Test
	void streamingThoughtTextAndFunctionCallProduceIndexedParts() {
		List<GenerateContentResponse> chunks = List.of(chunk("resp_1", thoughtPart("think ", null)),
				chunk("resp_1", thoughtPart("ing", OTHER_SIGNATURE)), chunk("resp_1", textPart("Let me ", null)),
				chunk("resp_1", textPart("check.", null)),
				chunk("resp_1", functionCallPart("call_1", "getWeather", Map.of("city", "Paris"), SIGNATURE)));
		givenStream(chunks);

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		List<ChatResponse> responses = new MessageAggregator()
			.aggregate(this.chatModel.stream(new Prompt("Weather?")), aggregatedRef::set)
			.collectList()
			.block();

		assertThat(responses).hasSize(5);
		assertThat(responses).allSatisfy(r -> assertThat(r.getMetadata().getId()).isEqualTo("resp_1"));
		MessagePart first = responses.get(0).getResult().getOutput().getParts().get(0);
		assertThat(first).isEqualTo(StreamingParts.partial(ReasoningPart.of("think "), 0));
		assertThat(responses.get(0).getResult().getOutput().getText()).isEmpty();
		MessagePart signed = responses.get(1).getResult().getOutput().getParts().get(0);
		assertThat(signed.payload()).isEqualTo(signaturePayload(OTHER_SIGNATURE));
		assertThat(StreamingParts.partIndex(signed)).isZero();
		MessagePart text = responses.get(2).getResult().getOutput().getParts().get(0);
		assertThat(text).isEqualTo(StreamingParts.partial(TextPart.of("Let me "), 1));
		assertThat(responses.get(2).getResult().getOutput().getText()).isEqualTo("Let me ");
		MessagePart call = responses.get(4).getResult().getOutput().getParts().get(0);
		assertThat(call).isInstanceOf(ToolCallPart.class);
		assertThat(StreamingParts.partIndex(call)).isEqualTo(2);
		assertThat(StreamingParts.isPartial(call)).isFalse();
		assertThat(responses.get(4).getResult().getOutput().hasToolCalls()).isTrue();

		AssistantMessage aggregated = aggregatedRef.get().getResult().getOutput();
		assertThat(aggregated.getText()).isEqualTo("Let me check.");
		assertThat(aggregated.getParts()).containsExactly(
				new ReasoningPart("think ing", null, signaturePayload(OTHER_SIGNATURE), Map.of()),
				TextPart.of("Let me check."),
				new ToolCallPart(
						new AssistantMessage.ToolCall("call_1", "function", "getWeather", "{\"city\":\"Paris\"}"),
						SIGNATURE_PAYLOAD, Map.of()));
	}

	@Test
	void streamingSignatureOnAClosingEmptyTextChunkLandsOnTheText() {
		givenStream(List.of(chunk("resp_3", textPart("Hello ", null)), chunk("resp_3", textPart("world", null)),
				chunk("resp_3", textPart("", SIGNATURE))));

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		new MessageAggregator().aggregate(this.chatModel.stream(new Prompt("Hi")), aggregatedRef::set)
			.collectList()
			.block();

		AssistantMessage aggregated = aggregatedRef.get().getResult().getOutput();
		assertThat(aggregated.getParts()).containsExactly(new TextPart("Hello world", SIGNATURE_PAYLOAD, Map.of()));
		assertThat(json(this.chatModel.messageToGeminiParts(aggregated)))
			.containsExactly(Part.builder().text("Hello world").thoughtSignature(SIGNATURE).build().toJson());
	}

	@Test
	void streamingChunkWithSeveralPartsAssignsConsecutiveIndices() {
		givenStream(List.of(chunk("resp_2", thoughtPart("t", null), textPart("a", null)),
				chunk("resp_2", textPart("b", null))));

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		List<ChatResponse> responses = new MessageAggregator()
			.aggregate(this.chatModel.stream(new Prompt("Explain")), aggregatedRef::set)
			.collectList()
			.block();

		assertThat(responses.get(0).getResult().getOutput().getParts()).containsExactly(
				StreamingParts.partial(ReasoningPart.of("t"), 0), StreamingParts.partial(TextPart.of("a"), 1));
		AssistantMessage aggregated = aggregatedRef.get().getResult().getOutput();
		assertThat(aggregated.getParts()).containsExactly(ReasoningPart.of("t"), TextPart.of("ab"));
	}

	@Test
	void streamingChunkWithoutPartsKeepsAnEmptyText() {
		givenStream(List.of(chunk("resp_4", textPart("answer", null)), chunk("resp_4")));

		List<ChatResponse> responses = this.chatModel.stream(new Prompt("Explain")).collectList().block();

		assertThat(responses).hasSize(2);
		assertThat(responses.get(1).getResult().getOutput().getText()).isEmpty();
	}

	@Test
	void streamingChunkWithoutPartsDoesNotSplitAThought() {
		givenStream(List.of(chunk("resp_5", thoughtPart("think ", null)), chunk("resp_5"),
				chunk("resp_5", thoughtPart("ing", SIGNATURE)), chunk("resp_5", textPart("answer", null))));

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		List<ChatResponse> responses = new MessageAggregator()
			.aggregate(this.chatModel.stream(new Prompt("Explain")), aggregatedRef::set)
			.collectList()
			.block();

		MessagePart placeholder = responses.get(1).getResult().getOutput().getParts().get(0);
		assertThat(StreamingParts.partIndex(placeholder)).isNull();
		assertThat(StreamingParts.partIndex(responses.get(2).getResult().getOutput().getParts().get(0))).isZero();
		AssistantMessage aggregated = aggregatedRef.get().getResult().getOutput();
		assertThat(aggregated.getParts())
			.containsExactly(new ReasoningPart("think ing", null, SIGNATURE_PAYLOAD, Map.of()), TextPart.of("answer"));
	}

	@Test
	void streamingPassesThroughAMessageSubclassReturnedByAnOverride() {
		TestGoogleGenAiGeminiChatModel customModel = new TestGoogleGenAiGeminiChatModel(this.client,
				this.chatModel.getOptions(), RetryUtils.DEFAULT_RETRY_TEMPLATE) {

			@Override
			protected List<Generation> responseCandidateToGeneration(Candidate candidate) {
				Generation generation = super.responseCandidateToGeneration(candidate).get(0);
				return List.of(new Generation(new TaggedAssistantMessage(generation.getOutput().getParts()),
						generation.getMetadata()));
			}

		};
		givenStream(List.of(chunk("resp_6", textPart("Hello ", null)), chunk("resp_6", textPart("world", null))));

		List<ChatResponse> responses = customModel.stream(new Prompt("Hi")).collectList().block();

		assertThat(responses).allSatisfy(response -> {
			AssistantMessage output = response.getResult().getOutput();
			assertThat(output).isInstanceOf(TaggedAssistantMessage.class);
			assertThat(StreamingParts.partIndex(output.getParts().get(0))).isNull();
		});
	}

	// -- helpers --

	private static List<String> json(List<Part> parts) {
		return parts.stream().map(Part::toJson).toList();
	}

	private static OpaquePayload signaturePayload(byte[] signature) {
		return new OpaquePayload(GoogleGenAiChatModel.GOOGLE_PROVIDER, GoogleGenAiChatModel.PAYLOAD_THOUGHT_SIGNATURE,
				Base64.getEncoder().encodeToString(signature));
	}

	private static Part thoughtPart(String text, byte @Nullable [] signature) {
		Part.Builder builder = Part.builder().text(text).thought(true);
		if (signature != null) {
			builder.thoughtSignature(signature);
		}
		return builder.build();
	}

	private static Part textPart(String text, byte @Nullable [] signature) {
		Part.Builder builder = Part.builder().text(text);
		if (signature != null) {
			builder.thoughtSignature(signature);
		}
		return builder.build();
	}

	private static Part functionCallPart(@Nullable String id, String name, Map<String, Object> args,
			byte @Nullable [] signature) {
		FunctionCall.Builder functionCall = FunctionCall.builder().name(name).args(args);
		if (id != null) {
			functionCall.id(id);
		}
		Part.Builder builder = Part.builder().functionCall(functionCall.build());
		if (signature != null) {
			builder.thoughtSignature(signature);
		}
		return builder.build();
	}

	private static GenerateContentResponse response(Part... parts) {
		return chunk("resp_call", parts);
	}

	private static GenerateContentResponse chunk(String responseId, Part... parts) {
		Content content = Content.builder().role("model").parts(List.of(parts)).build();
		Candidate candidate = Candidate.builder().content(content).build();
		return GenerateContentResponse.builder()
			.responseId(responseId)
			.candidates(List.of(candidate))
			.modelVersion("gemini-2.5-flash")
			.build();
	}

	private static ToolResponseMessage toolResponse(String name, String data) {
		return ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("", name, data)))
			.build();
	}

	private static List<Part> assistantParts(GoogleGenAiChatModel.GeminiRequest request) {
		for (Content content : request.contents()) {
			if (content.role().filter("model"::equals).isPresent()) {
				return content.parts().orElse(List.of());
			}
		}
		return List.of();
	}

	@SuppressWarnings("unchecked")
	private void givenStream(List<GenerateContentResponse> chunks) {
		Models models = Mockito.mock(Models.class);
		ReflectionTestUtils.setField(this.client, "models", models);
		ResponseStream<GenerateContentResponse> iterable = Mockito.mock(ResponseStream.class);
		given(iterable.iterator()).willReturn(chunks.iterator());
		given(iterable.spliterator()).willCallRealMethod();
		given(models.generateContentStream(any(String.class), any(List.class), any())).willReturn(iterable);
	}

	private static final class TaggedAssistantMessage extends AssistantMessage {

		TaggedAssistantMessage(List<MessagePart> parts) {
			super(parts, Map.of());
		}

	}

}
