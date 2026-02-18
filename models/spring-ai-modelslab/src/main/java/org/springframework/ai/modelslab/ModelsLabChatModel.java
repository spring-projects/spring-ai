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

package org.springframework.ai.modelslab;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.modelslab.api.ModelsLabApi;
import org.springframework.ai.modelslab.api.ModelsLabApiConstants;
import org.springframework.ai.modelslab.options.ModelsLabChatOptions;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link ChatModel} and {@link StreamingChatModel} implementation for {@literal ModelsLab}
 * backed by {@link ModelsLabApi}.
 * 
 * @author Patcher
 * @since 2.0.0
 * @see ChatModel
 * @see StreamingChatModel
 * @see ModelsLabApi
 */
public class ModelsLabChatModel implements ChatModel {

	private static final Logger logger = LoggerFactory.getLogger(ModelsLabChatModel.class);

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	/**
	 * The default options used for the chat completion requests.
	 */
	private final ModelsLabChatOptions defaultOptions;

	/**
	 * The retry template used to retry the ModelsLab API calls.
	 */
	private final RetryTemplate retryTemplate;

	/**
	 * Low-level access to the ModelsLab API.
	 */
	private final ModelsLabApi modelsLabApi;

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	/**
	 * Conventions to use for generating observations.
	 */
	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	/**
	 * Creates a new ModelsLabChatModel instance.
	 *
	 * @param modelsLabApi The ModelsLab API client
	 * @param defaultOptions The default chat options
	 * @param retryTemplate The retry template
	 * @param observationRegistry The observation registry
	 */
	public ModelsLabChatModel(ModelsLabApi modelsLabApi, ModelsLabChatOptions defaultOptions,
							  RetryTemplate retryTemplate, ObservationRegistry observationRegistry) {
		Assert.notNull(modelsLabApi, "modelsLabApi cannot be null");
		Assert.notNull(defaultOptions, "defaultOptions cannot be null");
		Assert.notNull(retryTemplate, "retryTemplate cannot be null");
		Assert.notNull(observationRegistry, "observationRegistry cannot be null");
		
		this.modelsLabApi = modelsLabApi;
		this.defaultOptions = defaultOptions;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		// Build the final request Prompt, merging runtime and default options
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return this.internalCall(requestPrompt);
	}

