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

package org.springframework.ai.chat.client;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.advisor.ChatModelCallAdvisor;
import org.springframework.ai.chat.client.advisor.ChatModelStreamAdvisor;
import org.springframework.ai.chat.client.advisor.DefaultAroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.MemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.ToolAdvisor;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationConvention;
import org.springframework.ai.chat.client.observation.ChatClientObservationContext;
import org.springframework.ai.chat.client.observation.ChatClientObservationConvention;
import org.springframework.ai.chat.client.observation.ChatClientObservationDocumentation;
import org.springframework.ai.chat.client.observation.DefaultChatClientObservationConvention;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.template.TemplateRenderer;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;

/**
 * The default implementation of {@link ChatClient} as created by the
 * {@link Builder#build()} } method.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Josh Long
 * @author Arjen Poutsma
 * @author Soby Chacko
 * @author Dariusz Jedrzejczyk
 * @author Thomas Vitale
 * @author Jonatan Ivanov
 * @author Wenli Tian
 * @author Sebastien Deleuze
 * @since 1.0.0
 */
public class DefaultChatClient implements ChatClient {

	private static final Logger logger = LoggerFactory.getLogger(DefaultChatClient.class);

	private static final ChatClientObservationConvention DEFAULT_CHAT_CLIENT_OBSERVATION_CONVENTION = new DefaultChatClientObservationConvention();

	private static final TemplateRenderer DEFAULT_TEMPLATE_RENDERER = StTemplateRenderer.builder().build();

	private static final ChatClientMessageAggregator CHAT_CLIENT_MESSAGE_AGGREGATOR = new ChatClientMessageAggregator();

	private final DefaultChatClientRequestSpec defaultChatClientRequest;

	public DefaultChatClient(DefaultChatClientRequestSpec defaultChatClientRequest) {
		Assert.notNull(defaultChatClientRequest, "defaultChatClientRequest cannot be null");
		this.defaultChatClientRequest = defaultChatClientRequest;
	}

	@Override
	public ChatClientRequestSpec prompt() {
		return new DefaultChatClientRequestSpec(this.defaultChatClientRequest);
	}

	@Override
	public ChatClientRequestSpec prompt(String content) {
		Assert.hasText(content, "content cannot be null or empty");
		return prompt(new Prompt(content));
	}

	@Override
	public ChatClientRequestSpec prompt(Prompt prompt) {
		Assert.notNull(prompt, "prompt cannot be null");

		DefaultChatClientRequestSpec spec = new DefaultChatClientRequestSpec(this.defaultChatClientRequest);

		// Messages
		if (prompt.getInstructions() != null) {
			spec.messages(prompt.getInstructions());
		}

		// ChatOptions
		if (prompt.getOptions() != null) {
			ChatOptions.Builder<?> requestOptionsBuilder = prompt.getOptions().mutate();
			if (spec.getOptionsCustomizer() != null) {
				spec.getOptionsCustomizer().combineWith(requestOptionsBuilder);
			}
			else {
				spec.options(requestOptionsBuilder);
			}
		}

		return spec;
	}

	/**
	 * Return a {@link ChatClient.Builder} to create a new {@link ChatClient} whose
	 * settings are replicated from this {@link ChatClientRequest}.
	 */
	@Override
	public Builder mutate() {
		return this.defaultChatClientRequest.mutate();
	}

	public static class DefaultPromptUserSpec implements PromptUserSpec {

		private final Map<String, Object> params = new HashMap<>();

		private final Map<String, Object> metadata = new HashMap<>();

		private final List<Media> media = new ArrayList<>();

		private @Nullable String text;

		@Override
		public PromptUserSpec media(Media... media) {
			Assert.notNull(media, "media cannot be null");
			Assert.noNullElements(media, "media cannot contain null elements");
			this.media.addAll(Arrays.asList(media));
			return this;
		}

		@Override
		public PromptUserSpec media(MimeType mimeType, URL url) {
			Assert.notNull(mimeType, "mimeType cannot be null");
			Assert.notNull(url, "url cannot be null");
			try {
				this.media.add(Media.builder().mimeType(mimeType).data(url.toURI()).build());
			}
			catch (URISyntaxException e) {
				throw new RuntimeException(e);
			}
			return this;
		}

		@Override
		public PromptUserSpec media(MimeType mimeType, Resource resource) {
			Assert.notNull(mimeType, "mimeType cannot be null");
			Assert.notNull(resource, "resource cannot be null");
			this.media.add(Media.builder().mimeType(mimeType).data(resource).build());
			return this;
		}

		@Override
		public PromptUserSpec text(String text) {
			Assert.hasText(text, "text cannot be null or empty");
			this.text = text;
			return this;
		}

		@Override
		public PromptUserSpec text(Resource text, Charset charset) {
			Assert.notNull(text, "text cannot be null");
			Assert.notNull(charset, "charset cannot be null");
			try {
				this.text(text.getContentAsString(charset));
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
			return this;
		}

		@Override
		public PromptUserSpec text(Resource text) {
			Assert.notNull(text, "text cannot be null");
			this.text(text, Charset.defaultCharset());
			return this;
		}

		@Override
		public PromptUserSpec param(String key, Object value) {
			Assert.hasText(key, "key cannot be null or empty");
			Assert.notNull(value, "value cannot be null");
			this.params.put(key, value);
			return this;
		}

		@Override
		public PromptUserSpec params(Map<String, Object> params) {
			Assert.notNull(params, "params cannot be null");
			Assert.noNullElements(params.keySet(), "param keys cannot contain null elements");
			Assert.noNullElements(params.values(), "param values cannot contain null elements");
			this.params.putAll(params);
			return this;
		}

		@Override
		public PromptUserSpec metadata(Map<String, Object> metadata) {
			Assert.notNull(metadata, "metadata cannot be null");
			Assert.noNullElements(metadata.keySet(), "metadata keys cannot contain null elements");
			Assert.noNullElements(metadata.values(), "metadata values cannot contain null elements");
			this.metadata.putAll(metadata);
			return this;
		}

		@Override
		public PromptUserSpec metadata(String key, Object value) {
			Assert.hasText(key, "metadata key cannot be null or empty");
			Assert.notNull(value, "metadata value cannot be null");
			this.metadata.put(key, value);
			return this;
		}

		protected @Nullable String text() {
			return this.text;
		}

		protected Map<String, Object> params() {
			return this.params;
		}

		protected List<Media> media() {
			return this.media;
		}

		protected Map<String, Object> metadata() {
			return this.metadata;
		}

	}

