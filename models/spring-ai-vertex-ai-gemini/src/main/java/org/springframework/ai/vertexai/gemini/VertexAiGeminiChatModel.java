/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.vertexai.gemini;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
import com.google.cloud.vertexai.api.GoogleSearchRetrieval;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.api.SafetySetting;
import com.google.cloud.vertexai.api.Schema;
import com.google.cloud.vertexai.api.Tool;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.PartMaker;
import com.google.cloud.vertexai.generativeai.ResponseStream;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.AbstractToolCallSupport;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ChatModelDescription;
import org.springframework.ai.model.Media;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackResolver;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.ai.model.tool.LegacyToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.vertexai.gemini.common.VertexAiGeminiConstants;
import org.springframework.ai.vertexai.gemini.common.VertexAiGeminiSafetySetting;
import org.springframework.ai.vertexai.gemini.schema.VertexToolCallingManager;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.lang.NonNull;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Vertex AI Gemini Chat Model implementation.
 *
 * @author Christian Tzolov
 * @author Grogdunn
 * @author luocongqiu
 * @author Chris Turchin
 * @author Mark Pollack
 * @author Soby Chacko
 * @author Jihoon Kim
 * @author Alexandros Pappas
 * @since 0.8.1
 */
public class VertexAiGeminiChatModel extends AbstractToolCallSupport implements ChatModel, DisposableBean {

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private static final ToolCallingManager DEFAULT_TOOL_CALLING_MANAGER = ToolCallingManager.builder().build();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final VertexAI vertexAI;

	private final VertexAiGeminiChatOptions defaultOptions;

	/**
	 * The retry template used to retry the API calls.
	 */
	private final RetryTemplate retryTemplate;

	private final GenerationConfig generationConfig;

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	/**
	 * Tool calling manager used to call tools.
	 */
	private final ToolCallingManager toolCallingManager;

	/**
	 * Conventions to use for generating observations.
	 */
	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	/**
	 * @deprecated Use {@link VertexAiGeminiChatModel.Builder}.
	 */
	@Deprecated
	public VertexAiGeminiChatModel(VertexAI vertexAI) {
		this(vertexAI, VertexAiGeminiChatOptions.builder().model(ChatModel.GEMINI_1_5_PRO).temperature(0.8).build());
	}

	/**
	 * @deprecated Use {@link VertexAiGeminiChatModel.Builder}.
	 */
	@Deprecated
	public VertexAiGeminiChatModel(VertexAI vertexAI, VertexAiGeminiChatOptions options) {
		this(vertexAI, options, null);
	}

	/**
	 * @deprecated Use {@link VertexAiGeminiChatModel.Builder}.
	 */
	@Deprecated
	public VertexAiGeminiChatModel(VertexAI vertexAI, VertexAiGeminiChatOptions options,
			FunctionCallbackResolver functionCallbackResolver) {
		this(vertexAI, options, functionCallbackResolver, List.of());
	}

