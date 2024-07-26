/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.vertexai.gemini;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.AbstractToolCallSupport;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ChatModelDescription;
import org.springframework.ai.model.Media;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.vertexai.gemini.metadata.VertexAiUsage;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Candidate;
import com.google.cloud.vertexai.api.Candidate.FinishReason;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.FunctionCall;
import com.google.cloud.vertexai.api.FunctionDeclaration;
import com.google.cloud.vertexai.api.FunctionResponse;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.api.Schema;
import com.google.cloud.vertexai.api.Tool;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.PartMaker;
import com.google.cloud.vertexai.generativeai.ResponseStream;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;

import reactor.core.publisher.Flux;

/**
 * @author Christian Tzolov
 * @author Grogdunn
 * @author luocongqiu
 * @author Chris Turchin
 * @since 0.8.1
 */
public class VertexAiGeminiChatModel extends AbstractToolCallSupport implements ChatModel, DisposableBean {

	private final static boolean IS_RUNTIME_CALL = true;

	private final VertexAI vertexAI;

	private final VertexAiGeminiChatOptions defaultOptions;

	private final GenerationConfig generationConfig;

	public enum GeminiMessageType {

		USER("user"),

		MODEL("model");

		GeminiMessageType(String value) {
			this.value = value;
		}

		public final String value;

		public String getValue() {
			return this.value;
		}

	}

	public enum ChatModel implements ChatModelDescription {

		/**
		 * Deprecated by Goolgle in favor of 1.5 pro and flash models.
		 */
		GEMINI_PRO_VISION("gemini-pro-vision"),

		GEMINI_PRO("gemini-pro"),

		GEMINI_1_5_PRO("gemini-1.5-pro-001"),

		GEMINI_1_5_FLASH("gemini-1.5-flash-001");

		ChatModel(String value) {
			this.value = value;
		}

		public final String value;

		public String getValue() {
			return this.value;
		}

		@Override
		public String getName() {
			return this.value;
		}

	}

	public VertexAiGeminiChatModel(VertexAI vertexAI) {
		this(vertexAI,
				VertexAiGeminiChatOptions.builder().withModel(ChatModel.GEMINI_1_5_PRO).withTemperature(0.8f).build());
	}

	public VertexAiGeminiChatModel(VertexAI vertexAI, VertexAiGeminiChatOptions options) {
		this(vertexAI, options, null);
	}

	public VertexAiGeminiChatModel(VertexAI vertexAI, VertexAiGeminiChatOptions options,
			FunctionCallbackContext functionCallbackContext) {

		super(functionCallbackContext);

		Assert.notNull(vertexAI, "VertexAI must not be null");
		Assert.notNull(options, "VertexAiGeminiChatOptions must not be null");
		Assert.notNull(options.getModel(), "VertexAiGeminiChatOptions.modelName must not be null");

		this.vertexAI = vertexAI;
		this.defaultOptions = options;
		this.generationConfig = toGenerationConfig(options);
	}

	// https://cloud.google.com/vertex-ai/docs/generative-ai/model-reference/gemini
	@Override
	public ChatResponse call(Prompt prompt) {

		var geminiRequest = createGeminiRequest(prompt);

		GenerateContentResponse response = this.getContentResponse(geminiRequest);

		List<Generation> generations = response.getCandidatesList()
			.stream()
			.map(this::responseCandiateToGeneration)
			.flatMap(List::stream)
			.toList();

		ChatResponse chatResponse = new ChatResponse(generations, toChatResponseMetadata(response));

		if (isToolCall(chatResponse, Set.of(FinishReason.STOP.name()))) {
			var toolCallConversation = handleToolCalls(prompt, chatResponse);
			// Recursively call the call method with the tool call message
			// conversation that contains the call responses.
			return this.call(new Prompt(toolCallConversation, prompt.getOptions()));
		}

		return chatResponse;
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		try {

			var request = createGeminiRequest(prompt);

			ResponseStream<GenerateContentResponse> responseStream = request.model
				.generateContentStream(request.contents);

			return Flux.fromStream(responseStream.stream()).switchMap(response -> {

				List<Generation> generations = response.getCandidatesList()
					.stream()
					.map(this::responseCandiateToGeneration)
					.flatMap(List::stream)
					.toList();

				ChatResponse chatResponse = new ChatResponse(generations, toChatResponseMetadata(response));

				if (isToolCall(chatResponse,
						Set.of(FinishReason.STOP.name(), FinishReason.FINISH_REASON_UNSPECIFIED.name()))) {
					var toolCallConversation = handleToolCalls(prompt, chatResponse);
					// Recursively call the stream method with the tool call message
					// conversation that contains the call responses.
					return this.stream(new Prompt(toolCallConversation, prompt.getOptions()));
				}

				return Flux.just(chatResponse);
			});
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to generate content", e);
		}
	}