	public static class DefaultPromptSystemSpec implements PromptSystemSpec {

		private final Map<String, Object> params = new HashMap<>();

		private final Map<String, Object> metadata = new HashMap<>();

		private @Nullable String text;

		@Override
		public PromptSystemSpec text(String text) {
			Assert.hasText(text, "text cannot be null or empty");
			this.text = text;
			return this;
		}

		@Override
		public PromptSystemSpec text(Resource text, Charset charset) {
			Assert.notNull(text, "text cannot be null");
			Assert.notNull(charset, "charset cannot be null");
			try {
				this.text(text.getContentAsString(charset));
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
			return this;
		}

		@Override
		public PromptSystemSpec text(Resource text) {
			Assert.notNull(text, "text cannot be null");
			this.text(text, Charset.defaultCharset());
			return this;
		}

		@Override
		public PromptSystemSpec param(String key, Object value) {
			Assert.hasText(key, "key cannot be null or empty");
			Assert.notNull(value, "value cannot be null");
			this.params.put(key, value);
			return this;
		}

		@Override
		public PromptSystemSpec params(Map<String, Object> params) {
			Assert.notNull(params, "params cannot be null");
			Assert.noNullElements(params.keySet(), "param keys cannot contain null elements");
			Assert.noNullElements(params.values(), "param values cannot contain null elements");
			this.params.putAll(params);
			return this;
		}

		@Override
		public PromptSystemSpec metadata(Map<String, Object> metadata) {
			Assert.notNull(metadata, "metadata cannot be null");
			Assert.noNullElements(metadata.keySet(), "metadata keys cannot contain null elements");
			Assert.noNullElements(metadata.values(), "metadata values cannot contain null elements");
			this.metadata.putAll(metadata);
			return this;
		}

		@Override
		public PromptSystemSpec metadata(String key, Object value) {
			Assert.hasText(key, "metadata key cannot be null or empty");
			Assert.notNull(value, "metadata value cannot be null");
			this.metadata.put(key, value);
			return this;
		}

		protected @Nullable String text() {
			return this.text;
		}

		protected Map<String, Object> params() {
			return this.params;
		}

		protected Map<String, Object> metadata() {
			return this.metadata;
		}

	}

	public static class DefaultAdvisorSpec implements AdvisorSpec {

		private final List<Advisor> advisors = new ArrayList<>();

		private final Map<String, Object> params = new HashMap<>();

		@Override
		public AdvisorSpec param(String key, Object value) {
			Assert.hasText(key, "key cannot be null or empty");
			Assert.notNull(value, "value cannot be null");
			this.params.put(key, value);
			return this;
		}

		@Override
		public AdvisorSpec params(Map<String, Object> params) {
			Assert.notNull(params, "params cannot be null");
			Assert.noNullElements(params.keySet(), "param keys cannot contain null elements");
			Assert.noNullElements(params.values(), "param values cannot contain null elements");
			this.params.putAll(params);
			return this;
		}

		@Override
		public AdvisorSpec advisors(Advisor... advisors) {
			Assert.notNull(advisors, "advisors cannot be null");
			Assert.noNullElements(advisors, "advisors cannot contain null elements");
			this.advisors.addAll(List.of(advisors));
			return this;
		}

		@Override
		public AdvisorSpec advisors(List<Advisor> advisors) {
			Assert.notNull(advisors, "advisors cannot be null");
			Assert.noNullElements(advisors, "advisors cannot contain null elements");
			this.advisors.addAll(advisors);
			return this;
		}

		public List<Advisor> getAdvisors() {
			return this.advisors;
		}

		public Map<String, Object> getParams() {
			return this.params;
		}

	}

	/**
	 * Default implementation of {@link EntityParamSpec}.
	 */
	public static class DefaultEntityParamSpec implements EntityParamSpec {

		private boolean enableNative = false;

		private boolean validated = false;

		public boolean isEnableNative() {
			return this.enableNative;
		}

		public boolean isValidated() {
			return this.validated;
		}

		@Override
		public EntityParamSpec useProviderStructuredOutput() {
			this.enableNative = true;
			return this;
		}

		@Override
		public EntityParamSpec validateSchema() {
			this.validated = true;
			return this;
		}

	}

	public static class DefaultCallResponseSpec implements CallResponseSpec {

		private final ChatClientRequest request;

		private final BaseAdvisorChain advisorChain;

		private final ObservationRegistry observationRegistry;

		private final ChatClientObservationConvention observationConvention;

		public DefaultCallResponseSpec(ChatClientRequest chatClientRequest, BaseAdvisorChain advisorChain,
				ObservationRegistry observationRegistry, ChatClientObservationConvention observationConvention) {
			Assert.notNull(chatClientRequest, "chatClientRequest cannot be null");
			Assert.notNull(advisorChain, "advisorChain cannot be null");
			Assert.notNull(observationRegistry, "observationRegistry cannot be null");
			Assert.notNull(observationConvention, "observationConvention cannot be null");

			this.request = chatClientRequest;
			this.advisorChain = advisorChain;
			this.observationRegistry = observationRegistry;
			this.observationConvention = observationConvention;
		}

		@Override
		public <T> ResponseEntity<ChatResponse, T> responseEntity(Class<T> type,
				Consumer<EntityParamSpec> entityParamSpecConsumer) {
			Assert.notNull(type, "type cannot be null");
			Assert.notNull(entityParamSpecConsumer, "entityParamSpecConsumer cannot be null");
			var converter = new BeanOutputConverter<>(type);
			return doResponseEntity(converter, resolveAdvisorChain(entityParamSpecConsumer, converter));
		}

