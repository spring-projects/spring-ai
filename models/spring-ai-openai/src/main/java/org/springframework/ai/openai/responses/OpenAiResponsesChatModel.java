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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.core.RequestOptions;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStatus;
import com.openai.models.responses.ResponseStreamEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.metadata.Usage;
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
import org.springframework.ai.model.observation.ObservationTermination;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.openai.AbstractOpenAiOptions;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.util.Assert;

/**
 * A {@link ChatModel} backed by OpenAI's Responses API ({@code /v1/responses}), the
 * endpoint where new OpenAI capability lands. Notably, it is the only way to combine
 * reasoning with tool calling on GPT-5.4 and later, which Chat Completions no longer
 * supports.
 * <p>
 * This model is <strong>stateless</strong>. Every call sends the whole {@link Prompt};
 * OpenAI is never asked to remember the conversation, so
 * {@link org.springframework.ai.chat.memory.ChatMemory ChatMemory}, advisors, RAG all
 * keep working exactly as they do with {@code OpenAiChatModel}. The stateful modes are
 * not supported.
 * <p>
 * Like {@code OpenAiChatModel} since 2.0.0, this model runs <em>no</em> tool loop:
 * multi-round tool calling belongs to {@code ToolCallingAdvisor}, which is what keeps the
 * behaviour identical across providers.
 * <p>
 * Known limitations: audio input and output, {@code n}, stop sequences, frequency and
 * presence penalties, {@code seed} and {@code logit_bias} have no equivalent here; many
 * OpenAI-compatible backends implement {@code /v1/chat/completions} but not
 * {@code /v1/responses}; and reasoning continuity across turns depends on the chat memory
 * repository in use, since none of them persists
 * {@link org.springframework.ai.chat.messages.part.MessagePart message parts} yet - see
 * the reference documentation for the details.
 *
 * @author Dimitar Proynov
 * @since 2.1.0
 * @see org.springframework.ai.openai.OpenAiChatModel
 */
public final class OpenAiResponsesChatModel implements ChatModel {

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private final OpenAIClient openAiClient;

	private final OpenAIClientAsync openAiClientAsync;

	private final OpenAiResponsesChatOptions options;

	private final ObservationRegistry observationRegistry;

	private final ToolCallingManager toolCallingManager;

	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	private OpenAiResponsesChatModel(OpenAIClient openAiClient, OpenAIClientAsync openAiClientAsync,
			OpenAiResponsesChatOptions options, ObservationRegistry observationRegistry,
			ToolCallingManager toolCallingManager) {
		this.openAiClient = openAiClient;
		this.openAiClientAsync = openAiClientAsync;
		this.options = options;
		this.observationRegistry = observationRegistry;
		this.toolCallingManager = toolCallingManager;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public OpenAiResponsesChatOptions getOptions() {
		return this.options;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		warnUnsupportedOptions(prompt.getOptions());
		Prompt requestPrompt = buildRequestPrompt(prompt);

		ResponseCreateParams request = ResponsesRequestBuilder.build(requestPrompt, this.toolCallingManager);
		RequestOptions requestOptions = buildRequestOptions(requestPrompt);

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(requestPrompt)
			.provider(AiProvider.OPENAI.value())
			.build();

		return ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				Response response = this.openAiClient.responses().create(request, requestOptions);
				if (isFailed(response)) {
					throw ResponsesItemMapper.failure(response);
				}

				Generation generation = ResponsesItemMapper.toGeneration(response);
				Usage usage = ResponsesItemMapper.toUsage(response);
				ChatResponse chatResponse = new ChatResponse(List.of(generation),
						ResponsesItemMapper.toResponseMetadata(response, usage));

				observationContext.setResponse(chatResponse);
				return chatResponse;
			});
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		warnUnsupportedOptions(prompt.getOptions());
		Prompt requestPrompt = buildRequestPrompt(prompt);

		return Flux.deferContextual(contextView -> {
			ResponseCreateParams request = ResponsesRequestBuilder.build(requestPrompt, this.toolCallingManager);
			RequestOptions requestOptions = buildRequestOptions(requestPrompt);

			ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(requestPrompt)
				.provider(AiProvider.OPENAI.value())
				.streaming(true)
				.build();
			Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
					this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);
			Observation parentObservation = contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null);
			observation.parentObservation(parentObservation);
			// Briefly make the parent observation current while starting this one, so
			// Micrometer tracing derives the span's parent from the parent observation
			// rather than from whatever scope happens to be open on the current thread
			// (e.g. the servlet HTTP span).
			try (Observation.Scope ignored = parentObservation != null ? parentObservation.openScope()
					: Observation.Scope.NOOP) {
				observation.start();
			}