	protected List<Generation> responseCandiateToGeneration(Candidate candidate) {

		// TODO - The candidateIndex (e.g. choice must be asigned to the generation).
		int candidateIndex = candidate.getIndex();
		FinishReason candidateFinishReasonn = candidate.getFinishReason();

		Map<String, Object> messageMetadata = Map.of("candidateIndex", candidateIndex, "finishReason",
				candidateFinishReasonn);

		ChatGenerationMetadata chatGenerationMetadata = ChatGenerationMetadata.from(candidateFinishReasonn.name(),
				null);

		boolean isFunctinCall = candidate.getContent().getPartsList().stream().allMatch(Part::hasFunctionCall);

		if (isFunctinCall) {
			List<AssistantMessage.ToolCall> assistantToolCalls = candidate.getContent()
				.getPartsList()
				.stream()
				.filter(part -> part.hasFunctionCall())
				.map(part -> {
					FunctionCall functionCall = part.getFunctionCall();
					var functionName = functionCall.getName();
					String functionArguments = structToJson(functionCall.getArgs());
					return new AssistantMessage.ToolCall("", "function", functionName, functionArguments);
				})
				.toList();

			AssistantMessage assistantMessage = new AssistantMessage("", messageMetadata, assistantToolCalls);

			return List.of(new Generation(assistantMessage, chatGenerationMetadata));
		}
		else {
			List<Generation> generations = candidate.getContent()
				.getPartsList()
				.stream()
				.map(part -> new AssistantMessage(part.getText(), messageMetadata))
				.map(assistantMessage -> new Generation(assistantMessage, chatGenerationMetadata))
				.toList();

			return generations;
		}
	}

	private ChatResponseMetadata toChatResponseMetadata(GenerateContentResponse response) {
		return ChatResponseMetadata.builder().withUsage(new VertexAiUsage(response.getUsageMetadata())).build();
	}

	@JsonInclude(Include.NON_NULL)
	public record GeminiRequest(List<Content> contents, GenerativeModel model) {
	}

	/**
	 * Tests access to the {@link #createGeminiRequest(Prompt)} method.
	 */
	GeminiRequest createGeminiRequest(Prompt prompt) {

		Set<String> functionsForThisRequest = new HashSet<>();

		GenerationConfig generationConfig = this.generationConfig;

		var generativeModelBuilder = new GenerativeModel.Builder().setModelName(this.defaultOptions.getModel())
			.setVertexAi(this.vertexAI);

		VertexAiGeminiChatOptions updatedRuntimeOptions = null;

		if (prompt.getOptions() != null) {
			updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
					VertexAiGeminiChatOptions.class);

			functionsForThisRequest
				.addAll(handleFunctionCallbackConfigurations(updatedRuntimeOptions, IS_RUNTIME_CALL));
		}

		if (this.defaultOptions != null) {

			functionsForThisRequest.addAll(handleFunctionCallbackConfigurations(this.defaultOptions, !IS_RUNTIME_CALL));

			if (updatedRuntimeOptions == null) {
				updatedRuntimeOptions = VertexAiGeminiChatOptions.builder().build();
			}

			updatedRuntimeOptions = ModelOptionsUtils.merge(updatedRuntimeOptions, this.defaultOptions,
					VertexAiGeminiChatOptions.class);

		}

		if (updatedRuntimeOptions != null) {

			if (StringUtils.hasText(updatedRuntimeOptions.getModel())
					&& !updatedRuntimeOptions.getModel().equals(this.defaultOptions.getModel())) {
				// Override model name
				generativeModelBuilder.setModelName(updatedRuntimeOptions.getModel());
			}

			generationConfig = toGenerationConfig(updatedRuntimeOptions);
		}

		// Add the enabled functions definitions to the request's tools parameter.
		if (!CollectionUtils.isEmpty(functionsForThisRequest)) {
			List<Tool> tools = this.getFunctionTools(functionsForThisRequest);
			generativeModelBuilder.setTools(tools);
		}

		generativeModelBuilder.setGenerationConfig(generationConfig);

		GenerativeModel generativeModel = generativeModelBuilder.build();

		List<Content> contents = toGeminiContent(
				prompt.getInstructions().stream().filter(m -> m.getMessageType() == MessageType.SYSTEM).toList());

		if (!CollectionUtils.isEmpty(contents)) {
			Assert.isTrue(contents.size() <= 1, "Only one system message is allowed in the prompt");
			generativeModel = generativeModel.withSystemInstruction(contents.get(0));
		}