		@Override
		public <T> ResponseEntity<ChatResponse, T> responseEntity(Class<T> type) {
			Assert.notNull(type, "type cannot be null");
			return doResponseEntity(new BeanOutputConverter<>(type));
		}

		@Override
		public <T> ResponseEntity<ChatResponse, T> responseEntity(ParameterizedTypeReference<T> type,
				Consumer<EntityParamSpec> entityParamSpecConsumer) {
			Assert.notNull(type, "type cannot be null");
			Assert.notNull(entityParamSpecConsumer, "entityParamSpecConsumer cannot be null");
			var converter = new BeanOutputConverter<>(type);
			return doResponseEntity(converter, resolveAdvisorChain(entityParamSpecConsumer, converter));
		}

		@Override
		public <T> ResponseEntity<ChatResponse, T> responseEntity(ParameterizedTypeReference<T> type) {
			Assert.notNull(type, "type cannot be null");
			return doResponseEntity(new BeanOutputConverter<>(type));
		}

		@Override
		public <T> ResponseEntity<ChatResponse, T> responseEntity(
				StructuredOutputConverter<T> structuredOutputConverter,
				Consumer<EntityParamSpec> entityParamSpecConsumer) {
			Assert.notNull(structuredOutputConverter, "structuredOutputConverter cannot be null");
			Assert.notNull(entityParamSpecConsumer, "entityParamSpecConsumer cannot be null");
			return doResponseEntity(structuredOutputConverter,
					resolveAdvisorChain(entityParamSpecConsumer, structuredOutputConverter));
		}

		@Override
		public <T> ResponseEntity<ChatResponse, T> responseEntity(
				StructuredOutputConverter<T> structuredOutputConverter) {
			Assert.notNull(structuredOutputConverter, "structuredOutputConverter cannot be null");
			return doResponseEntity(structuredOutputConverter);
		}

		protected <T> ResponseEntity<ChatResponse, T> doResponseEntity(StructuredOutputConverter<T> outputConverter) {
			return this.doResponseEntity(outputConverter, this.advisorChain);
		}

		protected <T> ResponseEntity<ChatResponse, T> doResponseEntity(StructuredOutputConverter<T> outputConverter,
				BaseAdvisorChain advisorChain) {

			Assert.notNull(outputConverter, "structuredOutputConverter cannot be null");
			Assert.notNull(advisorChain, "advisor chain cannot be null");

			this.request.context().put(ChatClientAttributes.OUTPUT_FORMAT.getKey(), outputConverter.getFormat());

			if (Boolean.TRUE
				.equals(this.request.context().get(ChatClientAttributes.STRUCTURED_OUTPUT_NATIVE.getKey()))) {
				this.request.context()
					.put(ChatClientAttributes.STRUCTURED_OUTPUT_SCHEMA.getKey(), outputConverter.getJsonSchema());
			}

			var chatResponse = doGetObservableChatClientResponse(this.request, advisorChain).chatResponse();
			var responseContent = getContentFromChatResponse(chatResponse);
			if (responseContent == null) {
				return new ResponseEntity<>(chatResponse, null);
			}
			T entity = outputConverter.convert(responseContent);
			return new ResponseEntity<>(chatResponse, entity);
		}

		@Override
		public <T> @Nullable T entity(ParameterizedTypeReference<T> type,
				Consumer<EntityParamSpec> entitySpecConsumer) {
			Assert.notNull(type, "type cannot be null");
			Assert.notNull(entitySpecConsumer, "entitySpecConsumer cannot be null");
			var converter = new BeanOutputConverter<>(type);
			return doSingleWithBeanOutputConverter(converter, resolveAdvisorChain(entitySpecConsumer, converter));
		}

		@Override
		public <T> @Nullable T entity(Class<T> type, Consumer<EntityParamSpec> entitySpecConsumer) {
			Assert.notNull(type, "type cannot be null");
			Assert.notNull(entitySpecConsumer, "entitySpecConsumer cannot be null");
			var converter = new BeanOutputConverter<>(type);
			return doSingleWithBeanOutputConverter(converter, resolveAdvisorChain(entitySpecConsumer, converter));
		}

		@Override
		public <T> @Nullable T entity(ParameterizedTypeReference<T> type) {
			Assert.notNull(type, "type cannot be null");
			return doSingleWithBeanOutputConverter(new BeanOutputConverter<>(type));
		}

		@Override
		public <T> @Nullable T entity(StructuredOutputConverter<T> structuredOutputConverter,
				Consumer<EntityParamSpec> entitySpecConsumer) {
			Assert.notNull(structuredOutputConverter, "structuredOutputConverter cannot be null");
			Assert.notNull(entitySpecConsumer, "entitySpecConsumer cannot be null");
			return doSingleWithBeanOutputConverter(structuredOutputConverter,
					resolveAdvisorChain(entitySpecConsumer, structuredOutputConverter));
		}

		@Override
		public <T> @Nullable T entity(StructuredOutputConverter<T> structuredOutputConverter) {
			Assert.notNull(structuredOutputConverter, "structuredOutputConverter cannot be null");
			return doSingleWithBeanOutputConverter(structuredOutputConverter);
		}

		@Override
		public <T> @Nullable T entity(Class<T> type) {
			Assert.notNull(type, "type cannot be null");
			return doSingleWithBeanOutputConverter(new BeanOutputConverter<>(type));
		}

		private BaseAdvisorChain resolveAdvisorChain(Consumer<EntityParamSpec> consumer,
				StructuredOutputConverter<?> converter) {
			var spec = new DefaultEntityParamSpec();
			consumer.accept(spec);
			if (spec.isEnableNative()) {
				this.request.context().put(ChatClientAttributes.STRUCTURED_OUTPUT_NATIVE.getKey(), true);
			}
			if (spec.isValidated()) {
				var validationAdvisor = StructuredOutputValidationAdvisor.builder()
					.outputJsonSchema(converter.getJsonSchema())
					.build();
				return this.advisorChain.mutate().push(validationAdvisor).build();
			}
			return this.advisorChain;
		}

