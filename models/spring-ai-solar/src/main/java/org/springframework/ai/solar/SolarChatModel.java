package org.springframework.ai.solar;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyUsage;
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
import org.springframework.ai.chat.prompt.ChatOptionsBuilder;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.solar.api.SolarApi;
import org.springframework.ai.solar.api.common.SolarConstants;
import org.springframework.ai.solar.metadata.SolarUsage;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SolarChatModel implements ChatModel, StreamingChatModel {

	private static final Logger logger = LoggerFactory.getLogger(SolarChatModel.class);

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	/**
	 * The retry template used to retry the Solar API calls.
	 */
	public final RetryTemplate retryTemplate;

	/**
	 * The default options used for the chat completion requests.
	 */
	private final SolarChatOptions defaultOptions;

	/**
	 * Low-level access to the Solar API.
	 */
	private final SolarApi solarApi;

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	/**
	 * Conventions to use for generating observations.
	 */
	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	/**
	 * Creates an instance of the SolarChatModel.
	 * @param SolarApi The SolarApi instance to be used for interacting with the Solar
	 * Chat API.
	 * @throws IllegalArgumentException if SolarApi is null
	 */
	public SolarChatModel(SolarApi SolarApi) {
		this(SolarApi, SolarChatOptions.builder().withModel(SolarApi.DEFAULT_CHAT_MODEL).withTemperature(0.7).build());
	}

	/**
	 * Initializes an instance of the SolarChatModel.
	 * @param SolarApi The SolarApi instance to be used for interacting with the Solar
	 * Chat API.
	 * @param options The SolarChatOptions to configure the chat client.
	 */
	public SolarChatModel(SolarApi SolarApi, SolarChatOptions options) {
		this(SolarApi, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes a new instance of the SolarChatModel.
	 * @param SolarApi The SolarApi instance to be used for interacting with the Solar
	 * Chat API.
	 * @param options The SolarChatOptions to configure the chat client.
	 * @param retryTemplate The retry template.
	 */
	public SolarChatModel(SolarApi SolarApi, SolarChatOptions options, RetryTemplate retryTemplate) {
		this(SolarApi, options, retryTemplate, ObservationRegistry.NOOP);
	}

	/**
	 * Initializes a new instance of the SolarChatModel.
	 * @param SolarApi The SolarApi instance to be used for interacting with the Solar
	 * Chat API.
	 * @param options The SolarChatOptions to configure the chat client.
	 * @param retryTemplate The retry template.
	 * @param observationRegistry The ObservationRegistry used for instrumentation.
	 */
	public SolarChatModel(SolarApi SolarApi, SolarChatOptions options, RetryTemplate retryTemplate,
			ObservationRegistry observationRegistry) {
		Assert.notNull(SolarApi, "SolarApi must not be null");
		Assert.notNull(options, "Options must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");
		Assert.notNull(observationRegistry, "ObservationRegistry must not be null");
		this.solarApi = SolarApi;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		SolarApi.ChatCompletionRequest request = createRequest(prompt, false);

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(prompt)
			.provider(SolarConstants.PROVIDER_NAME)
			.requestOptions(buildRequestOptions(request))
			.build();

		return ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				ResponseEntity<SolarApi.ChatCompletion> completionEntity = this.retryTemplate
					.execute(ctx -> this.solarApi.chatCompletionEntity(request));

				var chatCompletion = completionEntity.getBody();
				if (chatCompletion == null) {
					logger.warn("No chat completion returned for prompt: {}", prompt);
					return new ChatResponse(List.of());
				}

			// @formatter:off
					Map<String, Object> metadata = Map.of(
							"id", chatCompletion.id(),
							"role", SolarApi.ChatCompletionMessage.Role.ASSISTANT
					);
					// @formatter:on

				var assistantMessage = new AssistantMessage(chatCompletion.choices().get(0).message().content(),
						metadata);
				List<Generation> generations = Collections.singletonList(new Generation(assistantMessage));
				ChatResponse chatResponse = new ChatResponse(generations, from(chatCompletion, request.model()));
				observationContext.setResponse(chatResponse);
				return chatResponse;
			});
	}

	/**
	 * Accessible for testing.
	 */
	public SolarApi.ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {
		var chatCompletionMessages = prompt.getInstructions()
			.stream()
			.map(m -> new SolarApi.ChatCompletionMessage(m.getContent(),
					SolarApi.ChatCompletionMessage.Role.valueOf(m.getMessageType().name())))
			.toList();
		var systemMessageList = chatCompletionMessages.stream()
			.filter(msg -> msg.role() == SolarApi.ChatCompletionMessage.Role.SYSTEM)
			.toList();
		var userMessageList = chatCompletionMessages.stream()
			.filter(msg -> msg.role() != SolarApi.ChatCompletionMessage.Role.SYSTEM)
			.toList();

		if (systemMessageList.size() > 1) {
			throw new IllegalArgumentException("Only one system message is allowed in the prompt");
		}

		var systemMessage = systemMessageList.isEmpty() ? null : systemMessageList.get(0).content();

		var request = new SolarApi.ChatCompletionRequest(userMessageList, systemMessage, stream);

		if (this.defaultOptions != null) {
			request = ModelOptionsUtils.merge(this.defaultOptions, request, SolarApi.ChatCompletionRequest.class);
		}

		if (prompt.getOptions() != null) {
			var updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
					SolarChatOptions.class);
			request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, SolarApi.ChatCompletionRequest.class);
		}
		return request;
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return SolarChatOptions.fromOptions(this.defaultOptions);
	}

	private ChatOptions buildRequestOptions(SolarApi.ChatCompletionRequest request) {
		return ChatOptionsBuilder.builder()
			.withModel(request.model())
			.withFrequencyPenalty(request.frequencyPenalty())
			.withMaxTokens(request.maxTokens())
			.withPresencePenalty(request.presencePenalty())
			.withStopSequences(request.stop())
			.withTemperature(request.temperature())
			.withTopP(request.topP())
			.build();
	}

	private ChatResponseMetadata from(SolarApi.ChatCompletion result, String model) {
		Assert.notNull(result, "Solar ChatCompletionResult must not be null");
		return ChatResponseMetadata.builder()
			.withId(result.id() != null ? result.id() : "")
			.withUsage(result.usage() != null ? SolarUsage.from(result.usage()) : new EmptyUsage())
			.withModel(model)
			.withKeyValue("created", result.created() != null ? result.created() : 0L)
			.build();
	}

	public void setObservationConvention(ChatModelObservationConvention observationConvention) {
		this.observationConvention = observationConvention;
	}

}
