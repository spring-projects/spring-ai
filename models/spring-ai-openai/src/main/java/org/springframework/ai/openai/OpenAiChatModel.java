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

package org.springframework.ai.openai;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionNamedToolChoice;
import com.openai.models.chat.completions.ChatCompletionToolChoiceOption;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.openai.OpenAiChatOptions.Api;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.util.Assert;

/**
 * Chat Model implementation using the OpenAI Java SDK.
 * <p>
 * One model, two endpoints. OpenAI serves chat over {@code /v1/chat/completions} and over
 * {@code /v1/responses}, and {@link OpenAiChatOptions.Api} selects between them - by
 * default {@link Api#AUTO}, which picks the Responses API for GPT-5.4 and later, and for
 * any request using a setting only that endpoint provides. Everything above this class -
 * {@code ChatClient}, advisors, chat memory, tool calling, observability - behaves
 * identically either way.
 * <p>
 * The Responses API is the endpoint where new OpenAI capability lands, and the only one
 * that serves reasoning together with tool calling on GPT-5.4 and later. Spring AI drives
 * it <strong>statelessly</strong>: every request carries the whole prompt, and OpenAI is
 * never asked to remember the conversation, so the conversation stays in the
 * application's hands. What that costs is reasoning continuity across a restart, because
 * no chat memory repository persists
 * {@link org.springframework.ai.chat.messages.part.MessagePart message parts} yet - see
 * the reference documentation.
 * <p>
 * Like every {@code ChatModel} since 2.0.0, this one runs <em>no</em> tool loop of its
 * own: multi-round tool calling belongs to {@code ToolCallingAdvisor}.
 *
 * @author Julien Dubois
 * @author Christian Tzolov
 * @author Soby Chacko
 * @author Ilayaperumal Gopinathan
 * @author Thomas Vitale
 * @author Eric Bottard
 * @author Taewoong Kim
 * @author Jewoo Shin
 * @author guan xu
 * @author Dimitar Proynov
 */
public final class OpenAiChatModel implements ChatModel {

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private final OpenAIClient openAiClient;

	private final OpenAIClientAsync openAiClientAsync;

	private final OpenAiChatOptions options;

	private final ObservationRegistry observationRegistry;

	private final ToolCallingManager toolCallingManager;

	/**
	 * Both API implementations are wired up front - they are thin adapters over the same
	 * pair of OpenAI clients - and each request picks one. Resolving per request rather
	 * than per model is what lets a single bean answer a {@code ChatClient} call that
	 * overrides the model, or the endpoint, at runtime.
	 */
	private final OpenAiCompletionsChatModel chatCompletionsApi;

	private final OpenAiResponsesChatModel responsesApi;

	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	private OpenAiChatModel(OpenAIClient openAiClient, OpenAIClientAsync openAiClientAsync, OpenAiChatOptions options,
			ObservationRegistry observationRegistry, ToolCallingManager toolCallingManager) {
		this.openAiClient = openAiClient;
		this.openAiClientAsync = openAiClientAsync;
		this.options = options;
		this.observationRegistry = observationRegistry;
		this.toolCallingManager = toolCallingManager;
		this.chatCompletionsApi = new OpenAiCompletionsChatModel(this.openAiClient, this.openAiClientAsync,
				this.toolCallingManager, this.options);
		this.responsesApi = new OpenAiResponsesChatModel(this.openAiClient, this.openAiClientAsync,
				this.toolCallingManager);
	}

	/**
	 * Creates a new builder for {@link OpenAiChatModel}.
	 * @return a new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Gets the chat options for this model.
	 * @return the chat options
	 * @since 2.0.0
	 */
	@Override
	public OpenAiChatOptions getOptions() {
		return this.options;
	}