		private <T> @Nullable T doSingleWithBeanOutputConverter(StructuredOutputConverter<T> outputConverter) {
			return doSingleWithBeanOutputConverter(outputConverter, this.advisorChain);
		}

		private <T> @Nullable T doSingleWithBeanOutputConverter(StructuredOutputConverter<T> outputConverter,
				BaseAdvisorChain advisorChain) {

			if (StringUtils.hasText(outputConverter.getFormat())) {
				// Used for default structured output format support, based on prompt
				// instructions.
				this.request.context().put(ChatClientAttributes.OUTPUT_FORMAT.getKey(), outputConverter.getFormat());
			}

			if (Boolean.TRUE
				.equals(this.request.context().get(ChatClientAttributes.STRUCTURED_OUTPUT_NATIVE.getKey()))) {
				// Used for native structured output support, e.g. AI model API should
				// provide structured output support.
				this.request.context()
					.put(ChatClientAttributes.STRUCTURED_OUTPUT_SCHEMA.getKey(), outputConverter.getJsonSchema());

			}

			var chatResponse = doGetObservableChatClientResponse(this.request, advisorChain).chatResponse();

			var stringResponse = getContentFromChatResponse(chatResponse);
			if (stringResponse == null) {
				return null;
			}
			return outputConverter.convert(stringResponse);
		}

		@Override
		public ChatClientResponse chatClientResponse() {
			return doGetObservableChatClientResponse(this.request);
		}

		@Override
		public @Nullable ChatResponse chatResponse() {
			return doGetObservableChatClientResponse(this.request).chatResponse();
		}

		@Override
		public @Nullable String content() {
			ChatResponse chatResponse = doGetObservableChatClientResponse(this.request).chatResponse();
			return getContentFromChatResponse(chatResponse);
		}

		private ChatClientResponse doGetObservableChatClientResponse(ChatClientRequest chatClientRequest) {
			return doGetObservableChatClientResponse(chatClientRequest, this.advisorChain);
		}

		private ChatClientResponse doGetObservableChatClientResponse(ChatClientRequest chatClientRequest,
				BaseAdvisorChain advisorChain) {

			String outputFormat = (String) chatClientRequest.context()
				.getOrDefault(ChatClientAttributes.OUTPUT_FORMAT.getKey(), null);

			ChatClientObservationContext observationContext = ChatClientObservationContext.builder()
				.request(chatClientRequest)
				.advisors(advisorChain.getCallAdvisors())
				.stream(false)
				.format(outputFormat)
				.build();

			var observation = ChatClientObservationDocumentation.AI_CHAT_CLIENT.observation(this.observationConvention,
					DEFAULT_CHAT_CLIENT_OBSERVATION_CONVENTION, () -> observationContext, this.observationRegistry);

			// CHECKSTYLE:OFF
			var chatClientResponse = observation.observe(() -> {
				// Apply the advisor chain that terminates with the ChatModelCallAdvisor.
				var response = advisorChain.nextCall(chatClientRequest);
				observationContext.setResponse(response);
				return response;
			});
			// CHECKSTYLE:ON
			return chatClientResponse != null ? chatClientResponse : ChatClientResponse.builder().build();
		}

		private static @Nullable String getContentFromChatResponse(@Nullable ChatResponse chatResponse) {
			return Optional.ofNullable(chatResponse)
				.map(ChatResponse::getResult)
				.map(Generation::getOutput)
				.map(AbstractMessage::getText)
				.orElse(null);
		}

	}

	public static class DefaultStreamResponseSpec implements StreamResponseSpec {

		private final ChatClientRequest request;

		private final BaseAdvisorChain advisorChain;

		private final ObservationRegistry observationRegistry;

		private final ChatClientObservationConvention observationConvention;

		public DefaultStreamResponseSpec(ChatClientRequest chatClientRequest, BaseAdvisorChain advisorChain,
				ObservationRegistry observationRegistry, ChatClientObservationConvention observationConvention) {
			Assert.notNull(chatClientRequest, "chatClientRequest cannot be null");
			Assert.notNull(advisorChain, "advisorChain cannot be null");
			Assert.notNull(observationRegistry, "observationRegistry cannot be null");
			Assert.notNull(observationConvention, "observationConvention cannot be null");

			this.request = chatClientRequest;
			this.advisorChain = advisorChain;
			this.observationRegistry = observationRegistry;
			this.observationConvention = observationConvention;
		}

		private Flux<ChatClientResponse> doGetObservableFluxChatResponse(ChatClientRequest chatClientRequest) {
			return Flux.deferContextual(contextView -> {

				ChatClientObservationContext observationContext = ChatClientObservationContext.builder()
					.request(chatClientRequest)
					.advisors(this.advisorChain.getStreamAdvisors())
					.stream(true)
					.build();

				Observation observation = ChatClientObservationDocumentation.AI_CHAT_CLIENT.observation(
						this.observationConvention, DEFAULT_CHAT_CLIENT_OBSERVATION_CONVENTION,
						() -> observationContext, this.observationRegistry);

				Observation parentObservation = contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null);
				observation.parentObservation(parentObservation);
				// Briefly make the parent observation current while starting this one, so
				// Micrometer tracing derives the span's parent from the parent
				// observation rather
				// than from whatever scope happens to be open on the current thread (e.g.
				// the
				// servlet HTTP span). This keeps span parenting correct without relying
				// on
				// automatic context propagation.
				try (Observation.Scope ignored = parentObservation != null ? parentObservation.openScope()
						: Observation.Scope.NOOP) {
					observation.start();
				}

				// @formatter:off
				// Apply the advisor chain that terminates with the ChatModelStreamAdvisor.
				Flux<ChatClientResponse> chatClientResponse = this.advisorChain.nextStream(chatClientRequest)
						.doOnError(observation::error)
						.doFinally(s -> observation.stop())
						.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
				// @formatter:on
				return CHAT_CLIENT_MESSAGE_AGGREGATOR.aggregateChatClientResponse(chatClientResponse,
						observationContext::setResponse);
			});
		}