	/**
	 * Internal call method to handle the actual API request.
	 *
	 * @param prompt The request prompt
	 * @return The chat response
	 */
	public ChatResponse internalCall(Prompt prompt) {
		ModelsLabApi.ChatCompletionRequest request = createRequest(prompt, false);

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(prompt)
			.provider(ModelsLabApiConstants.PROVIDER_NAME)
			.build();

		return ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
				this.observationRegistry)
			.observe(() -> {

				ResponseEntity<ModelsLabApi.ChatCompletion> completionEntity = RetryUtils.execute(this.retryTemplate,
					() -> this.modelsLabApi.chatCompletionEntity(request, getAdditionalHttpHeaders(prompt)));

				var chatCompletion = completionEntity.getBody();

				if (chatCompletion == null) {
					logger.warn("No chat completion returned for prompt: {}", prompt);
					return new ChatResponse(List.of());
				}

				List<ModelsLabApi.Choice> choices = chatCompletion.choices();
				if (choices == null) {
					logger.warn("No choices returned for prompt: {}", prompt);
					return new ChatResponse(List.of());
				}

				List<Generation> generations = choices.stream().map(choice -> {
					Map<String, Object> metadata = Map.of(
						"id", chatCompletion.id() != null ? chatCompletion.id() : "",
						"role", choice.message() != null && choice.message().role() != null ? choice.message().role() : "",
						"index", choice.index() != null ? choice.index() : 0,
						"finishReason", choice.finishReason() != null ? choice.finishReason() : ""
					);
					return buildGeneration(choice, metadata);
				}).toList();

				// Build usage information
				Usage usage = buildUsage(chatCompletion.usage());
				ChatResponse chatResponse = new ChatResponse(generations, from(chatCompletion, usage));

				observationContext.setResponse(chatResponse);

				return chatResponse;
			});
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		// Build the final request Prompt, merging runtime and default options
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return internalStream(requestPrompt);
	}

	/**
	 * Internal streaming method to handle the actual API request.
	 *
	 * @param prompt The request prompt
	 * @return A flux of chat responses
	 */
	public Flux<ChatResponse> internalStream(Prompt prompt) {
		return Flux.deferContextual(contextView -> {
			ModelsLabApi.ChatCompletionRequest request = createRequest(prompt, true);

			Flux<ModelsLabApi.ChatCompletionChunk> completionChunks = this.modelsLabApi.chatCompletionStream(request,
				getAdditionalHttpHeaders(prompt));

			// For chunked responses, only the first chunk contains the choice role.
			// The rest of the chunks with same ID share the same role.
			ConcurrentHashMap<String, String> roleMap = new ConcurrentHashMap<>();

			final ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(prompt)
				.provider(ModelsLabApiConstants.PROVIDER_NAME)
				.build();

			Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
				this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
				this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			// Convert the ChatCompletionChunk into a ChatCompletion to be able to reuse
			// the function call handling logic.
			Flux<ChatResponse> chatResponse = completionChunks.map(this::chunkToChatCompletion)
				.switchMap(chatCompletion -> Mono.just(chatCompletion).map(chatCompletion2 -> {
					try {
						// If an id is not provided, set to "NO_ID" (for compatible APIs).
						String id = chatCompletion2.id() == null ? "NO_ID" : chatCompletion2.id();

						List<Generation> generations = chatCompletion2.choices().stream().map(choice -> {
							if (choice.message() != null && choice.message().role() != null) {
								roleMap.putIfAbsent(id, choice.message().role());
							}
							Map<String, Object> metadata = Map.of(
								"id", id,
								"role", roleMap.getOrDefault(id, ""),
								"index", choice.index() != null ? choice.index() : 0,
								"finishReason", choice.finishReason() != null ? choice.finishReason() : ""
							);
							return buildGeneration(choice, metadata);
						}).toList();

						Usage usage = buildUsage(chatCompletion2.usage());
						return new ChatResponse(generations, from(chatCompletion2, usage));
					} catch (Exception e) {
						logger.error("Error processing chat completion", e);
						return new ChatResponse(List.of());
					}
				}));

			Flux<ChatResponse> flux = chatResponse
				.doOnError(observation::error)
				.doFinally(s -> observation.stop())
				.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));

			return new MessageAggregator().aggregate(flux, observationContext::setResponse);
		});
	}

	/**
	 * Gets additional HTTP headers for the request.
	 *
	 * @param prompt The prompt containing options
	 * @return HTTP headers
	 */
	private HttpHeaders getAdditionalHttpHeaders(Prompt prompt) {
		Map<String, String> headers = new HashMap<>(this.defaultOptions.getHttpHeaders() != null ? this.defaultOptions.getHttpHeaders() : Map.of());
		if (prompt.getOptions() != null && prompt.getOptions() instanceof ModelsLabChatOptions chatOptions) {
			if (chatOptions.getHttpHeaders() != null) {
				headers.putAll(chatOptions.getHttpHeaders());
			}
		}
		HttpHeaders httpHeaders = new HttpHeaders();
		headers.forEach(httpHeaders::add);
		return httpHeaders;
	}

	/**
	 * Builds a Generation from a Choice.
	 *
	 * @param choice The choice from the API response
	 * @param metadata Metadata for the generation
	 * @return A Generation instance
	 */
	private Generation buildGeneration(ModelsLabApi.Choice choice, Map<String, Object> metadata) {
		String content = choice.message() != null ? choice.message().content() : "";
		
		var generationMetadata = ChatGenerationMetadata.builder()
			.finishReason(choice.finishReason() != null ? choice.finishReason() : "")
			.build();

		var assistantMessage = AssistantMessage.builder()
			.content(content)
			.properties(metadata)
			.build();

		return new Generation(assistantMessage, generationMetadata);
	}

	/**
	 * Builds usage information from the API response.
	 *
	 * @param usage The usage from the API response
	 * @return A Usage instance
	 */
	private Usage buildUsage(ModelsLabApi.Usage usage) {
		if (usage == null) {
			return new DefaultUsage(0, 0, 0);
		}

		return new DefaultUsage(
			usage.promptTokens() != null ? usage.promptTokens() : 0,
			usage.completionTokens() != null ? usage.completionTokens() : 0,
			usage.totalTokens() != null ? usage.totalTokens() : 0
		);
	}

	/**
	 * Builds ChatResponseMetadata from the API response.
	 *
	 * @param result The chat completion result
	 * @param usage The usage information
	 * @return ChatResponseMetadata
	 */
	private ChatResponseMetadata from(ModelsLabApi.ChatCompletion result, Usage usage) {
		Assert.notNull(result, "ModelsLab ChatCompletion must not be null");
		return ChatResponseMetadata.builder()
			.id(result.id() != null ? result.id() : "")
			.usage(usage)
			.model(result.model() != null ? result.model() : "")
			.keyValue("created", result.created() != null ? result.created() : 0L)
			.build();
	}

	/**
	 * Converts a ChatCompletionChunk into a ChatCompletion.
	 *
	 * @param chunk The chunk to convert
	 * @return A ChatCompletion
	 */
	private ModelsLabApi.ChatCompletion chunkToChatCompletion(ModelsLabApi.ChatCompletionChunk chunk) {
		List<ModelsLabApi.Choice> choices = chunk.choices()
			.stream()
			.map(chunkChoice -> new ModelsLabApi.Choice(
				chunkChoice.index(),
				chunkChoice.delta(),
				chunkChoice.finishReason()))
			.toList();

		return new ModelsLabApi.ChatCompletion(
			chunk.id(),
			chunk.object(),
			chunk.created(),
			chunk.model(),
			choices,
			null); // Usage is typically null for streaming chunks
	}

	/**
	 * Builds the request prompt by merging runtime and default options.
	 *
	 * @param prompt The original prompt
	 * @return The merged prompt
	 */
	Prompt buildRequestPrompt(Prompt prompt) {
		// Process runtime options
		ModelsLabChatOptions runtimeOptions = null;
		if (prompt.getOptions() != null) {
			runtimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
				ModelsLabChatOptions.class);
		}

		// Define request options by merging runtime options and default options
		ModelsLabChatOptions requestOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions,
			ModelsLabChatOptions.class);

		return new Prompt(prompt.getInstructions(), requestOptions);
	}

	/**
	 * Creates a chat completion request from a prompt.
	 *
	 * @param prompt The prompt
	 * @param stream Whether to stream the response
	 * @return A ChatCompletionRequest
	 */
	ModelsLabApi.ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {
		List<ModelsLabApi.ChatMessage> chatMessages = prompt.getInstructions().stream()
			.map(this::toModelsLabChatMessage)
			.toList();

		ModelsLabChatOptions requestOptions = (ModelsLabChatOptions) prompt.getOptions();

		return new ModelsLabApi.ChatCompletionRequest(
			requestOptions != null && requestOptions.getModel() != null ? requestOptions.getModel() : ModelsLabApiConstants.DEFAULT_CHAT_MODEL,
			chatMessages,
			requestOptions != null ? requestOptions.getTemperature() : null,
			requestOptions != null ? requestOptions.getMaxTokens() : null,
			stream,
			requestOptions != null ? requestOptions.getPresencePenalty() : null,
			requestOptions != null ? requestOptions.getFrequencyPenalty() : null,
			requestOptions != null ? requestOptions.getStopSequences() : null
		);
	}

	/**
	 * Converts a Spring AI Message to a ModelsLab ChatMessage.
	 *
	 * @param message The Spring AI message
	 * @return A ModelsLab ChatMessage
	 */
	private ModelsLabApi.ChatMessage toModelsLabChatMessage(Message message) {
		String role = switch (message.getMessageType()) {
			case USER -> "user";
			case ASSISTANT -> "assistant";
			case SYSTEM -> "system";
			default -> "user";
		};

		return new ModelsLabApi.ChatMessage(role, message.getText());
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return ModelsLabChatOptions.fromOptions(this.defaultOptions);
	}

	@Override
	public String toString() {
		return "ModelsLabChatModel [defaultOptions=" + this.defaultOptions + "]";
	}

	/**
	 * Use the provided convention for reporting observation data.
	 *
	 * @param observationConvention The provided convention
	 */
	public void setObservationConvention(ChatModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	/**
	 * Creates a new builder.
	 *
	 * @return A new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Returns a builder pre-populated with the current configuration for mutation.
	 *
	 * @return A builder with current configuration
	 */
	public Builder mutate() {
		return new Builder(this);
	}

	@Override
	public ModelsLabChatModel clone() {
		return this.mutate().build();
	}

	/**
	 * Builder for ModelsLabChatModel.
	 */
	public static final class Builder {

		private ModelsLabApi modelsLabApi;
		private ModelsLabChatOptions defaultOptions = ModelsLabChatOptions.builder()
			.model(ModelsLabApiConstants.DEFAULT_CHAT_MODEL)
			.temperature(0.7f)
			.build();
		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;
		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private Builder() {
		}

		// Copy constructor for mutate()
		public Builder(ModelsLabChatModel model) {
			this.modelsLabApi = model.modelsLabApi;
			this.defaultOptions = model.defaultOptions;
			this.retryTemplate = model.retryTemplate;
			this.observationRegistry = model.observationRegistry;
		}

		public Builder modelsLabApi(ModelsLabApi modelsLabApi) {
			this.modelsLabApi = modelsLabApi;
			return this;
		}

		public Builder defaultOptions(ModelsLabChatOptions defaultOptions) {
			this.defaultOptions = defaultOptions;
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

		public ModelsLabChatModel build() {
			return new ModelsLabChatModel(this.modelsLabApi, this.defaultOptions,
				this.retryTemplate, this.observationRegistry);
		}
	}

}