	/**
	 * @deprecated use {@link #getOptions()} instead.
	 */
	@Override
	@Deprecated(forRemoval = true)
	@SuppressWarnings("removal")
	public ChatOptions getDefaultOptions() {
		return this.options;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		Prompt requestPrompt = buildRequestPrompt(prompt);
		ChatModel api = apiFor(requestPrompt, prompt.getOptions());

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(requestPrompt)
			.provider(AiProvider.OPENAI.value())
			.build();

		return ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				ChatResponse chatResponse = api.call(requestPrompt);
				observationContext.setResponse(chatResponse);
				return chatResponse;
			});
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		Prompt requestPrompt = buildRequestPrompt(prompt);
		ChatModel api = apiFor(requestPrompt, prompt.getOptions());

		return Flux.deferContextual(contextView -> {
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
			// (e.g. the servlet HTTP span). This keeps span parenting correct without
			// relying on automatic context propagation.
			try (Observation.Scope ignored = parentObservation != null ? parentObservation.openScope()
					: Observation.Scope.NOOP) {
				observation.start();
			}

			Flux<ChatResponse> observedResponses = api.stream(requestPrompt)
				.doOnError(observation::error)
				.doFinally(signal -> observation.stop())
				.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));

			return new MessageAggregator().aggregate(observedResponses, observationContext::setResponse);
		});
	}

	/**
	 * Use the provided convention for reporting observation data
	 * @param observationConvention The provided convention
	 */
	public void setObservationConvention(ChatModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	/**
	 * Resolve the options the request is built from.
	 * <p>
	 * A prompt without options gets this model's defaults, and portable
	 * {@link ChatOptions} are merged over them, so a {@code ChatClient} request that only
	 * sets a temperature keeps the configured model, endpoint and server-side tools.
	 * <p>
	 * Runtime options that are already {@link OpenAiChatOptions} <em>replace</em> the
	 * defaults rather than merging with them: they are a complete specification of the
	 * request, so a setting they leave unset falls back to {@link OpenAiChatOptions}' own
	 * default and not to this model's. That is why {@link OpenAiChatOptions#mutate()}
	 * exists - start from the model's options to override one field and keep the rest.
	 */
	private Prompt buildRequestPrompt(Prompt prompt) {
		ChatOptions runtimeOptions = prompt.getOptions();
		if (runtimeOptions == null) {
			return prompt.mutate().chatOptions(this.options).build();
		}
		if (runtimeOptions instanceof OpenAiChatOptions) {
			return prompt;
		}
		OpenAiChatOptions.Builder builder = this.options.mutate();
		builder.combineWith(runtimeOptions.mutate());
		return prompt.mutate().chatOptions(builder.build()).build();
	}

	/**
	 * The endpoint this request goes to, with everything the endpoint cannot honour
	 * reported first.
	 * @param requestPrompt the prompt, carrying merged {@link OpenAiChatOptions}
	 * @param runtimeOptions the options as they arrived on the original prompt
	 */
	private ChatModel apiFor(Prompt requestPrompt, @Nullable ChatOptions runtimeOptions) {
		OpenAiChatOptions requestOptions = (OpenAiChatOptions) requestPrompt.getOptions();
		Assert.state(requestOptions != null, "Prompt options must be OpenAiChatOptions type");
		Api api = OpenAiChatApiSelection.resolve(requestOptions);
		OpenAiChatApiSelection.warnIgnoredOptions(requestOptions, api, runtimeOptions);
		return api == Api.RESPONSES ? this.responsesApi : this.chatCompletionsApi;
	}

	/**
	 * The Chat Completions request built from the given prompt. Package-private: the
	 * mapping it exercises is an implementation detail of
	 * {@link OpenAiCompletionsChatModel}, exposed here for the tests that assert on it.
	 */
	ChatCompletionCreateParams createRequest(Prompt prompt, boolean stream) {
		return this.chatCompletionsApi.createRequest(prompt, stream);
	}

	public static ChatCompletionToolChoiceOption parseToolChoice(JsonNode node) {
		String type = node.get("type").asString();
		switch (type) {
			case "function":
				String functionName = node.get("function").get("name").asString();
				ChatCompletionNamedToolChoice.Function func = ChatCompletionNamedToolChoice.Function.builder()
					.name(functionName)
					.build();
				ChatCompletionNamedToolChoice named = ChatCompletionNamedToolChoice.builder().function(func).build();
				return ChatCompletionToolChoiceOption.ofNamedToolChoice(named);
			case "auto":
				// There is a built-in “auto” option — but how to get it depends on SDK
				// version
				return ChatCompletionToolChoiceOption.ofAuto(ChatCompletionToolChoiceOption.Auto.AUTO);
			case "required":
				return ChatCompletionToolChoiceOption.ofAuto(ChatCompletionToolChoiceOption.Auto.REQUIRED);
			case "none":
				return ChatCompletionToolChoiceOption.ofAuto(ChatCompletionToolChoiceOption.Auto.NONE);
			default:
				throw new IllegalArgumentException("Unknown tool_choice type: " + type);
		}
	}

	/**
	 * Response format (text, json_object, json_schema) for OpenAiChatModel responses.
	 *
	 * @author Julien Dubois
	 * @author Mariusz Bernacki
	 * @author Grogdunn
	 * @author Thomas Vitale
	 * @author John Blum
	 * @author Mark Pollack
	 * @author Josh Long
	 * @author Jemin Huh
	 * @author Ueibin Kim
	 * @author Alexandros Pappas
	 * @author luocongqiu
	 * @author Hyunjoon Choi
	 * @author Jonghoon Park
	 * @author Sebastien Deleuze
	 * @author Bishen Yu
	 */
	public static class ResponseFormat {

		private Type type = Type.TEXT;

		private @Nullable String jsonSchema;

		private @Nullable Boolean strict;

		public Type getType() {
			return this.type;
		}

		public void setType(Type type) {
			this.type = type;
		}

		public @Nullable String getJsonSchema() {
			return this.jsonSchema;
		}

		public void setJsonSchema(@Nullable String jsonSchema) {
			this.jsonSchema = jsonSchema;
		}

		/**
		 * Whether to enable strict schema adherence for JSON schema response format.
		 * Defaults to {@code true} when unset.
		 * <p>
		 * This applies only to the JSON schema response format and is distinct from
		 * {@link OpenAiChatOptions.Builder#strict(Boolean)}, which controls strict mode
		 * for tool/function calling.
		 * @return the strict flag, or {@code null} if not configured
		 */
		public @Nullable Boolean getStrict() {
			return this.strict;
		}

		public void setStrict(@Nullable Boolean strict) {
			this.strict = strict;
		}

		@Override
		public boolean equals(@Nullable Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			ResponseFormat that = (ResponseFormat) o;
			return this.type == that.type && Objects.equals(this.jsonSchema, that.jsonSchema)
					&& Objects.equals(this.strict, that.strict);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.type, this.jsonSchema, this.strict);
		}

		public static Builder builder() {
			return new Builder();
		}

		public static final class Builder {

			private final ResponseFormat responseFormat = new ResponseFormat();

			private Builder() {
			}

			public Builder type(Type type) {
				this.responseFormat.setType(type);
				return this;
			}

			public Builder jsonSchema(String jsonSchema) {
				this.responseFormat.setType(Type.JSON_SCHEMA);
				this.responseFormat.setJsonSchema(jsonSchema);
				return this;
			}

			/**
			 * Whether to enable strict schema adherence for JSON schema response format.
			 * <p>
			 * Not to be confused with {@link OpenAiChatOptions.Builder#strict(Boolean)},
			 * which applies to tool/function calling rather than response format.
			 * @param strict the strict flag
			 * @return this builder
			 */
			public Builder strict(@Nullable Boolean strict) {
				this.responseFormat.setStrict(strict);
				return this;
			}

			public ResponseFormat build() {
				return this.responseFormat;
			}

		}

		public enum Type {

			/**
			 * Generates a text response. (default)
			 */
			TEXT,

			/**
			 * Enables JSON mode, which guarantees the message the model generates is
			 * valid JSON.
			 */
			JSON_OBJECT,

			/**
			 * Enables Structured Outputs which guarantees the model will match your
			 * supplied JSON schema.
			 */
			JSON_SCHEMA

		}

	}

	/**
	 * Builder for creating {@link OpenAiChatModel} instances.
	 */
	public static final class Builder {

		private @Nullable OpenAIClient openAiClient;

		private @Nullable OpenAIClientAsync openAiClientAsync;

		private @Nullable OpenAiChatOptions options;

		private @Nullable ToolCallingManager toolCallingManager;

		private @Nullable ObservationRegistry observationRegistry;

		private @Nullable MeterRegistry meterRegistry;

		private List<OpenAiHttpClientBuilderCustomizer> httpClientCustomizers = new ArrayList<>();

		private Builder() {
		}

		/**
		 * Sets the synchronous OpenAI client.
		 * @param openAiClient the synchronous client
		 * @return this builder
		 */
		public Builder openAiClient(OpenAIClient openAiClient) {
			this.openAiClient = openAiClient;
			return this;
		}

		/**
		 * Sets the asynchronous OpenAI client.
		 * @param openAiClientAsync the asynchronous client
		 * @return this builder
		 */
		public Builder openAiClientAsync(OpenAIClientAsync openAiClientAsync) {
			this.openAiClientAsync = openAiClientAsync;
			return this;
		}

		/**
		 * Sets the chat options.
		 * @param options the chat options
		 * @return this builder
		 */
		public Builder options(OpenAiChatOptions options) {
			this.options = options;
			return this;
		}

		/**
		 * Sets the tool calling manager used for internal tool execution.
		 * @param toolCallingManager the tool calling manager
		 * @return this builder
		 * @deprecated since 2.0.0 for removal in 3.0.0 — internal tool execution in
		 * {@link OpenAiChatModel} is superseded by {@code ToolCallingAdvisor} used via
		 * {@code ChatClient}.
		 */
		@Deprecated(since = "2.0.0", forRemoval = true)
		public Builder toolCallingManager(ToolCallingManager toolCallingManager) {
			this.toolCallingManager = toolCallingManager;
			return this;
		}

		/**
		 * Sets the observation registry for metrics and tracing.
		 * @param observationRegistry the observation registry
		 * @return this builder
		 */
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
		 * underlying OkHttp client builder before the OpenAI clients are constructed. Use
		 * this to attach OkHttp interceptors (e.g. OAuth2 bearer-token injection), swap
		 * the dispatcher executor, or tweak any other OkHttp setting. Customizers are
		 * applied in the order they are registered, after Spring AI's own defaults, so
		 * user code wins.
		 */
		public Builder httpClientBuilderCustomizer(OpenAiHttpClientBuilderCustomizer customizer) {
			Assert.notNull(customizer, "customizer cannot be null");
			this.httpClientCustomizers.add(customizer);
			return this;
		}

		/**
		 * Sets the full list of {@link OpenAiHttpClientBuilderCustomizer customizers} to
		 * apply, replacing any customizers registered earlier on this builder. The order
		 * of the list is preserved when invoking the customizers.
		 */
		public Builder httpClientBuilderCustomizers(List<OpenAiHttpClientBuilderCustomizer> customizers) {
			Assert.notNull(customizers, "customizers cannot be null");
			this.httpClientCustomizers = new ArrayList<>(customizers);
			return this;
		}

		/**
		 * Builds a new {@link OpenAiChatModel} instance.
		 * @return the configured chat model
		 */
		public OpenAiChatModel build() {
			OpenAiChatOptions resolvedOptions = Objects.requireNonNullElseGet(this.options,
					() -> OpenAiChatOptions.builder().build());
			ObservationRegistry resolvedObservationRegistry = Objects.requireNonNullElse(this.observationRegistry,
					ObservationRegistry.NOOP);

			OpenAIClient resolvedClient = Objects.requireNonNullElseGet(this.openAiClient,
					() -> OpenAiSetup.setupSyncClient(resolvedOptions.getBaseUrl(), resolvedOptions.getApiKey(),
							resolvedOptions.getCredential(), resolvedOptions.getMicrosoftDeploymentName(),
							resolvedOptions.getMicrosoftFoundryServiceVersion(), resolvedOptions.getOrganizationId(),
							resolvedOptions.isMicrosoftFoundry(), resolvedOptions.isGitHubModels(),
							resolvedOptions.getModel(),
							Objects.requireNonNullElse(resolvedOptions.getTimeout(),
									AbstractOpenAiOptions.DEFAULT_TIMEOUT),
							resolvedOptions.getMaxRetries(), resolvedOptions.getProxy(),
							resolvedOptions.getCustomHeaders(), resolvedObservationRegistry, this.meterRegistry,
							this.httpClientCustomizers));

			OpenAIClientAsync resolvedClientAsync = Objects.requireNonNullElseGet(this.openAiClientAsync,
					() -> OpenAiSetup.setupAsyncClient(resolvedOptions.getBaseUrl(), resolvedOptions.getApiKey(),
							resolvedOptions.getCredential(), resolvedOptions.getMicrosoftDeploymentName(),
							resolvedOptions.getMicrosoftFoundryServiceVersion(), resolvedOptions.getOrganizationId(),
							resolvedOptions.isMicrosoftFoundry(), resolvedOptions.isGitHubModels(),
							resolvedOptions.getModel(),
							Objects.requireNonNullElse(resolvedOptions.getTimeout(),
									AbstractOpenAiOptions.DEFAULT_TIMEOUT),
							resolvedOptions.getMaxRetries(), resolvedOptions.getProxy(),
							resolvedOptions.getCustomHeaders(), resolvedObservationRegistry, this.meterRegistry,
							this.httpClientCustomizers));

			ToolCallingManager resolvedToolCallingManager = Objects.requireNonNullElse(this.toolCallingManager,
					ToolCallingManager.builder().observationRegistry(resolvedObservationRegistry).build());

			// Fail at startup rather than on the first request when the configured
			// endpoint cannot serve the configured provider
			OpenAiChatApiSelection.resolve(resolvedOptions);

			return new OpenAiChatModel(resolvedClient, resolvedClientAsync, resolvedOptions,
					resolvedObservationRegistry, resolvedToolCallingManager);
		}

	}

}