		@Override
		public Flux<ChatClientResponse> chatClientResponse() {
			return doGetObservableFluxChatResponse(this.request);
		}

		@Override
		@SuppressWarnings("NullAway") // https://github.com/uber/NullAway/issues/1290
		public Flux<ChatResponse> chatResponse() {
			return doGetObservableFluxChatResponse(this.request).mapNotNull(ChatClientResponse::chatResponse);
		}

		@Override
		public Flux<String> content() {
			// @formatter:off
			return chatResponse()
					.map(r -> Optional.ofNullable(r.getResult())
							.map(Generation::getOutput)
							.map(AbstractMessage::getText)
							.orElse(""))
					.filter(StringUtils::hasLength);
			// @formatter:on
		}

	}

	public static class DefaultToolSpec implements ToolSpec {

		private final Map<String, Object> context = new HashMap<>();

		private final List<ToolCallback> toolCallbacks = new ArrayList<>();

		private final List<ToolCallbackProvider> toolCallbackProviders = new ArrayList<>();

		private @Nullable ToolAdvisor toolAdvisor;

		public Map<String, Object> getContext() {
			return this.context;
		}

		public List<ToolCallback> getToolCallbacks() {
			return this.toolCallbacks;
		}

		public List<ToolCallbackProvider> getToolCallbackProviders() {
			return this.toolCallbackProviders;
		}

		public @Nullable ToolAdvisor getAdvisor() {
			return this.toolAdvisor;
		}

		@Override
		public ToolSpec context(String key, Object value) {
			Assert.hasText(key, "context key cannot be null or empty");
			Assert.notNull(value, "context value cannot be null");
			this.context.put(key, value);
			return this;
		}

		@Override
		public ToolSpec context(Map<String, Object> context) {
			Assert.notNull(context, "context cannot be null");
			Assert.noNullElements(context.keySet(), "context keys cannot contain null elements");
			Assert.noNullElements(context.values(), "context values cannot contain null elements");
			this.context.putAll(context);
			return this;
		}

		@Override
		public ToolSpec instances(Object... toolObjects) {
			Assert.notNull(toolObjects, "toolObjects cannot be null");
			Assert.noNullElements(toolObjects, "toolObjects cannot contain null elements");
			this.toolCallbacks.addAll(Arrays.asList(ToolCallbacks.from(toolObjects)));
			return this;
		}

		@Override
		public ToolSpec instances(List<Object> toolObjects) {
			Assert.notNull(toolObjects, "toolObjects cannot be null");
			Assert.noNullElements(toolObjects, "toolObjects cannot contain null elements");
			this.toolCallbacks.addAll(Arrays.asList(ToolCallbacks.from(toolObjects.toArray(new Object[0]))));
			return this;
		}

		@Override
		public ToolSpec callbacks(ToolCallback... toolCallbacks) {
			Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
			Assert.noNullElements(toolCallbacks, "toolCallbacks cannot contain null elements");
			this.toolCallbacks.addAll(Arrays.asList(toolCallbacks));
			return this;
		}

		@Override
		public ToolSpec callbacks(List<ToolCallback> toolCallbacks) {
			Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
			Assert.noNullElements(toolCallbacks, "toolCallbacks cannot contain null elements");
			this.toolCallbacks.addAll(toolCallbacks);
			return this;
		}

		@Override
		public ToolSpec callbacks(ToolCallbackProvider... toolCallbackProvider) {
			Assert.notNull(toolCallbackProvider, "toolCallbackProvider cannot be null");
			Assert.noNullElements(toolCallbackProvider, "toolCallbackProvider cannot contain null elements");
			this.toolCallbackProviders.addAll(Arrays.asList(toolCallbackProvider));
			return this;
		}

		@Override
		public ToolSpec advisor(ToolAdvisor toolAdvisor) {
			Assert.notNull(toolAdvisor, "toolAdvisor cannot be null");
			this.toolAdvisor = toolAdvisor;
			return this;
		}

	}

	public static class DefaultChatClientRequestSpec implements ChatClientRequestSpec {

		private final ObservationRegistry observationRegistry;

		private final ChatClientObservationConvention chatClientObservationConvention;

		private final @Nullable AdvisorObservationConvention advisorObservationConvention;

		private final ChatModel chatModel;

		private final List<Media> media = new ArrayList<>();

		private final List<String> toolNames = new ArrayList<>();

		private final List<ToolCallback> toolCallbacks = new ArrayList<>();

		private final List<ToolCallbackProvider> toolCallbackProviders = new ArrayList<>();

		private final List<Message> messages = new ArrayList<>();

		private final Map<String, Object> userParams = new HashMap<>();

		private final Map<String, Object> userMetadata = new HashMap<>();

		private final Map<String, Object> systemParams = new HashMap<>();

		private final Map<String, Object> systemMetadata = new HashMap<>();

		private final List<Advisor> advisors = new ArrayList<>();

		private final Map<String, Object> advisorParams = new HashMap<>();

		private final Map<String, Object> toolContext = new HashMap<>();

		private TemplateRenderer templateRenderer;

		private @Nullable String userText;

		private @Nullable String systemText;

		private ChatOptions.@Nullable Builder<?> optionsCustomizer;

		private final ToolCallAdvisor.Builder<?> toolCallAdvisorBuilder;