		return new GeminiRequest(toGeminiContent(
				prompt.getInstructions().stream().filter(m -> m.getMessageType() != MessageType.SYSTEM).toList()),
				generativeModel);
	}

	private GenerationConfig toGenerationConfig(VertexAiGeminiChatOptions options) {

		GenerationConfig.Builder generationConfigBuilder = GenerationConfig.newBuilder();

		if (options.getTemperature() != null) {
			generationConfigBuilder.setTemperature(options.getTemperature());
		}
		if (options.getMaxOutputTokens() != null) {
			generationConfigBuilder.setMaxOutputTokens(options.getMaxOutputTokens());
		}
		if (options.getTopK() != null) {
			generationConfigBuilder.setTopK(options.getTopK());
		}
		if (options.getTopP() != null) {
			generationConfigBuilder.setTopP(options.getTopP());
		}
		if (options.getCandidateCount() != null) {
			generationConfigBuilder.setCandidateCount(options.getCandidateCount());
		}
		if (options.getStopSequences() != null) {
			generationConfigBuilder.addAllStopSequences(options.getStopSequences());
		}

		return generationConfigBuilder.build();
	}

	private List<Content> toGeminiContent(List<Message> instrucitons) {

		List<Content> contents = instrucitons.stream()
			.map(message -> Content.newBuilder()
				.setRole(toGeminiMessageType(message.getMessageType()).getValue())
				.addAllParts(messageToGeminiParts(message))
				.build())
			.toList();

		return contents;
	}

	private static GeminiMessageType toGeminiMessageType(@NonNull MessageType type) {

		Assert.notNull(type, "Message type must not be null");

		switch (type) {
			case SYSTEM:
			case USER:
			case TOOL:
				return GeminiMessageType.USER;
			case ASSISTANT:
				return GeminiMessageType.MODEL;
			default:
				throw new IllegalArgumentException("Unsupported message type: " + type);
		}
	}

	static List<Part> messageToGeminiParts(Message message) {

		if (message instanceof SystemMessage systemMessage) {

			List<Part> parts = new ArrayList<>();

			if (systemMessage.getContent() != null) {
				parts.add(Part.newBuilder().setText(systemMessage.getContent()).build());
			}

			return parts;
		}
		else if (message instanceof UserMessage userMessage) {
			List<Part> parts = new ArrayList<>();
			if (userMessage.getContent() != null) {
				parts.add(Part.newBuilder().setText(userMessage.getContent()).build());
			}

			parts.addAll(mediaToParts(userMessage.getMedia()));

			return parts;
		}
		else if (message instanceof AssistantMessage assistantMessage) {
			List<Part> parts = new ArrayList<>();
			if (StringUtils.hasText(assistantMessage.getContent())) {
				List.of(Part.newBuilder().setText(assistantMessage.getContent()).build());
			}
			if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
				parts.addAll(assistantMessage.getToolCalls()
					.stream()
					.map(toolCall -> Part.newBuilder()
						.setFunctionCall(FunctionCall.newBuilder()
							.setName(toolCall.name())
							.setArgs(jsonToStruct(toolCall.arguments()))
							.build())
						.build())
					.toList());
			}
			return parts;
		}
		else if (message instanceof ToolResponseMessage toolResponseMessage) {

			return toolResponseMessage.getResponses()
				.stream()
				.map(response -> Part.newBuilder()
					.setFunctionResponse(FunctionResponse.newBuilder()
						.setName(response.name())
						.setResponse(jsonToStruct(response.responseData()))
						.build())
					.build())
				.toList();
		}
		else {
			throw new IllegalArgumentException("Gemini doesn't support message type: " + message.getClass());
		}
	}

	private static List<Part> mediaToParts(Collection<Media> media) {
		List<Part> parts = new ArrayList<>();

		List<Part> mediaParts = media.stream()
			.map(mediaData -> PartMaker.fromMimeTypeAndData(mediaData.getMimeType().toString(), mediaData.getData()))
			.toList();

		if (!CollectionUtils.isEmpty(mediaParts)) {
			parts.addAll(mediaParts);
		}

		return parts;
	}

	private List<Tool> getFunctionTools(Set<String> functionNames) {

		final var tool = Tool.newBuilder();

		final List<FunctionDeclaration> functionDeclarations = this.resolveFunctionCallbacks(functionNames)
			.stream()
			.map(functionCallback -> FunctionDeclaration.newBuilder()
				.setName(functionCallback.getName())
				.setDescription(functionCallback.getDescription())
				.setParameters(jsonToSchema(functionCallback.getInputTypeSchema()))
				.build())
			.toList();
		tool.addAllFunctionDeclarations(functionDeclarations);
		return List.of(tool.build());
	}

	private static String structToJson(Struct struct) {
		try {
			return JsonFormat.printer().print(struct);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static Struct jsonToStruct(String json) {
		try {
			var structBuilder = Struct.newBuilder();
			JsonFormat.parser().ignoringUnknownFields().merge(json, structBuilder);
			return structBuilder.build();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static Schema jsonToSchema(String json) {
		try {
			var schemaBuilder = Schema.newBuilder();
			JsonFormat.parser().ignoringUnknownFields().merge(json, schemaBuilder);
			return schemaBuilder.build();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private GenerateContentResponse getContentResponse(GeminiRequest request) {
		try {
			return request.model.generateContent(request.contents);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to generate content", e);
		}
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return VertexAiGeminiChatOptions.fromOptions(this.defaultOptions);
	}

	@Override
	public void destroy() throws Exception {
		if (this.vertexAI != null) {
			this.vertexAI.close();
		}
	}

}
