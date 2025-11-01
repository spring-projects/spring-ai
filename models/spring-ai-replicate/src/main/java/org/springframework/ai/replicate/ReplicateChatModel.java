/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.replicate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.replicate.api.ReplicateApi;
import org.springframework.ai.replicate.api.ReplicateApi.PredictionRequest;
import org.springframework.util.Assert;

/**
 * Replicate Chat Model implementation.
 *
 * @author Rene Maierhofer
 * @since 1.1.0
 */
public class ReplicateChatModel implements ChatModel {

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final ReplicateApi replicateApi;

	private final ObservationRegistry observationRegistry;

	private final ReplicateChatOptions defaultOptions;

	private final ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public ReplicateChatModel(ReplicateApi replicateApi, ObservationRegistry observationRegistry,
			ReplicateChatOptions defaultOptions) {
		Assert.notNull(replicateApi, "replicateApi must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");
		this.replicateApi = replicateApi;
		this.observationRegistry = observationRegistry;
		this.defaultOptions = defaultOptions;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		return this.internalCall(prompt);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		return this.internalStream(prompt);
	}

	private ChatResponse internalCall(Prompt prompt) {
		// Replicate does not support conversation history.
		assert prompt.getUserMessages().size() == 1;
		ReplicateChatOptions promptOptions = (ReplicateChatOptions) prompt.getOptions();
		ReplicateChatOptions requestOptions = mergeOptions(promptOptions);
		PredictionRequest request = createRequestWithOptions(prompt, requestOptions, false);

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(prompt)
			.provider(ReplicateApi.PROVIDER_NAME)
			.build();

		return ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				ReplicateApi.PredictionResponse predictionResponse = this.replicateApi
					.createPredictionAndWait(requestOptions.getModel(), request);

				if (predictionResponse == null) {
					logger.warn("No prediction response returned for prompt: {}", prompt);
					return new ChatResponse(List.of());
				}

				Map<String, Object> metadata = buildMetadataMap(predictionResponse);

				String content = extractContentFromOutput(predictionResponse.output());
				AssistantMessage assistantMessage = AssistantMessage.builder()
					.content(content)
					.properties(metadata)
					.build();
				Generation generation = new Generation(assistantMessage);
				DefaultUsage usage = getDefaultUsage(predictionResponse.metrics());
				ChatResponse chatResponse = new ChatResponse(List.of(generation), from(predictionResponse, usage));
				observationContext.setResponse(chatResponse);

				return chatResponse;
			});
	}

	private static ChatResponseMetadata from(ReplicateApi.PredictionResponse result, Usage usage) {
		return ChatResponseMetadata.builder()
			.id(result.id())
			.model(result.model())
			.usage(usage)
			.keyValue("created", result.createdAt())
			.keyValue("version", result.version())
			.build();
	}

	private static DefaultUsage getDefaultUsage(ReplicateApi.Metrics metrics) {
		if (metrics == null) {
			return new DefaultUsage(0, 0);
		}
		Integer inputTokens = metrics.inputTokenCount() != null ? metrics.inputTokenCount() : 0;
		Integer outputTokens = metrics.outputTokenCount() != null ? metrics.outputTokenCount() : 0;
		return new DefaultUsage(inputTokens, outputTokens);
	}

	private Flux<ChatResponse> internalStream(Prompt prompt) {
		return Flux.deferContextual(contextView -> {
			ReplicateChatOptions promptOptions = (ReplicateChatOptions) prompt.getOptions();
			ReplicateChatOptions requestOptions = mergeOptions(promptOptions);
			PredictionRequest request = createRequestWithOptions(prompt, requestOptions, true);

			ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(prompt)
				.provider(ReplicateApi.PROVIDER_NAME)
				.build();

			Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
					this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			Flux<ReplicateApi.PredictionResponse> responseStream = this.replicateApi
				.createPredictionStream(requestOptions.getModel(), request);

			Flux<ChatResponse> chatResponseFlux = responseStream.map(chunk -> {
				String content = extractContentFromOutput(chunk.output());

				AssistantMessage assistantMessage = AssistantMessage.builder()
					.content(content)
					.properties(buildMetadataMap(chunk))
					.build();

				Generation generation = new Generation(assistantMessage);
				DefaultUsage usage = getDefaultUsage(chunk.metrics());
				return new ChatResponse(List.of(generation), from(chunk, usage));
			});

			// @formatter:off
			return new MessageAggregator()
				.aggregate(chatResponseFlux, observationContext::setResponse)
				.doOnError(observation::error)
				.doFinally(s -> observation.stop())
				.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
			// @formatter:on
		});
	}

	/**
	 * Merges default options from properties with prompt options. Prompt options take
	 * precedence
	 * @param promptOptions Options from the current Prompt
	 * @return merged Options
	 */
	private ReplicateChatOptions mergeOptions(ReplicateChatOptions promptOptions) {
		if (this.defaultOptions == null) {
			return promptOptions != null ? promptOptions : ReplicateChatOptions.builder().build();
		}
		if (promptOptions == null) {
			return this.defaultOptions;
		}
		ReplicateChatOptions merged = ReplicateChatOptions.fromOptions(this.defaultOptions);
		if (promptOptions.getModel() != null) {
			merged.setModel(promptOptions.getModel());
		}
		if (promptOptions.getVersion() != null) {
			merged.setVersion(promptOptions.getVersion());
		}
		if (promptOptions.getWebhook() != null) {
			merged.setWebhook(promptOptions.getWebhook());
		}
		if (promptOptions.getWebhookEventsFilter() != null) {
			merged.setWebhookEventsFilter(promptOptions.getWebhookEventsFilter());
		}
		Map<String, Object> mergedInput = new HashMap<>();
		if (this.defaultOptions.getInput() != null) {
			mergedInput.putAll(this.defaultOptions.getInput());
		}
		if (promptOptions.getInput() != null) {
			mergedInput.putAll(promptOptions.getInput());
		}
		merged.setInput(mergedInput);

		return merged;
	}

	private PredictionRequest createRequestWithOptions(Prompt prompt, ReplicateChatOptions requestOptions,
			boolean stream) {
		Map<String, Object> input = new HashMap<>();
		if (requestOptions.getInput() != null) {
			input.putAll(requestOptions.getInput());
		}
		input.put("prompt", prompt.getUserMessage().getText());
		return new PredictionRequest(requestOptions.getVersion(), input, requestOptions.getWebhook(),
				requestOptions.getWebhookEventsFilter(), stream);
	}

	private Map<String, Object> buildMetadataMap(ReplicateApi.PredictionResponse response) {
		Map<String, Object> metadata = new HashMap<>();
		if (response.id() != null) {
			metadata.put("id", response.id());
		}
		if (response.urls() != null) {
			metadata.put("urls", response.urls());
		}
		if (response.error() != null) {
			metadata.put("error", response.error());
		}
		if (response.logs() != null) {
			metadata.put("logs", response.logs());
		}
		return metadata;
	}

	/**
	 * Extracts content from the output object. The output can be either a String or a
	 * List of Strings, depending on the model being used.
	 * @param output The output object from the prediction response
	 * @return The extracted content as a String, or empty string if null
	 */
	private static String extractContentFromOutput(Object output) {
		if (output == null) {
			return "";
		}
		if (output instanceof String stringOutput) {
			return stringOutput;
		}
		if (output instanceof List<?> outputList) {
			if (outputList.isEmpty()) {
				return "";
			}
			return outputList.stream().map(Object::toString).reduce("", (a, b) -> a + b);
		}
		// Fallback to toString for other types
		return output.toString();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private ReplicateApi replicateApi;

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private ReplicateChatOptions defaultOptions;

		private Builder() {
		}

		public Builder replicateApi(ReplicateApi replicateApi) {
			this.replicateApi = replicateApi;
			return this;
		}

		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		public Builder defaultOptions(ReplicateChatOptions defaultOptions) {
			this.defaultOptions = defaultOptions;
			return this;
		}

		public ReplicateChatModel build() {
			return new ReplicateChatModel(this.replicateApi, this.observationRegistry, this.defaultOptions);
		}

	}

}