		/* copy constructor */
		DefaultChatClientRequestSpec(DefaultChatClientRequestSpec ccr) {
			this(ccr.chatModel, ccr.userText, ccr.userParams, ccr.userMetadata, ccr.systemText, ccr.systemParams,
					ccr.systemMetadata, ccr.toolCallbacks, ccr.toolCallbackProviders, ccr.messages, ccr.toolNames,
					ccr.media, ccr.optionsCustomizer, ccr.advisors, ccr.advisorParams, ccr.observationRegistry,
					ccr.chatClientObservationConvention, ccr.toolContext, ccr.templateRenderer,
					ccr.advisorObservationConvention, ccr.toolCallAdvisorBuilder);
		}

		protected DefaultChatClientRequestSpec(ChatModel chatModel, @Nullable String userText,
				Map<String, Object> userParams, Map<String, Object> userMetadata, @Nullable String systemText,
				Map<String, Object> systemParams, Map<String, Object> systemMetadata, List<ToolCallback> toolCallbacks,
				List<ToolCallbackProvider> toolCallbackProviders, List<Message> messages, List<String> toolNames,
				List<Media> media, ChatOptions.@Nullable Builder<?> customizer, List<Advisor> advisors,
				Map<String, Object> advisorParams, ObservationRegistry observationRegistry,
				@Nullable ChatClientObservationConvention chatClientObservationConvention,
				Map<String, Object> toolContext, @Nullable TemplateRenderer templateRenderer,
				@Nullable AdvisorObservationConvention advisorObservationConvention,
				ToolCallAdvisor.Builder<?> toolCallAdvisorBuilder) {

			Assert.notNull(chatModel, "chatModel cannot be null");
			Assert.notNull(userParams, "userParams cannot be null");
			Assert.notNull(userMetadata, "userMetadata cannot be null");
			Assert.notNull(systemParams, "systemParams cannot be null");
			Assert.notNull(systemMetadata, "systemMetadata cannot be null");
			Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
			Assert.notNull(toolCallbackProviders, "toolCallbackProviders cannot be null");
			Assert.notNull(messages, "messages cannot be null");
			Assert.notNull(toolNames, "toolNames cannot be null");
			Assert.notNull(media, "media cannot be null");
			Assert.notNull(advisors, "advisors cannot be null");
			Assert.notNull(advisorParams, "advisorParams cannot be null");
			Assert.notNull(observationRegistry, "observationRegistry cannot be null");
			Assert.notNull(toolContext, "toolContext cannot be null");

			this.chatModel = chatModel;
			this.toolCallAdvisorBuilder = toolCallAdvisorBuilder;
			this.optionsCustomizer = customizer != null ? customizer.clone() : null;

			this.userText = userText;
			this.userParams.putAll(userParams);
			this.userMetadata.putAll(userMetadata);

			this.systemText = systemText;
			this.systemParams.putAll(systemParams);
			this.systemMetadata.putAll(systemMetadata);

			this.toolNames.addAll(toolNames);
			this.toolCallbacks.addAll(toolCallbacks);
			this.toolCallbackProviders.addAll(toolCallbackProviders);
			this.messages.addAll(messages);
			this.media.addAll(media);
			this.advisors.addAll(advisors);
			this.advisorParams.putAll(advisorParams);
			this.observationRegistry = observationRegistry;
			this.chatClientObservationConvention = chatClientObservationConvention != null
					? chatClientObservationConvention : DEFAULT_CHAT_CLIENT_OBSERVATION_CONVENTION;
			this.toolContext.putAll(toolContext);
			this.templateRenderer = templateRenderer != null ? templateRenderer : DEFAULT_TEMPLATE_RENDERER;
			this.advisorObservationConvention = advisorObservationConvention;
		}

		public @Nullable String getUserText() {
			return this.userText;
		}

		public Map<String, Object> getUserParams() {
			return this.userParams;
		}

		public Map<String, Object> getUserMetadata() {
			return this.userMetadata;
		}

		public @Nullable String getSystemText() {
			return this.systemText;
		}

		public Map<String, Object> getSystemParams() {
			return this.systemParams;
		}

		public Map<String, Object> getSystemMetadata() {
			return this.systemMetadata;
		}

		public List<Advisor> getAdvisors() {
			return this.advisors;
		}

		public Map<String, Object> getAdvisorParams() {
			return this.advisorParams;
		}

		public List<Message> getMessages() {
			return this.messages;
		}

		public List<Media> getMedia() {
			return this.media;
		}

		public List<String> getToolNames() {
			return this.toolNames;
		}

		public List<ToolCallback> getToolCallbacks() {
			return this.toolCallbacks;
		}

		public List<ToolCallbackProvider> getToolCallbackProviders() {
			return this.toolCallbackProviders;
		}

		public Map<String, Object> getToolContext() {
			return this.toolContext;
		}

		public TemplateRenderer getTemplateRenderer() {
			return this.templateRenderer;
		}

		/* package */ ChatModel getChatModel() {
			return this.chatModel;
		}

		/* package */ ChatOptions.@Nullable Builder<?> getOptionsCustomizer() {
			return this.optionsCustomizer;
		}

		/**
		 * Return a {@link ChatClient.Builder} to create a new {@link ChatClient} whose
		 * settings are replicated from this {@link ChatClientRequest}.
		 */
		@Override
		public Builder mutate() {
			DefaultChatClientBuilder builder = (DefaultChatClientBuilder) ChatClient
				.builder(this.chatModel, this.observationRegistry, this.chatClientObservationConvention,
						this.advisorObservationConvention, this.toolCallAdvisorBuilder)
				.defaultTemplateRenderer(this.templateRenderer)
				.defaultTools(t -> t.callbacks(this.toolCallbacks)
					.callbacks(this.toolCallbackProviders.toArray(new ToolCallbackProvider[0]))
					.context(this.toolContext))
				.defaultToolNames(StringUtils.toStringArray(this.toolNames));

			if (!CollectionUtils.isEmpty(this.advisors)) {
				builder.defaultAdvisors(a -> a.advisors(this.advisors).params(this.advisorParams));
			}

			if (StringUtils.hasText(this.userText)) {
				String text = this.userText;
				builder.defaultUser(u -> u.text(text)
					.params(this.userParams)
					.media(this.media.toArray(new Media[0]))
					.metadata(this.userMetadata));
			}

			if (StringUtils.hasText(this.systemText)) {
				String text = this.systemText;
				builder.defaultSystem(s -> s.text(text).params(this.systemParams).metadata(this.systemMetadata));
			}

			if (this.optionsCustomizer != null) {
				builder.defaultOptions(this.optionsCustomizer);
			}

			builder.addMessages(this.messages);

			return builder;
		}