	/**
	 * @deprecated Use {@link VertexAiGeminiChatModel.Builder}.
	 */
	@Deprecated
	public VertexAiGeminiChatModel(VertexAI vertexAI, VertexAiGeminiChatOptions options,
			FunctionCallbackResolver functionCallbackResolver, List<FunctionCallback> toolFunctionCallbacks) {
		this(vertexAI, options, functionCallbackResolver, toolFunctionCallbacks, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * @deprecated Use {@link VertexAiGeminiChatModel.Builder}.
	 */
	@Deprecated
	public VertexAiGeminiChatModel(VertexAI vertexAI, VertexAiGeminiChatOptions options,
			FunctionCallbackResolver functionCallbackResolver, List<FunctionCallback> toolFunctionCallbacks,
			RetryTemplate retryTemplate) {
		this(vertexAI, options, functionCallbackResolver, toolFunctionCallbacks, retryTemplate,
				ObservationRegistry.NOOP);
	}

	/**
	 * @deprecated Use {@link VertexAiGeminiChatModel.Builder}.
	 */
	@Deprecated
	public VertexAiGeminiChatModel(VertexAI vertexAI, VertexAiGeminiChatOptions options,
			FunctionCallbackResolver functionCallbackResolver, List<FunctionCallback> toolFunctionCallbacks,
			RetryTemplate retryTemplate, ObservationRegistry observationRegistry) {

		this(vertexAI, options,
				LegacyToolCallingManager.builder()
					.functionCallbackResolver(functionCallbackResolver)
					.functionCallbacks(toolFunctionCallbacks)
					.build(),
				retryTemplate, observationRegistry);
		logger.warn("This constructor is deprecated and will be removed in the next milestone. "
				+ "Please use the new constructor accepting ToolCallingManager instead.");

	}

	public VertexAiGeminiChatModel(VertexAI vertexAI, VertexAiGeminiChatOptions defaultOptions,
			ToolCallingManager toolCallingManager, RetryTemplate retryTemplate,
			ObservationRegistry observationRegistry) {

		super(null, VertexAiGeminiChatOptions.builder().build(), List.of());

		Assert.notNull(vertexAI, "VertexAI must not be null");
		Assert.notNull(defaultOptions, "VertexAiGeminiChatOptions must not be null");
		Assert.notNull(defaultOptions.getModel(), "VertexAiGeminiChatOptions.modelName must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");
		Assert.notNull(toolCallingManager, "ToolCallingManager must not be null");

		this.vertexAI = vertexAI;
		this.defaultOptions = defaultOptions;
		this.generationConfig = toGenerationConfig(defaultOptions);
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;

		if (toolCallingManager instanceof VertexToolCallingManager) {
			this.toolCallingManager = toolCallingManager;
		}
		else {
			this.toolCallingManager = new VertexToolCallingManager(toolCallingManager);
		}
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

			if (systemMessage.getText() != null) {
				parts.add(Part.newBuilder().setText(systemMessage.getText()).build());
			}

			return parts;
		}
		else if (message instanceof UserMessage userMessage) {
			List<Part> parts = new ArrayList<>();
			if (userMessage.getText() != null) {
				parts.add(Part.newBuilder().setText(userMessage.getText()).build());
			}

			parts.addAll(mediaToParts(userMessage.getMedia()));

			return parts;
		}
		else if (message instanceof AssistantMessage assistantMessage) {
			List<Part> parts = new ArrayList<>();
			if (StringUtils.hasText(assistantMessage.getText())) {
				parts.add(Part.newBuilder().setText(assistantMessage.getText()).build());
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

	// https://cloud.google.com/vertex-ai/docs/generative-ai/model-reference/gemini
	@Override
	public ChatResponse call(Prompt prompt) {
		var requestPrompt = this.buildRequestPrompt(prompt);
		return this.internalCall(requestPrompt);
	}

	private ChatResponse internalCall(Prompt prompt) {

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(prompt)
			.provider(VertexAiGeminiConstants.PROVIDER_NAME)
			.requestOptions(prompt.getOptions())
			.build();

		ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> this.retryTemplate.execute(context -> {

				var geminiRequest = createGeminiRequest(prompt);

				GenerateContentResponse generateContentResponse = this.getContentResponse(geminiRequest);

				List<Generation> generations = generateContentResponse.getCandidatesList()
					.stream()
					.map(this::responseCandidateToGeneration)
					.flatMap(List::stream)
					.toList();

				ChatResponse chatResponse = new ChatResponse(generations,
						toChatResponseMetadata(generateContentResponse));

				observationContext.setResponse(chatResponse);
				return chatResponse;
			}));

		if (ToolCallingChatOptions.isInternalToolExecutionEnabled(prompt.getOptions()) && response != null
				&& response.hasToolCalls()) {
			var toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, response);
			if (toolExecutionResult.returnDirect()) {
				// Return tool execution result directly to the client.
				return ChatResponse.builder()
					.from(response)
					.generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
					.build();
			}
			else {
				// Send the tool execution result back to the model.
				return this.internalCall(new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()));
			}
		}

		return response;

	}

	Prompt buildRequestPrompt(Prompt prompt) {
		// Process runtime options
		VertexAiGeminiChatOptions runtimeOptions = null;
		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
				runtimeOptions = ModelOptionsUtils.copyToTarget(toolCallingChatOptions, ToolCallingChatOptions.class,
						VertexAiGeminiChatOptions.class);
			}
			else if (prompt.getOptions() instanceof FunctionCallingOptions functionCallingOptions) {
				runtimeOptions = ModelOptionsUtils.copyToTarget(functionCallingOptions, FunctionCallingOptions.class,
						VertexAiGeminiChatOptions.class);
			}
			else {
				runtimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
						VertexAiGeminiChatOptions.class);
			}
		}

		// Define request options by merging runtime options and default options
		VertexAiGeminiChatOptions requestOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions,
				VertexAiGeminiChatOptions.class);

		// Merge @JsonIgnore-annotated options explicitly since they are ignored by
		// Jackson, used by ModelOptionsUtils.
		if (runtimeOptions != null) {
			requestOptions.setInternalToolExecutionEnabled(
					ModelOptionsUtils.mergeOption(runtimeOptions.isInternalToolExecutionEnabled(),
							this.defaultOptions.isInternalToolExecutionEnabled()));
			requestOptions.setToolNames(ToolCallingChatOptions.mergeToolNames(runtimeOptions.getToolNames(),
					this.defaultOptions.getToolNames()));
			requestOptions.setToolCallbacks(ToolCallingChatOptions.mergeToolCallbacks(runtimeOptions.getToolCallbacks(),
					this.defaultOptions.getToolCallbacks()));
			requestOptions.setToolContext(ToolCallingChatOptions.mergeToolContext(runtimeOptions.getToolContext(),
					this.defaultOptions.getToolContext()));

			requestOptions.setGoogleSearchRetrieval(ModelOptionsUtils.mergeOption(
					runtimeOptions.getGoogleSearchRetrieval(), this.defaultOptions.getGoogleSearchRetrieval()));
			requestOptions.setSafetySettings(ModelOptionsUtils.mergeOption(runtimeOptions.getSafetySettings(),
					this.defaultOptions.getSafetySettings()));
		}
		else {
			requestOptions.setInternalToolExecutionEnabled(this.defaultOptions.isInternalToolExecutionEnabled());
			requestOptions.setToolNames(this.defaultOptions.getToolNames());
			requestOptions.setToolCallbacks(this.defaultOptions.getToolCallbacks());
			requestOptions.setToolContext(this.defaultOptions.getToolContext());

			requestOptions.setGoogleSearchRetrieval(this.defaultOptions.getGoogleSearchRetrieval());
			requestOptions.setSafetySettings(this.defaultOptions.getSafetySettings());
		}

		ToolCallingChatOptions.validateToolCallbacks(requestOptions.getToolCallbacks());

		return new Prompt(prompt.getInstructions(), requestOptions);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		var requestPrompt = this.buildRequestPrompt(prompt);
		return this.internalStream(requestPrompt);
	}

	public Flux<ChatResponse> internalStream(Prompt prompt) {
		return Flux.deferContextual(contextView -> {

			ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(prompt)
				.provider(VertexAiGeminiConstants.PROVIDER_NAME)
				.requestOptions(prompt.getOptions())
				.build();

			Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
					this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			var request = createGeminiRequest(prompt);

			try {
				ResponseStream<GenerateContentResponse> responseStream = request.model
					.generateContentStream(request.contents);

				Flux<ChatResponse> chatResponse1 = Flux.fromStream(responseStream.stream())
					.switchMap(response2 -> Mono.just(response2).map(response -> {

						List<Generation> generations = response.getCandidatesList()
							.stream()
							.map(this::responseCandidateToGeneration)
							.flatMap(List::stream)
							.toList();

						return new ChatResponse(generations, toChatResponseMetadata(response));

					}));

				// @formatter:off
				Flux<ChatResponse> chatResponseFlux = chatResponse1.flatMap(response -> {					
					if (ToolCallingChatOptions.isInternalToolExecutionEnabled(prompt.getOptions()) && response.hasToolCalls()) {
						var toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, response);
						if (toolExecutionResult.returnDirect()) {
							// Return tool execution result directly to the client.
							return Flux.just(ChatResponse.builder().from(response)
									.generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
									.build());
						} else {
							// Send the tool execution result back to the model.
							return this.internalStream(new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()));
						}
					}
					else {
						return Flux.just(response);
					}
				})
				.doOnError(observation::error)
				.doFinally(s -> observation.stop())
				.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
				// @formatter:on;

				return new MessageAggregator().aggregate(chatResponseFlux, observationContext::setResponse);

			}
			catch (Exception e) {
				throw new RuntimeException("Failed to generate content", e);
			}

		});
	}

	protected List<Generation> responseCandidateToGeneration(Candidate candidate) {

		// TODO - The candidateIndex (e.g. choice must be asigned to the generation).
		int candidateIndex = candidate.getIndex();
		FinishReason candidateFinishReason = candidate.getFinishReason();

		Map<String, Object> messageMetadata = Map.of("candidateIndex", candidateIndex, "finishReason",
				candidateFinishReason);

		ChatGenerationMetadata chatGenerationMetadata = ChatGenerationMetadata.builder()
			.finishReason(candidateFinishReason.name())
			.build();

		boolean isFunctionCall = candidate.getContent().getPartsList().stream().allMatch(Part::hasFunctionCall);

		if (isFunctionCall) {
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
		return ChatResponseMetadata.builder().usage(getDefaultUsage(response.getUsageMetadata())).build();
	}

	private DefaultUsage getDefaultUsage(GenerateContentResponse.UsageMetadata usageMetadata) {
		return new DefaultUsage(usageMetadata.getPromptTokenCount(), usageMetadata.getCandidatesTokenCount(),
				usageMetadata.getTotalTokenCount(), usageMetadata);
	}

	private VertexAiGeminiChatOptions vertexAiGeminiChatOptions(Prompt prompt) {
		VertexAiGeminiChatOptions updatedRuntimeOptions = VertexAiGeminiChatOptions.builder().build();
		if (prompt.getOptions() != null) {
			updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
					VertexAiGeminiChatOptions.class);

		}

		updatedRuntimeOptions = ModelOptionsUtils.merge(updatedRuntimeOptions, this.defaultOptions,
				VertexAiGeminiChatOptions.class);

		return updatedRuntimeOptions;
	}

	GeminiRequest createGeminiRequest(Prompt prompt) {

		VertexAiGeminiChatOptions requestOptions = (VertexAiGeminiChatOptions) prompt.getOptions();

		var generativeModelBuilder = new GenerativeModel.Builder().setVertexAi(this.vertexAI)
			.setSafetySettings(toGeminiSafetySettings(requestOptions.getSafetySettings()));

		if (requestOptions.getModel() != null) {
			generativeModelBuilder.setModelName(requestOptions.getModel());
		}
		else {
			generativeModelBuilder.setModelName(this.defaultOptions.getModel());
		}

		GenerationConfig generationConfig = this.generationConfig;

		if (requestOptions != null) {
			generationConfig = toGenerationConfig(requestOptions);
		}

		// Add the enabled functions definitions to the request's tools parameter.
		List<Tool> tools = new ArrayList<>();
		List<ToolDefinition> toolDefinitions = this.toolCallingManager.resolveToolDefinitions(requestOptions);
		if (!CollectionUtils.isEmpty(toolDefinitions)) {
			final List<FunctionDeclaration> functionDeclarations = toolDefinitions.stream()
				.map(toolDefinition -> FunctionDeclaration.newBuilder()
					.setName(toolDefinition.name())
					.setDescription(toolDefinition.description())
					.setParameters(jsonToSchema(toolDefinition.inputSchema()))
					.build())
				.toList();
			tools.add(Tool.newBuilder().addAllFunctionDeclarations(functionDeclarations).build());
		}

		if (prompt.getOptions() instanceof VertexAiGeminiChatOptions options && options.getGoogleSearchRetrieval()) {
			final var googleSearchRetrieval = GoogleSearchRetrieval.newBuilder().getDefaultInstanceForType();
			final var googleSearchRetrievalTool = Tool.newBuilder()
				.setGoogleSearchRetrieval(googleSearchRetrieval)
				.build();
			tools.add(googleSearchRetrievalTool);
		}

		if (!CollectionUtils.isEmpty(tools)) {
			generativeModelBuilder.setTools(tools);
		}

		if (!CollectionUtils.isEmpty(requestOptions.getSafetySettings())) {
			generativeModelBuilder.setSafetySettings(toGeminiSafetySettings(requestOptions.getSafetySettings()));
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
			generationConfigBuilder.setTemperature(options.getTemperature().floatValue());
		}
		if (options.getMaxOutputTokens() != null) {
			generationConfigBuilder.setMaxOutputTokens(options.getMaxOutputTokens());
		}
		if (options.getTopK() != null) {
			generationConfigBuilder.setTopK(options.getTopK());
		}
		if (options.getTopP() != null) {
			generationConfigBuilder.setTopP(options.getTopP().floatValue());
		}
		if (options.getCandidateCount() != null) {
			generationConfigBuilder.setCandidateCount(options.getCandidateCount());
		}
		if (options.getStopSequences() != null) {
			generationConfigBuilder.addAllStopSequences(options.getStopSequences());
		}
		if (options.getResponseMimeType() != null) {
			generationConfigBuilder.setResponseMimeType(options.getResponseMimeType());
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

	private List<SafetySetting> toGeminiSafetySettings(List<VertexAiGeminiSafetySetting> safetySettings) {
		return safetySettings.stream()
			.map(safetySetting -> SafetySetting.newBuilder()
				.setCategoryValue(safetySetting.getCategory().getValue())
				.setThresholdValue(safetySetting.getThreshold().getValue())
				.setMethodValue(safetySetting.getMethod().getValue())
				.build())
			.toList();
	}

	/**
	 * Generates the content response based on the provided Gemini request. Package
	 * protected for testing purposes.
	 * @param request the GeminiRequest containing the content and model information
	 * @return a GenerateContentResponse containing the generated content
	 * @throws RuntimeException if content generation fails
	 */
	GenerateContentResponse getContentResponse(GeminiRequest request) {
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

	/**
	 * Use the provided convention for reporting observation data
	 * @param observationConvention The provided convention
	 */
	public void setObservationConvention(ChatModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	public enum GeminiMessageType {

		USER("user"),

		MODEL("model");

		public final String value;

		GeminiMessageType(String value) {
			this.value = value;
		}

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

		GEMINI_1_5_PRO("gemini-1.5-pro-002"),

		GEMINI_1_5_FLASH("gemini-1.5-flash-002"),

		GEMINI_1_5_FLASH_8B("gemini-1.5-flash-8b-001"),

		GEMINI_2_0_FLASH("gemini-2.0-flash"),

		GEMINI_2_0_FLASH_LIGHT("gemini-2.0-flash-lite-preview-02-05");

		public final String value;

		ChatModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

		@Override
		public String getName() {
			return this.value;
		}

	}

	@JsonInclude(Include.NON_NULL)
	public record GeminiRequest(List<Content> contents, GenerativeModel model) {

	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private VertexAI vertexAI;

		private VertexAiGeminiChatOptions defaultOptions = VertexAiGeminiChatOptions.builder()
			.temperature(0.7)
			.topP(1.0)
			.model(VertexAiGeminiChatModel.ChatModel.GEMINI_2_0_FLASH)
			.build();

		private ToolCallingManager toolCallingManager;

		private FunctionCallbackResolver functionCallbackResolver;

		private List<FunctionCallback> toolFunctionCallbacks;

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private Builder() {
		}

		public Builder vertexAI(VertexAI vertexAI) {
			this.vertexAI = vertexAI;
			return this;
		}

		public Builder defaultOptions(VertexAiGeminiChatOptions defaultOptions) {
			this.defaultOptions = defaultOptions;
			return this;
		}

		public Builder toolCallingManager(ToolCallingManager toolCallingManager) {
			this.toolCallingManager = toolCallingManager;
			return this;
		}

		@Deprecated
		public Builder functionCallbackResolver(FunctionCallbackResolver functionCallbackResolver) {
			this.functionCallbackResolver = functionCallbackResolver;
			return this;
		}

		@Deprecated
		public Builder toolFunctionCallbacks(List<FunctionCallback> toolFunctionCallbacks) {
			this.toolFunctionCallbacks = toolFunctionCallbacks;
			return this;
		}

		public Builder retryTemplate(RetryTemplate retryTemplate) {
			this.retryTemplate = retryTemplate;
			return this;
		}

		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		public VertexAiGeminiChatModel build() {
			if (toolCallingManager != null) {
				Assert.isNull(functionCallbackResolver,
						"functionCallbackResolver cannot be set when toolCallingManager is set");
				Assert.isNull(toolFunctionCallbacks,
						"toolFunctionCallbacks cannot be set when toolCallingManager is set");

				return new VertexAiGeminiChatModel(vertexAI, defaultOptions, toolCallingManager, retryTemplate,
						observationRegistry);
			}

			if (functionCallbackResolver != null) {
				Assert.isNull(toolCallingManager,
						"toolCallingManager cannot be set when functionCallbackResolver is set");
				List<FunctionCallback> toolCallbacks = this.toolFunctionCallbacks != null ? this.toolFunctionCallbacks
						: List.of();

				return new VertexAiGeminiChatModel(vertexAI, defaultOptions, functionCallbackResolver, toolCallbacks,
						retryTemplate, observationRegistry);
			}

			return new VertexAiGeminiChatModel(vertexAI, defaultOptions, DEFAULT_TOOL_CALLING_MANAGER, retryTemplate,
					observationRegistry);
		}

	}

}
