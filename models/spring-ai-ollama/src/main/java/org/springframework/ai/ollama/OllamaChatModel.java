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
package org.springframework.ai.ollama;

import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.ChatOptionsBuilder;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaApi.ChatRequest;
import org.springframework.ai.ollama.api.OllamaApi.Message.Role;
import org.springframework.ai.ollama.api.OllamaApi.Message.ToolCall;
import org.springframework.ai.ollama.api.OllamaApi.Message.ToolCallFunction;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.OllamaModelManager;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.ai.ollama.metadata.OllamaChatUsage;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import reactor.core.publisher.Flux;

/**
 * {@link ChatModel} implementation for {@literal Ollama}. Ollama allows developers to run
 * large language models and generate embeddings locally. It supports open-source models
 * available on [Ollama AI Library](<a href="https://ollama.ai/library">...</a>) and on
 * Hugging Face. Please refer to the <a href="https://ollama.ai/">official Ollama
 * website</a> for the most up-to-date information on available models.
 *
 * @author Christian Tzolov
 * @author luocongqiu
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class OllamaChatModel extends AbstractToolCallSupport implements ChatModel {

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private final OllamaApi chatApi;

	private final OllamaOptions defaultOptions;

	private final ObservationRegistry observationRegistry;

	private final OllamaModelManager modelManager;

	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public OllamaChatModel(OllamaApi ollamaApi, OllamaOptions defaultOptions,
			FunctionCallbackContext functionCallbackContext, List<FunctionCallback> toolFunctionCallbacks,
			ObservationRegistry observationRegistry, ModelManagementOptions modelManagementOptions) {
		super(functionCallbackContext, defaultOptions, toolFunctionCallbacks);
		Assert.notNull(ollamaApi, "ollamaApi must not be null");
		Assert.notNull(defaultOptions, "defaultOptions must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");
		Assert.notNull(observationRegistry, "modelManagementOptions must not be null");
		this.chatApi = ollamaApi;
		this.defaultOptions = defaultOptions;
		this.observationRegistry = observationRegistry;
		this.modelManager = new OllamaModelManager(chatApi, modelManagementOptions);
		initializeModelIfEnabled(defaultOptions.getModel(), modelManagementOptions.pullModelStrategy());
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public ChatResponse call(Prompt prompt) {

		OllamaApi.ChatRequest request = ollamaChatRequest(prompt, false);

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(prompt)
			.provider(OllamaApi.PROVIDER_NAME)
			.requestOptions(buildRequestOptions(request))
			.build();

		ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {

				OllamaApi.ChatResponse ollamaResponse = this.chatApi.chat(request);

				List<AssistantMessage.ToolCall> toolCalls = ollamaResponse.message().toolCalls() == null ? List.of()
						: ollamaResponse.message()
							.toolCalls()
							.stream()
							.map(toolCall -> new AssistantMessage.ToolCall("", "function", toolCall.function().name(),
									ModelOptionsUtils.toJsonString(toolCall.function().arguments())))
							.toList();

				var assistantMessage = new AssistantMessage(ollamaResponse.message().content(), Map.of(), toolCalls);

				ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.NULL;
				if (ollamaResponse.promptEvalCount() != null && ollamaResponse.evalCount() != null) {
					generationMetadata = ChatGenerationMetadata.from(ollamaResponse.doneReason(), null);
				}

				var generator = new Generation(assistantMessage, generationMetadata);
				ChatResponse chatResponse = new ChatResponse(List.of(generator), from(ollamaResponse));

				observationContext.setResponse(chatResponse);

				return chatResponse;

			});

		if (!isProxyToolCalls(prompt, this.defaultOptions) && response != null
				&& isToolCall(response, Set.of("stop"))) {
			var toolCallConversation = handleToolCalls(prompt, response);
			// Recursively call the call method with the tool call message
			// conversation that contains the call responses.
			return this.call(new Prompt(toolCallConversation, prompt.getOptions()));
		}

		return response;
	}

	public static ChatResponseMetadata from(OllamaApi.ChatResponse response) {
		Assert.notNull(response, "OllamaApi.ChatResponse must not be null");
		return ChatResponseMetadata.builder()
			.withUsage(OllamaChatUsage.from(response))
			.withModel(response.model())
			.withKeyValue("created-at", response.createdAt())
			.withKeyValue("eval-duration", response.evalDuration())
			.withKeyValue("eval-count", response.evalCount())
			.withKeyValue("load-duration", response.loadDuration())
			.withKeyValue("eval-duration", response.promptEvalDuration())
			.withKeyValue("eval-count", response.promptEvalCount())
			.withKeyValue("total-duration", response.totalDuration())
			.withKeyValue("done", response.done())
			.build();
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		return Flux.deferContextual(contextView -> {
			OllamaApi.ChatRequest request = ollamaChatRequest(prompt, true);

			final ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(prompt)
				.provider(OllamaApi.PROVIDER_NAME)
				.requestOptions(buildRequestOptions(request))
				.build();

			Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
					this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			Flux<OllamaApi.ChatResponse> ollamaResponse = this.chatApi.streamingChat(request);

			Flux<ChatResponse> chatResponse = ollamaResponse.map(chunk -> {
				String content = (chunk.message() != null) ? chunk.message().content() : "";

				List<AssistantMessage.ToolCall> toolCalls = List.of();

				// Added null checks to prevent NPE when accessing tool calls
				if (chunk.message() != null && chunk.message().toolCalls() != null) {
					toolCalls = chunk.message()
						.toolCalls()
						.stream()
						.map(toolCall -> new AssistantMessage.ToolCall("", "function", toolCall.function().name(),
								ModelOptionsUtils.toJsonString(toolCall.function().arguments())))
						.toList();
				}

				var assistantMessage = new AssistantMessage(content, Map.of(), toolCalls);

				ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.NULL;
				if (chunk.promptEvalCount() != null && chunk.evalCount() != null) {
					generationMetadata = ChatGenerationMetadata.from(chunk.doneReason(), null);
				}

				var generator = new Generation(assistantMessage, generationMetadata);
				return new ChatResponse(List.of(generator), from(chunk));
			});

			// @formatter:off
			Flux<ChatResponse> chatResponseFlux = chatResponse.flatMap(response -> {
				if (isToolCall(response, Set.of("stop"))) {
					var toolCallConversation = handleToolCalls(prompt, response);
					// Recursively call the stream method with the tool call message
					// conversation that contains the call responses.
					return this.stream(new Prompt(toolCallConversation, prompt.getOptions()));
				}
				else {
					return Flux.just(response);
				}
			})
			.doOnError(observation::error)
			.doFinally(s -> {
				observation.stop();
			})
			.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
			// @formatter:on

			return new MessageAggregator().aggregate(chatResponseFlux, observationContext::setResponse);
		});
	}

	/**
	 * Package access for testing.
	 */
	OllamaApi.ChatRequest ollamaChatRequest(Prompt prompt, boolean stream) {

		List<OllamaApi.Message> ollamaMessages = prompt.getInstructions().stream().map(message -> {
			if (message instanceof UserMessage userMessage) {
				var messageBuilder = OllamaApi.Message.builder(Role.USER).withContent(message.getContent());
				if (!CollectionUtils.isEmpty(userMessage.getMedia())) {
					messageBuilder.withImages(
							userMessage.getMedia().stream().map(media -> this.fromMediaData(media.getData())).toList());
				}
				return List.of(messageBuilder.build());
			}
			else if (message instanceof SystemMessage systemMessage) {
				return List.of(OllamaApi.Message.builder(Role.SYSTEM).withContent(systemMessage.getContent()).build());
			}
			else if (message instanceof AssistantMessage assistantMessage) {
				List<ToolCall> toolCalls = null;
				if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
					toolCalls = assistantMessage.getToolCalls().stream().map(toolCall -> {
						var function = new ToolCallFunction(toolCall.name(),
								ModelOptionsUtils.jsonToMap(toolCall.arguments()));
						return new ToolCall(function);
					}).toList();
				}
				return List.of(OllamaApi.Message.builder(Role.ASSISTANT)
					.withContent(assistantMessage.getContent())
					.withToolCalls(toolCalls)
					.build());
			}
			else if (message instanceof ToolResponseMessage toolMessage) {
				return toolMessage.getResponses()
					.stream()
					.map(tr -> OllamaApi.Message.builder(Role.TOOL).withContent(tr.responseData()).build())
					.toList();
			}
			throw new IllegalArgumentException("Unsupported message type: " + message.getMessageType());
		}).flatMap(List::stream).toList();

		Set<String> functionsForThisRequest = new HashSet<>();

		// runtime options
		OllamaOptions runtimeOptions = null;
		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof FunctionCallingOptions functionCallingOptions) {
				runtimeOptions = ModelOptionsUtils.copyToTarget(functionCallingOptions, FunctionCallingOptions.class,
						OllamaOptions.class);
			}
			else {
				runtimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
						OllamaOptions.class);
			}
			functionsForThisRequest.addAll(this.runtimeFunctionCallbackConfigurations(runtimeOptions));
		}

		if (!CollectionUtils.isEmpty(this.defaultOptions.getFunctions())) {
			functionsForThisRequest.addAll(this.defaultOptions.getFunctions());
		}
		OllamaOptions mergedOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions, OllamaOptions.class);

		mergedOptions.setPullModelStrategy(this.defaultOptions.getPullModelStrategy());
		if (runtimeOptions != null && runtimeOptions.getPullModelStrategy() != null) {
			mergedOptions.setPullModelStrategy(runtimeOptions.getPullModelStrategy());
		}

		// Override the model.
		if (!StringUtils.hasText(mergedOptions.getModel())) {
			throw new IllegalArgumentException("Model is not set!");
		}

		String model = mergedOptions.getModel();
		OllamaApi.ChatRequest.Builder requestBuilder = OllamaApi.ChatRequest.builder(model)
			.withStream(stream)
			.withMessages(ollamaMessages)
			.withOptions(mergedOptions);

		if (mergedOptions.getFormat() != null) {
			requestBuilder.withFormat(mergedOptions.getFormat());
		}

		if (mergedOptions.getKeepAlive() != null) {
			requestBuilder.withKeepAlive(mergedOptions.getKeepAlive());
		}

		// Add the enabled functions definitions to the request's tools parameter.
		if (!CollectionUtils.isEmpty(functionsForThisRequest)) {
			requestBuilder.withTools(this.getFunctionTools(functionsForThisRequest));
		}

		initializeModelIfEnabled(mergedOptions.getModel(), mergedOptions.getPullModelStrategy());

		return requestBuilder.build();
	}

	private String fromMediaData(Object mediaData) {
		if (mediaData instanceof byte[] bytes) {
			return Base64.getEncoder().encodeToString(bytes);
		}
		else if (mediaData instanceof String text) {
			return text;
		}
		else {
			throw new IllegalArgumentException("Unsupported media data type: " + mediaData.getClass().getSimpleName());
		}

	}

	private List<ChatRequest.Tool> getFunctionTools(Set<String> functionNames) {
		return this.resolveFunctionCallbacks(functionNames).stream().map(functionCallback -> {
			var function = new ChatRequest.Tool.Function(functionCallback.getName(), functionCallback.getDescription(),
					functionCallback.getInputTypeSchema());
			return new ChatRequest.Tool(function);
		}).toList();
	}

	private ChatOptions buildRequestOptions(OllamaApi.ChatRequest request) {
		var options = ModelOptionsUtils.mapToClass(request.options(), OllamaOptions.class);
		return ChatOptionsBuilder.builder()
			.withModel(request.model())
			.withFrequencyPenalty(options.getFrequencyPenalty())
			.withMaxTokens(options.getMaxTokens())
			.withPresencePenalty(options.getPresencePenalty())
			.withStopSequences(options.getStopSequences())
			.withTemperature(options.getTemperature())
			.withTopK(options.getTopK())
			.withTopP(options.getTopP())
			.build();
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return OllamaOptions.fromOptions(this.defaultOptions);
	}

	/**
	 * Pull the given model into Ollama based on the specified strategy.
	 */
	private void initializeModelIfEnabled(String model, PullModelStrategy pullModelStrategy) {
		if (!PullModelStrategy.NEVER.equals(pullModelStrategy)) {
			this.modelManager.pullModel(model, pullModelStrategy);
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

	public static class Builder {

		private OllamaApi ollamaApi;

		private OllamaOptions defaultOptions = OllamaOptions.create().withModel(OllamaOptions.DEFAULT_MODEL);

		private FunctionCallbackContext functionCallbackContext;

		private List<FunctionCallback> toolFunctionCallbacks = List.of();

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private ModelManagementOptions modelManagementOptions = ModelManagementOptions.defaults();

		private Builder() {
		}

		public Builder withOllamaApi(OllamaApi ollamaApi) {
			this.ollamaApi = ollamaApi;
			return this;
		}

		public Builder withDefaultOptions(OllamaOptions defaultOptions) {
			this.defaultOptions = defaultOptions;
			return this;
		}

		public Builder withFunctionCallbackContext(FunctionCallbackContext functionCallbackContext) {
			this.functionCallbackContext = functionCallbackContext;
			return this;
		}

		public Builder withToolFunctionCallbacks(List<FunctionCallback> toolFunctionCallbacks) {
			this.toolFunctionCallbacks = toolFunctionCallbacks;
			return this;
		}

		public Builder withObservationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		public Builder withModelManagementOptions(ModelManagementOptions modelManagementOptions) {
			this.modelManagementOptions = modelManagementOptions;
			return this;
		}

		public OllamaChatModel build() {
			return new OllamaChatModel(ollamaApi, defaultOptions, functionCallbackContext, toolFunctionCallbacks,
					observationRegistry, modelManagementOptions);
		}

	}

}