		@Override
		public ChatClientRequestSpec tools(Consumer<ToolSpec> consumer) {
			Assert.notNull(consumer, "consumer cannot be null");
			var toolSpec = new DefaultToolSpec();
			consumer.accept(toolSpec);

			this.toolContext.putAll(toolSpec.getContext());
			this.toolCallbacks.addAll(toolSpec.getToolCallbacks());
			this.toolCallbackProviders.addAll(toolSpec.getToolCallbackProviders());
			if (toolSpec.getAdvisor() != null) {
				this.advisors.add(toolSpec.getAdvisor());
			}

			return this;
		}

		@Override
		public ChatClientRequestSpec advisors(Consumer<ChatClient.AdvisorSpec> consumer) {
			Assert.notNull(consumer, "consumer cannot be null");
			var advisorSpec = new DefaultAdvisorSpec();
			consumer.accept(advisorSpec);
			this.advisorParams.putAll(advisorSpec.getParams());
			this.advisors.addAll(advisorSpec.getAdvisors());
			return this;
		}

		@Override
		public ChatClientRequestSpec advisors(Advisor... advisors) {
			Assert.notNull(advisors, "advisors cannot be null");
			Assert.noNullElements(advisors, "advisors cannot contain null elements");
			this.advisors.addAll(Arrays.asList(advisors));
			return this;
		}

		@Override
		public ChatClientRequestSpec advisors(List<Advisor> advisors) {
			Assert.notNull(advisors, "advisors cannot be null");
			Assert.noNullElements(advisors, "advisors cannot contain null elements");
			this.advisors.addAll(advisors);
			return this;
		}

		@Override
		public ChatClientRequestSpec messages(Message... messages) {
			Assert.notNull(messages, "messages cannot be null");
			Assert.noNullElements(messages, "messages cannot contain null elements");
			this.messages.addAll(List.of(messages));
			return this;
		}

		@Override
		public ChatClientRequestSpec messages(List<Message> messages) {
			Assert.notNull(messages, "messages cannot be null");
			Assert.noNullElements(messages, "messages cannot contain null elements");
			this.messages.addAll(messages);
			return this;
		}

		@Override
		public <B extends ChatOptions.Builder<?>> ChatClientRequestSpec options(B customizer) {
			Assert.notNull(customizer, "customizer cannot be null");
			this.optionsCustomizer = customizer;
			return this;
		}

		@Override
		public ChatClientRequestSpec toolNames(String... toolNames) {
			Assert.notNull(toolNames, "toolNames cannot be null");
			Assert.noNullElements(toolNames, "toolNames cannot contain null elements");
			this.toolNames.addAll(List.of(toolNames));
			return this;
		}

		@Override
		public ChatClientRequestSpec toolCallbacks(ToolCallback... toolCallbacks) {
			return tools(t -> t.callbacks(toolCallbacks));
		}

		@Override
		public ChatClientRequestSpec toolCallbacks(List<ToolCallback> toolCallbacks) {
			return tools(t -> t.callbacks(toolCallbacks));
		}

		@Override
		public ChatClientRequestSpec tools(Object... toolObjects) {
			Assert.notNull(toolObjects, "toolObjects cannot be null");
			Assert.noNullElements(toolObjects, "toolObjects cannot contain null elements");
			this.toolCallbacks.addAll(Arrays.asList(ToolCallbacks.from(toolObjects)));
			return this;
		}

		@Override
		public ChatClientRequestSpec toolCallbacks(ToolCallbackProvider... toolCallbackProviders) {
			return tools(t -> t.callbacks(toolCallbackProviders));
		}

		@Override
		public ChatClientRequestSpec toolContext(Map<String, Object> toolContext) {
			return tools(t -> t.context(toolContext));
		}

		@Override
		public ChatClientRequestSpec system(String text) {
			Assert.hasText(text, "text cannot be null or empty");
			this.systemText = text;
			return this;
		}