			Flux<ResponseStreamEvent> events = Flux.create(sink -> {
				AsyncStreamResponse<ResponseStreamEvent> response = this.openAiClientAsync.responses()
					.createStreaming(request, requestOptions);
				sink.onDispose(response::close);
				response.subscribe(sink::next).onCompleteFuture().whenComplete((unused, throwable) -> {
					if (throwable != null) {
						sink.error(throwable);
					}
					else {
						sink.complete();
					}
				});
			});

			// One assembler per subscription: it accumulates the transcript and the
			// running reasoning summary for this stream only.
			ResponsesStreamAssembler assembler = new ResponsesStreamAssembler();
			Flux<ChatResponse> chatResponses = events.concatMapIterable(assembler::apply);

			Flux<ChatResponse> observedResponses = chatResponses
				.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));

			return new MessageAggregator().aggregate(observedResponses, observationContext::setResponse)
				.transform(ObservationTermination.stopOnTermination(observation));
		});
	}

	/**
	 * Use the provided convention for reporting observation data.
	 * @param observationConvention the provided convention
	 */
	public void setObservationConvention(ChatModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	private static boolean isFailed(Response response) {
		return response.status().filter(status -> ResponseStatus.FAILED.equals(status)).isPresent();
	}

	/**
	 * Warn once per option about portable settings the Responses API has no equivalent
	 * for. Only the incoming options are inspected: once merged into
	 * {@link OpenAiResponsesChatOptions} these read back as {@code null}, and that class
	 * warns from its own builder.
	 */
	private static void warnUnsupportedOptions(@Nullable ChatOptions chatOptions) {
		if (chatOptions == null || chatOptions instanceof OpenAiResponsesChatOptions) {
			return;
		}
		if (chatOptions.getTopK() != null) {
			OpenAiResponsesChatOptions.warnUnsupported("topK");
		}
		if (chatOptions.getStopSequences() != null && !chatOptions.getStopSequences().isEmpty()) {
			OpenAiResponsesChatOptions.warnUnsupported("stopSequences");
		}
		if (chatOptions.getFrequencyPenalty() != null) {
			OpenAiResponsesChatOptions.warnUnsupported("frequencyPenalty");
		}
		if (chatOptions.getPresencePenalty() != null) {
			OpenAiResponsesChatOptions.warnUnsupported("presencePenalty");
		}
	}

	/**
	 * Resolve the options the request is built from.
	 * <p>
	 * A prompt without options gets this model's defaults, and portable
	 * {@link ChatOptions} are merged over them, so a {@code ChatClient} request that only
	 * sets a temperature keeps the configured model, reasoning effort and hosted tools.
	 * <p>
	 * Runtime options that are already {@link OpenAiResponsesChatOptions}
	 * <em>replace</em> the defaults rather than merging with them: they are a complete
	 * specification of the request, so a setting they leave unset falls back to
	 * {@link OpenAiResponsesChatOptions}' own default and not to this model's. This is
	 * what {@code OpenAiChatModel} does with native options too, and it is why
	 * {@link OpenAiResponsesChatOptions#mutate()} exists - start from the model's options
	 * to override one field and keep the rest.
	 */
	private Prompt buildRequestPrompt(Prompt prompt) {
		ChatOptions runtimeOptions = prompt.getOptions();
		if (runtimeOptions == null) {
			return prompt.mutate().chatOptions(this.options).build();
		}
		if (runtimeOptions instanceof OpenAiResponsesChatOptions) {
			return prompt;
		}
		OpenAiResponsesChatOptions.Builder builder = this.options.mutate();
		builder.combineWith(runtimeOptions.mutate());
		return prompt.mutate().chatOptions(builder.build()).build();
	}

	private RequestOptions buildRequestOptions(Prompt prompt) {
		OpenAiResponsesChatOptions chatOptions = (OpenAiResponsesChatOptions) prompt.getOptions();
		Assert.state(chatOptions != null, "Prompt options must be OpenAiResponsesChatOptions type");
		RequestOptions.Builder requestOptions = RequestOptions.builder();
		if (chatOptions.getTimeout() != null) {
			requestOptions.timeout(chatOptions.getTimeout());
		}
		return requestOptions.build();
	}

	/**
	 * Builder for {@link OpenAiResponsesChatModel} instances. Mirrors
	 * {@code OpenAiChatModel.Builder}, so the two models are configured the same way.
	 */
	public static final class Builder {

		private @Nullable OpenAIClient openAiClient;

		private @Nullable OpenAIClientAsync openAiClientAsync;

		private @Nullable OpenAiResponsesChatOptions options;

		private @Nullable ToolCallingManager toolCallingManager;

		private @Nullable ObservationRegistry observationRegistry;

		private @Nullable MeterRegistry meterRegistry;

		private List<OpenAiHttpClientBuilderCustomizer> httpClientCustomizers = new ArrayList<>();

		private Builder() {
		}

		public Builder openAiClient(OpenAIClient openAiClient) {
			this.openAiClient = openAiClient;
			return this;
		}

		public Builder openAiClientAsync(OpenAIClientAsync openAiClientAsync) {
			this.openAiClientAsync = openAiClientAsync;
			return this;
		}

		public Builder options(OpenAiResponsesChatOptions options) {
			this.options = options;
			return this;
		}

		/**
		 * Sets the tool calling manager used to resolve tool definitions. This model
		 * never executes tools: {@code ToolCallingAdvisor} does.
		 */
		public Builder toolCallingManager(ToolCallingManager toolCallingManager) {
			this.toolCallingManager = toolCallingManager;
			return this;
		}

		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		public Builder meterRegistry(@Nullable MeterRegistry meterRegistry) {
			this.meterRegistry = meterRegistry;
			return this;
		}

		/**
		 * Registers an {@link OpenAiHttpClientBuilderCustomizer} that mutates the
		 * underlying OkHttp client builder before the OpenAI clients are constructed.
		 * Customizers are applied in registration order, after Spring AI's own defaults,
		 * so user code wins.
		 */
		public Builder httpClientBuilderCustomizer(OpenAiHttpClientBuilderCustomizer customizer) {
			Assert.notNull(customizer, "customizer cannot be null");
			this.httpClientCustomizers.add(customizer);
			return this;
		}

		/**
		 * Sets the full list of customizers to apply, replacing any registered earlier on
		 * this builder. The order of the list is preserved.
		 */
		public Builder httpClientBuilderCustomizers(List<OpenAiHttpClientBuilderCustomizer> customizers) {
			Assert.notNull(customizers, "customizers cannot be null");
			this.httpClientCustomizers = new ArrayList<>(customizers);
			return this;
		}

		public OpenAiResponsesChatModel build() {
			OpenAiResponsesChatOptions resolvedOptions = Objects.requireNonNullElseGet(this.options,
					() -> OpenAiResponsesChatOptions.builder().build());
			ObservationRegistry resolvedObservationRegistry = Objects.requireNonNullElse(this.observationRegistry,
					ObservationRegistry.NOOP);
			if (OpenAiSetup.detectModelProvider(resolvedOptions.isMicrosoftFoundry(), false,
					resolvedOptions.getBaseUrl(), resolvedOptions.getMicrosoftDeploymentName(),
					resolvedOptions.getMicrosoftFoundryServiceVersion()) == OpenAiSetup.ModelProvider.GITHUB_MODELS) {
				throw new IllegalStateException("GitHub Models does not support the OpenAI Responses API");
			}

			OpenAIClient resolvedClient = Objects.requireNonNullElseGet(this.openAiClient,
					() -> OpenAiSetup.setupSyncClient(resolvedOptions.getBaseUrl(), resolvedOptions.getApiKey(),
							resolvedOptions.getCredential(), resolvedOptions.getMicrosoftDeploymentName(),
							resolvedOptions.getMicrosoftFoundryServiceVersion(), resolvedOptions.getOrganizationId(),
							resolvedOptions.isMicrosoftFoundry(), false, resolvedOptions.getModel(),
							Objects.requireNonNullElse(resolvedOptions.getTimeout(),
									AbstractOpenAiOptions.DEFAULT_TIMEOUT),
							resolvedOptions.getMaxRetries(), resolvedOptions.getProxy(),
							resolvedOptions.getCustomHeaders(), resolvedObservationRegistry, this.meterRegistry,
							this.httpClientCustomizers));

			OpenAIClientAsync resolvedClientAsync = Objects.requireNonNullElseGet(this.openAiClientAsync,
					() -> OpenAiSetup.setupAsyncClient(resolvedOptions.getBaseUrl(), resolvedOptions.getApiKey(),
							resolvedOptions.getCredential(), resolvedOptions.getMicrosoftDeploymentName(),
							resolvedOptions.getMicrosoftFoundryServiceVersion(), resolvedOptions.getOrganizationId(),
							resolvedOptions.isMicrosoftFoundry(), false, resolvedOptions.getModel(),
							Objects.requireNonNullElse(resolvedOptions.getTimeout(),
									AbstractOpenAiOptions.DEFAULT_TIMEOUT),
							resolvedOptions.getMaxRetries(), resolvedOptions.getProxy(),
							resolvedOptions.getCustomHeaders(), resolvedObservationRegistry, this.meterRegistry,
							this.httpClientCustomizers));

			ToolCallingManager resolvedToolCallingManager = Objects.requireNonNullElse(this.toolCallingManager,
					ToolCallingManager.builder().observationRegistry(resolvedObservationRegistry).build());

			return new OpenAiResponsesChatModel(resolvedClient, resolvedClientAsync, resolvedOptions,
					resolvedObservationRegistry, resolvedToolCallingManager);
		}

	}

}
