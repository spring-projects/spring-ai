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
import com.fasterxml.jackson.databind.JsonNode;
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
import com.google.cloud.vertexai.api.SafetySetting;
import com.google.cloud.vertexai.api.Schema;
import com.google.cloud.vertexai.api.Tool;
import com.google.cloud.vertexai.api.Tool.GoogleSearch;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.PartMaker;
import com.google.cloud.vertexai.generativeai.ResponseStream;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.support.UsageCalculator;
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
import org.springframework.ai.content.Media;
import org.springframework.ai.model.ChatModelDescription;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
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
 * Vertex AI Gemini Chat Model implementation that provides access to Google's Gemini
 * language models.
 *
 * <p>
 * Key features include:
 * <ul>
 * <li>Support for multiple Gemini model versions including Gemini Pro, Gemini 1.5 Pro,
 * Gemini 1.5/2.0 Flash variants</li>
 * <li>Tool/Function calling capabilities through {@link ToolCallingManager}</li>
 * <li>Streaming support via {@link #stream(Prompt)} method</li>
 * <li>Configurable safety settings through {@link VertexAiGeminiSafetySetting}</li>
 * <li>Support for system messages and multi-modal content (text and images)</li>
 * <li>Built-in retry mechanism and observability through Micrometer</li>
 * <li>Google Search Retrieval integration</li>
 * </ul>
 *
 * <p>
 * The model can be configured with various options including temperature, top-k, top-p
 * sampling, maximum output tokens, and candidate count through
 * {@link VertexAiGeminiChatOptions}.
 *
 * <p>
 * Use the {@link Builder} to create instances with custom configurations:
 *
 * <pre>{@code
 * VertexAiGeminiChatModel model = VertexAiGeminiChatModel.builder()
 * 		.vertexAI(vertexAI)
 * 		.defaultOptions(options)
 * 		.toolCallingManager(toolManager)
 * 		.build();
 * }</pre>
 *
 * @author Christian Tzolov
 * @author Grogdunn
 * @author luocongqiu
 * @author Chris Turchin
 * @author Mark Pollack
 * @author Soby Chacko
 * @author Jihoon Kim
 * @author Alexandros Pappas
 * @author Ilayaperumal Gopinathan
 * @since 0.8.1
 * @see VertexAiGeminiChatOptions
 * @see ToolCallingManager
 * @see ChatModel
 */
public class VertexAiGeminiChatModel implements ChatModel, DisposableBean {

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
	 * The tool execution eligibility predicate used to determine if a tool can be
	 * executed.
	 */
	private final ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate;

	/**
	 * Conventions to use for generating observations.
	 */
	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	/**
	 * Creates a new instance of VertexAiGeminiChatModel.
	 * @param vertexAI the Vertex AI instance to use
	 * @param defaultOptions the default options to use
	 * @param toolCallingManager the tool calling manager to use. It is wrapped in a
	 * {@link VertexToolCallingManager} to ensure compatibility with Vertex AI's OpenAPI
	 * schema format.
	 * @param retryTemplate the retry template to use
	 * @param observationRegistry the observation registry to use
	 */
	public VertexAiGeminiChatModel(VertexAI vertexAI, VertexAiGeminiChatOptions defaultOptions,
			ToolCallingManager toolCallingManager, RetryTemplate retryTemplate,
			ObservationRegistry observationRegistry) {
		this(vertexAI, defaultOptions, toolCallingManager, retryTemplate, observationRegistry,
				new DefaultToolExecutionEligibilityPredicate());
	}

	/**
	 * Creates a new instance of VertexAiGeminiChatModel.
	 * @param vertexAI the Vertex AI instance to use
	 * @param defaultOptions the default options to use
	 * @param toolCallingManager the tool calling manager to use. It is wrapped in a
	 * {@link VertexToolCallingManager} to ensure compatibility with Vertex AI's OpenAPI
	 * schema format.
	 * @param retryTemplate the retry template to use
	 * @param observationRegistry the observation registry to use
	 * @param toolExecutionEligibilityPredicate the tool execution eligibility predicate
	 */
	public VertexAiGeminiChatModel(VertexAI vertexAI, VertexAiGeminiChatOptions defaultOptions,
			ToolCallingManager toolCallingManager, RetryTemplate retryTemplate, ObservationRegistry observationRegistry,
			ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {

		Assert.notNull(vertexAI, "VertexAI must not be null");
		Assert.notNull(defaultOptions, "VertexAiGeminiChatOptions must not be null");
		Assert.notNull(defaultOptions.getModel(), "VertexAiGeminiChatOptions.modelName must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");
		Assert.notNull(toolCallingManager, "ToolCallingManager must not be null");
		Assert.notNull(toolExecutionEligibilityPredicate, "ToolExecutionEligibilityPredicate must not be null");

		this.vertexAI = vertexAI;
		this.defaultOptions = defaultOptions;
		this.generationConfig = toGenerationConfig(defaultOptions);
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
		this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;

		// Wrap the provided tool calling manager in a VertexToolCallingManager to
		// ensure
		// compatibility with Vertex AI's OpenAPI schema format.
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
			JsonNode rootNode = ModelOptionsUtils.OBJECT_MAPPER.readTree(json);

			Struct.Builder structBuilder = Struct.newBuilder();

			if (rootNode.isTextual()) {
				structBuilder.putFields("result", Value.newBuilder().setStringValue(json).build());
			}
			else if (rootNode.isArray()) {
				// Handle JSON array
				List<Value> values = new ArrayList<>();

				for (JsonNode element : rootNode) {
					String elementJson = element.toString();
					Struct.Builder elementBuilder = Struct.newBuilder();
					JsonFormat.parser().ignoringUnknownFields().merge(elementJson, elementBuilder);

					// Add each parsed object as a value in an array field
					values.add(Value.newBuilder().setStructValue(elementBuilder.build()).build());
				}

				// Add the array to the main struct with a field name like "items"
				structBuilder.putFields("items",
						Value.newBuilder()
							.setListValue(com.google.protobuf.ListValue.newBuilder().addAllValues(values).build())
							.build());
			}
			else {
				// Original behavior for single JSON object
				JsonFormat.parser().ignoringUnknownFields().merge(json, structBuilder);
			}

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
		return this.internalCall(requestPrompt, null);
	}

	private ChatResponse internalCall(Prompt prompt, ChatResponse previousChatResponse) {

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(prompt)
			.provider(VertexAiGeminiConstants.PROVIDER_NAME)
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

				GenerateContentResponse.UsageMetadata usage = generateContentResponse.getUsageMetadata();
				Usage currentUsage = (usage != null)
						? new DefaultUsage(usage.getPromptTokenCount(), usage.getCandidatesTokenCount())
						: new EmptyUsage();
				Usage cumulativeUsage = UsageCalculator.getCumulativeUsage(currentUsage, previousChatResponse);
				ChatResponse chatResponse = new ChatResponse(generations, toChatResponseMetadata(cumulativeUsage));

				observationContext.setResponse(chatResponse);
				return chatResponse;
			}));

		if (this.toolExecutionEligibilityPredicate.isToolExecutionRequired(prompt.getOptions(), response)) {
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
				return this.internalCall(new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()),
						response);
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
					ModelOptionsUtils.mergeOption(runtimeOptions.getInternalToolExecutionEnabled(),
							this.defaultOptions.getInternalToolExecutionEnabled()));
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
			requestOptions.setInternalToolExecutionEnabled(this.defaultOptions.getInternalToolExecutionEnabled());
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
		return this.internalStream(requestPrompt, null);
	}

	public Flux<ChatResponse> internalStream(Prompt prompt, ChatResponse previousChatResponse) {
		return Flux.deferContextual(contextView -> {

			ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(prompt)
				.provider(VertexAiGeminiConstants.PROVIDER_NAME)
				.build();

			Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
					this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			var request = createGeminiRequest(prompt);

			try {
				ResponseStream<GenerateContentResponse> responseStream = request.model
					.generateContentStream(request.contents);

				Flux<ChatResponse> chatResponseFlux = Flux.fromStream(responseStream.stream()).switchMap(response -> {
					List<Generation> generations = response.getCandidatesList()
						.stream()
						.map(this::responseCandidateToGeneration)
						.flatMap(List::stream)
						.toList();

					GenerateContentResponse.UsageMetadata usage = response.getUsageMetadata();
					Usage currentUsage = (usage != null) ? getDefaultUsage(usage) : new EmptyUsage();
					Usage cumulativeUsage = UsageCalculator.getCumulativeUsage(currentUsage, previousChatResponse);
					ChatResponse chatResponse = new ChatResponse(generations, toChatResponseMetadata(cumulativeUsage));
					return Flux.just(chatResponse);
				});

				// @formatter:off
				Flux<ChatResponse> flux = chatResponseFlux.flatMap(response -> {
					if (this.toolExecutionEligibilityPredicate.isToolExecutionRequired(prompt.getOptions(), response)) {
						// FIXME: bounded elastic needs to be used since tool calling
						// is currently only synchronous
						return Flux.defer(() -> {
							var toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, response);
							if (toolExecutionResult.returnDirect()) {
								// Return tool execution result directly to the client.
								return Flux.just(ChatResponse.builder().from(response)
										.generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
										.build());
							}
							else {
								// Send the tool execution result back to the model.
								return this.internalStream(new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()), response);
							}
						}).subscribeOn(Schedulers.boundedElastic());
					}
					else {
						return Flux.just(response);
					}
				})
				.doOnError(observation::error)
				.doFinally(s -> observation.stop())
				.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
				// @formatter:on;

				return new MessageAggregator().aggregate(flux, observationContext::setResponse);

			}
			catch (Exception e) {
				throw new RuntimeException("Failed to generate content", e);
			}

		});
	}

	protected List<Generation> responseCandidateToGeneration(Candidate candidate) {

		// TODO - The candidateIndex (e.g. choice must be assigned to the generation).
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

	private ChatResponseMetadata toChatResponseMetadata(Usage usage) {
		return ChatResponseMetadata.builder().usage(usage).build();
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
			var googleSearch = GoogleSearch.newBuilder().getDefaultInstanceForType();
			final var googleSearchRetrievalTool = Tool.newBuilder().setGoogleSearch(googleSearch).build();
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
		if (options.getFrequencyPenalty() != null) {
			generationConfigBuilder.setFrequencyPenalty(options.getFrequencyPenalty().floatValue());
		}
		if (options.getPresencePenalty() != null) {
			generationConfigBuilder.setPresencePenalty(options.getPresencePenalty().floatValue());
		}

		return generationConfigBuilder.build();
	}

	private List<Content> toGeminiContent(List<Message> instructions) {

		List<Content> contents = instructions.stream()
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

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private VertexAI vertexAI;

		private VertexAiGeminiChatOptions defaultOptions = VertexAiGeminiChatOptions.builder()
			.temperature(0.7)
			.topP(1.0)
			.model(VertexAiGeminiChatModel.ChatModel.GEMINI_2_0_FLASH)
			.build();

		private ToolCallingManager toolCallingManager;

		private ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate = new DefaultToolExecutionEligibilityPredicate();

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

		public Builder toolExecutionEligibilityPredicate(
				ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {
			this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
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
			if (this.toolCallingManager != null) {
				return new VertexAiGeminiChatModel(this.vertexAI, this.defaultOptions, this.toolCallingManager,
						this.retryTemplate, this.observationRegistry, this.toolExecutionEligibilityPredicate);
			}
			return new VertexAiGeminiChatModel(this.vertexAI, this.defaultOptions, DEFAULT_TOOL_CALLING_MANAGER,
					this.retryTemplate, this.observationRegistry, this.toolExecutionEligibilityPredicate);
		}

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
		 * <b>gemini-1.5-pro</b> is recommended to upgrade to <b>gemini-2.0-flash</b>
		 * <p>
		 * Discontinuation date: September 24, 2025
		 * <p>
		 * See: <a href=
		 * "https://cloud.google.com/vertex-ai/generative-ai/docs/learn/model-versions#stable-version">stable-version</a>
		 */
		GEMINI_1_5_PRO("gemini-1.5-pro-002"),

		/**
		 * <b>gemini-1.5-flash</b> is recommended to upgrade to
		 * <b>gemini-2.0-flash-lite</b>
		 * <p>
		 * Discontinuation date: September 24, 2025
		 * <p>
		 * See: <a href=
		 * "https://cloud.google.com/vertex-ai/generative-ai/docs/learn/model-versions#stable-version">stable-version</a>
		 */
		GEMINI_1_5_FLASH("gemini-1.5-flash-002"),

		/**
		 * <b>gemini-2.0-flash</b> delivers next-gen features and improved capabilities,
		 * including superior speed, built-in tool use, multimodal generation, and a 1M
		 * token context window.
		 * <p>
		 * Inputs: Text, Code, Images, Audio, Video - 1,048,576 tokens | Outputs: Text,
		 * Audio(Experimental), Images(Experimental) - 8,192 tokens
		 * <p>
		 * Knowledge cutoff: June 2024
		 * <p>
		 * Model ID: gemini-2.0-flash
		 * <p>
		 * See: <a href=
		 * "https://cloud.google.com/vertex-ai/generative-ai/docs/models/gemini/2-0-flash">gemini-2.0-flash</a>
		 */
		GEMINI_2_0_FLASH("gemini-2.0-flash"),

		/**
		 * <b>gemini-2.0-flash-lite</b> is the fastest and most cost efficient Flash
		 * model. It's an upgrade path for 1.5 Flash users who want better quality for the
		 * same price and speed.
		 * <p>
		 * Inputs: Text, Code, Images, Audio, Video - 1,048,576 tokens | Outputs: Text -
		 * 8,192 tokens
		 * <p>
		 * Knowledge cutoff: June 2024
		 * <p>
		 * Model ID: gemini-2.0-flash-lite
		 * <p>
		 * See: <a href=
		 * "https://cloud.google.com/vertex-ai/generative-ai/docs/models/gemini/2-0-flash-lite">gemini-2.0-flash-lite</a>
		 */
		GEMINI_2_0_FLASH_LIGHT("gemini-2.0-flash-lite"),

		/**
		 * <b>gemini-2.5-pro</b> is the most advanced reasoning Gemini model, capable of
		 * solving complex problems.
		 * <p>
		 * Inputs: Text, Code, Images, Audio, Video - 1,048,576 tokens | Outputs: Text -
		 * 65,536 tokens
		 * <p>
		 * Knowledge cutoff: January 2025
		 * <p>
		 * Model ID: gemini-2.5-pro-preview-05-06
		 * <p>
		 * See: <a href=
		 * "https://cloud.google.com/vertex-ai/generative-ai/docs/models/gemini/2-5-pro">gemini-2.5-pro</a>
		 */
		GEMINI_2_5_PRO("gemini-2.5-pro-preview-05-06"),

		/**
		 * <b>gemini-2.5-flash</b> is a thinking model that offers great, well-rounded
		 * capabilities. It is designed to offer a balance between price and performance.
		 * <p>
		 * Inputs: Text, Code, Images, Audio, Video - 1,048,576 tokens | Outputs: Text -
		 * 65,536 tokens
		 * <p>
		 * Knowledge cutoff: January 2025
		 * <p>
		 * Model ID: gemini-2.5-flash-preview-04-17
		 * <p>
		 * See: <a href=
		 * "https://cloud.google.com/vertex-ai/generative-ai/docs/models/gemini/2-5-flash">gemini-2.5-flash</a>
		 */
		GEMINI_2_5_FLASH("gemini-2.5-flash-preview-04-17");

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

}