		@Override
		public ChatClientRequestSpec system(Resource text, Charset charset) {
			Assert.notNull(text, "text cannot be null");
			Assert.notNull(charset, "charset cannot be null");

			try {
				this.systemText = text.getContentAsString(charset);
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
			return this;
		}

		@Override
		public ChatClientRequestSpec system(Resource text) {
			Assert.notNull(text, "text cannot be null");
			return this.system(text, Charset.defaultCharset());
		}

		@Override
		public ChatClientRequestSpec system(Consumer<PromptSystemSpec> consumer) {
			Assert.notNull(consumer, "consumer cannot be null");

			var systemSpec = new DefaultPromptSystemSpec();
			consumer.accept(systemSpec);
			this.systemText = StringUtils.hasText(systemSpec.text()) ? systemSpec.text() : this.systemText;
			this.systemParams.putAll(systemSpec.params());
			this.systemMetadata.putAll(systemSpec.metadata());
			return this;
		}

		@Override
		public ChatClientRequestSpec user(String text) {
			Assert.hasText(text, "text cannot be null or empty");
			this.userText = text;
			return this;
		}

		@Override
		public ChatClientRequestSpec user(Resource text, Charset charset) {
			Assert.notNull(text, "text cannot be null");
			Assert.notNull(charset, "charset cannot be null");

			try {
				this.userText = text.getContentAsString(charset);
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
			return this;
		}

		@Override
		public ChatClientRequestSpec user(Resource text) {
			Assert.notNull(text, "text cannot be null");
			return this.user(text, Charset.defaultCharset());
		}

		@Override
		public ChatClientRequestSpec user(Consumer<PromptUserSpec> consumer) {
			Assert.notNull(consumer, "consumer cannot be null");

			var us = new DefaultPromptUserSpec();
			consumer.accept(us);
			this.userText = StringUtils.hasText(us.text()) ? us.text() : this.userText;
			this.userParams.putAll(us.params());
			this.media.addAll(us.media());
			this.userMetadata.putAll(us.metadata());
			return this;
		}

		@Override
		public ChatClientRequestSpec templateRenderer(TemplateRenderer templateRenderer) {
			Assert.notNull(templateRenderer, "templateRenderer cannot be null");
			this.templateRenderer = templateRenderer;
			return this;
		}

		@Override
		public CallResponseSpec call() {
			BaseAdvisorChain advisorChain = buildAdvisorChain();
			return new DefaultCallResponseSpec(DefaultChatClientUtils.toChatClientRequest(this), advisorChain,
					this.observationRegistry, this.chatClientObservationConvention);
		}

		@Override
		public StreamResponseSpec stream() {
			BaseAdvisorChain advisorChain = buildAdvisorChain();
			return new DefaultStreamResponseSpec(DefaultChatClientUtils.toChatClientRequest(this), advisorChain,
					this.observationRegistry, this.chatClientObservationConvention);
		}

		private BaseAdvisorChain buildAdvisorChain() {
			autoRegisterToolCallAdvisor();
			validateSingleToolAdvisor();
			warnOnMemoryAdvisorOrderMismatch();

			// At the stack bottom add the model call advisors.
			// They play the role of the last advisors in the advisor chain.
			this.advisors.add(ChatModelCallAdvisor.builder().chatModel(this.chatModel).build());
			this.advisors.add(ChatModelStreamAdvisor.builder().chatModel(this.chatModel).build());

			return DefaultAroundAdvisorChain.builder(this.observationRegistry)
				.observationConvention(this.advisorObservationConvention)
				.pushAll(this.advisors)
				.build();
		}

		/**
		 * Auto-registers a {@link ToolCallAdvisor} when tools are configured but no
		 * {@link ToolAdvisor} is present. Disables the advisor's internal conversation
		 * history when a {@link BaseChatMemoryAdvisor} with a higher order (i.e.
		 * downstream in the request direction) is already registered, since that memory
		 * advisor will handle history for every tool-call iteration.
		 * <p>
		 * {@code streamToolCallResponses} must be pre-configured on the
		 * {@code toolCallAdvisorBuilder} passed to {@link DefaultChatClient}.
		 */
		private void autoRegisterToolCallAdvisor() {

			boolean autoRegisterDisabled = Boolean.FALSE
				.equals(this.advisorParams.get(ChatClientAttributes.TOOL_CALL_ADVISOR_AUTO_REGISTER.getKey()));
			if (autoRegisterDisabled) {
				return;
			}

			boolean hasTools = !this.toolCallbacks.isEmpty() || !this.toolCallbackProviders.isEmpty()
					|| !this.toolNames.isEmpty() || hasToolsInChatOptions(this.optionsCustomizer)
					|| hasToolsInChatOptions(this.chatModel.getOptions());
			if (!hasTools) {
				return;
			}

			boolean hasToolCallAdvisor = this.advisors.stream().anyMatch(a -> a instanceof ToolAdvisor);
			if (hasToolCallAdvisor) {
				return;
			}

			int configuredOrder = this.toolCallAdvisorBuilder.getAdvisorOrder();

			boolean hasDownstreamMemoryAdvisor = this.advisors.stream()
				.anyMatch(a -> a instanceof MemoryAdvisor && a.getOrder() > configuredOrder);

			this.advisors.add(
					this.toolCallAdvisorBuilder.copy().conversationHistoryEnabled(!hasDownstreamMemoryAdvisor).build());
		}

		private static boolean hasToolsInChatOptions(@Nullable Object options) {
			ToolCallingChatOptions tco = null;
			if (options instanceof ToolCallingChatOptions direct) {
				tco = direct;
			}
			else if (options instanceof ToolCallingChatOptions.Builder<?> builder) {
				tco = (ToolCallingChatOptions) builder.build();
			}
			return tco != null && ((tco.getToolCallbacks() != null && !tco.getToolCallbacks().isEmpty())
					|| (tco.getToolNames() != null && !tco.getToolNames().isEmpty()));
		}

		private void validateSingleToolAdvisor() {
			List<Advisor> toolAdvisors = this.advisors.stream().filter(a -> a instanceof ToolAdvisor).toList();
			if (toolAdvisors.size() > 1) {
				String names = String.join(", ",
						toolAdvisors.stream().map(a -> a.getName() + " (order=" + a.getOrder() + ")").toList());
				throw new IllegalStateException("At most one ToolAdvisor is allowed in the advisor chain, but found "
						+ toolAdvisors.size() + ": [" + names + "]");
			}
		}

		/**
		 * Warns when a {@link MemoryAdvisor} is ordered before (lower order than) a
		 * {@link ToolAdvisor}. In that configuration the memory advisor is not part of
		 * the recursive tool-call chain, so tool messages will not be stored between
		 * iterations and streaming memory updates will not be sequenced correctly.
		 */
		private void warnOnMemoryAdvisorOrderMismatch() {
			this.advisors.stream()
				.filter(a -> a instanceof ToolAdvisor)
				.forEach(tca -> this.advisors.stream()
					.filter(a -> a instanceof MemoryAdvisor && a.getOrder() <= tca.getOrder())
					.forEach(mem -> logger.warn(
							"ChatMemoryAdvisor '{}' (order={}) is ordered at or before ToolCallAdvisor '{}' (order={}). "
									+ "Memory will not be updated between tool-call iterations. "
									+ "Set the memory advisor order above {} to fix this.",
							mem.getName(), mem.getOrder(), tca.getName(), tca.getOrder(), tca.getOrder())));
		}

	}

}
