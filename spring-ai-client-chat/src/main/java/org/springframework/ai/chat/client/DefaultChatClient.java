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
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.advisor.ChatModelCallAdvisor;
import org.springframework.ai.chat.client.advisor.ChatModelStreamAdvisor;
import org.springframework.ai.chat.client.advisor.DefaultAroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisorChain;
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
import org.springframework.ai.template.TemplateRenderer;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
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
 * @since 1.0.0
 */
public class DefaultChatClient implements ChatClient {

	private static final ChatClientObservationConvention DEFAULT_CHAT_CLIENT_OBSERVATION_CONVENTION = new DefaultChatClientObservationConvention();

	private static final TemplateRenderer DEFAULT_TEMPLATE_RENDERER = StTemplateRenderer.builder().build();

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

		// Options
		if (prompt.getOptions() != null) {
			spec.options(prompt.getOptions());
		}

		// Messages
		if (prompt.getInstructions() != null) {
			spec.messages(prompt.getInstructions());
		}

		return spec;
	}

	/**
	 * Return a {@code ChatClient2Builder} to create a new {@code ChatClient} whose
	 * settings are replicated from this {@code ChatClientRequest}.
	 */
	@Override
	public Builder mutate() {
		return this.defaultChatClientRequest.mutate();
	}

	public static class DefaultPromptUserSpec implements PromptUserSpec {

		private final Map<String, Object> params = new HashMap<>();

		private final List<Media> media = new ArrayList<>();

		@Nullable
		private String text;

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

		@Nullable
		protected String text() {
			return this.text;
		}

		protected Map<String, Object> params() {
			return this.params;
		}

		protected List<Media> media() {
			return this.media;
		}

	}

	public static class DefaultPromptSystemSpec implements PromptSystemSpec {

		private final Map<String, Object> params = new HashMap<>();

		@Nullable
		private String text;

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

		@Nullable
		protected String text() {
			return this.text;
		}

		protected Map<String, Object> params() {
			return this.params;
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
		public <T> ResponseEntity<ChatResponse, T> responseEntity(Class<T> type) {
			Assert.notNull(type, "type cannot be null");
			return doResponseEntity(new BeanOutputConverter<T>(type));
		}

		@Override
		public <T> ResponseEntity<ChatResponse, T> responseEntity(ParameterizedTypeReference<T> type) {
			Assert.notNull(type, "type cannot be null");
			return doResponseEntity(new BeanOutputConverter<T>(type));
		}

		@Override
		public <T> ResponseEntity<ChatResponse, T> responseEntity(
				StructuredOutputConverter<T> structuredOutputConverter) {
			Assert.notNull(structuredOutputConverter, "structuredOutputConverter cannot be null");
			return doResponseEntity(structuredOutputConverter);
		}

		protected <T> ResponseEntity<ChatResponse, T> doResponseEntity(StructuredOutputConverter<T> outputConverter) {
			Assert.notNull(outputConverter, "structuredOutputConverter cannot be null");
			var chatResponse = doGetObservableChatClientResponse(this.request, outputConverter.getFormat())
				.chatResponse();
			var responseContent = getContentFromChatResponse(chatResponse);
			if (responseContent == null) {
				return new ResponseEntity<>(chatResponse, null);
			}
			T entity = outputConverter.convert(responseContent);
			return new ResponseEntity<>(chatResponse, entity);
		}

		@Override
		@Nullable
		public <T> T entity(ParameterizedTypeReference<T> type) {
			Assert.notNull(type, "type cannot be null");
			return doSingleWithBeanOutputConverter(new BeanOutputConverter<>(type));
		}

		@Override
		@Nullable
		public <T> T entity(StructuredOutputConverter<T> structuredOutputConverter) {
			Assert.notNull(structuredOutputConverter, "structuredOutputConverter cannot be null");
			return doSingleWithBeanOutputConverter(structuredOutputConverter);
		}

		@Override
		@Nullable
		public <T> T entity(Class<T> type) {
			Assert.notNull(type, "type cannot be null");
			var outputConverter = new BeanOutputConverter<>(type);
			return doSingleWithBeanOutputConverter(outputConverter);
		}

		@Nullable
		private <T> T doSingleWithBeanOutputConverter(StructuredOutputConverter<T> outputConverter) {
			var chatResponse = doGetObservableChatClientResponse(this.request, outputConverter.getFormat())
				.chatResponse();
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
		@Nullable
		public ChatResponse chatResponse() {
			return doGetObservableChatClientResponse(this.request).chatResponse();
		}

		@Override
		@Nullable
		public String content() {
			ChatResponse chatResponse = doGetObservableChatClientResponse(this.request).chatResponse();
			return getContentFromChatResponse(chatResponse);
		}

		private ChatClientResponse doGetObservableChatClientResponse(ChatClientRequest chatClientRequest) {
			return doGetObservableChatClientResponse(chatClientRequest, null);
		}

		private ChatClientResponse doGetObservableChatClientResponse(ChatClientRequest chatClientRequest,
				@Nullable String outputFormat) {

			if (outputFormat != null) {
				chatClientRequest.context().put(ChatClientAttributes.OUTPUT_FORMAT.getKey(), outputFormat);
			}

			ChatClientObservationContext observationContext = ChatClientObservationContext.builder()
				.request(chatClientRequest)
				.advisors(advisorChain.getCallAdvisors())
				.stream(false)
				.format(outputFormat)
				.build();

			var observation = ChatClientObservationDocumentation.AI_CHAT_CLIENT.observation(observationConvention,
					DEFAULT_CHAT_CLIENT_OBSERVATION_CONVENTION, () -> observationContext, observationRegistry);
			var chatClientResponse = observation.observe(() -> {
				// Apply the advisor chain that terminates with the ChatModelCallAdvisor.
				return advisorChain.nextCall(chatClientRequest);
			});
			return chatClientResponse != null ? chatClientResponse : ChatClientResponse.builder().build();
		}

		@Nullable
		private static String getContentFromChatResponse(@Nullable ChatResponse chatResponse) {
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
					.advisors(advisorChain.getStreamAdvisors())
					.stream(true)
					.build();

				Observation observation = ChatClientObservationDocumentation.AI_CHAT_CLIENT.observation(
						observationConvention, DEFAULT_CHAT_CLIENT_OBSERVATION_CONVENTION, () -> observationContext,
						observationRegistry);

				observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null))
					.start();

				// @formatter:off
				// Apply the advisor chain that terminates with the ChatModelStreamAdvisor.
				return advisorChain.nextStream(chatClientRequest)
						.doOnError(observation::error)
						.doFinally(s -> observation.stop())
						.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
				// @formatter:on
			});
		}

		@Override
		public Flux<ChatClientResponse> chatClientResponse() {
			return doGetObservableFluxChatResponse(this.request);
		}

		@Override
		public Flux<ChatResponse> chatResponse() {
			return doGetObservableFluxChatResponse(this.request).mapNotNull(ChatClientResponse::chatResponse);
		}

		@Override
		public Flux<String> content() {
			// @formatter:off
			return doGetObservableFluxChatResponse(this.request)
					.mapNotNull(ChatClientResponse::chatResponse)
					.map(r -> {
						if (r.getResult() == null || r.getResult().getOutput() == null
								|| r.getResult().getOutput().getText() == null) {
							return "";
						}
						return r.getResult().getOutput().getText();
					})
					.filter(StringUtils::hasLength);
			// @formatter:on
		}

	}

	public static class DefaultChatClientRequestSpec implements ChatClientRequestSpec {

		private final ObservationRegistry observationRegistry;

		private final ChatClientObservationConvention observationConvention;

		private final ChatModel chatModel;

		private final List<Media> media = new ArrayList<>();

		private final List<String> toolNames = new ArrayList<>();

		private final List<ToolCallback> toolCallbacks = new ArrayList<>();

		private final List<Message> messages = new ArrayList<>();

		private final Map<String, Object> userParams = new HashMap<>();

		private final Map<String, Object> systemParams = new HashMap<>();

		private final List<Advisor> advisors = new ArrayList<>();

		private final Map<String, Object> advisorParams = new HashMap<>();

		private final Map<String, Object> toolContext = new HashMap<>();

		private TemplateRenderer templateRenderer;

		@Nullable
		private String userText;

		@Nullable
		private String systemText;

		@Nullable
		private ChatOptions chatOptions;

		/* copy constructor */
		DefaultChatClientRequestSpec(DefaultChatClientRequestSpec ccr) {
			this(ccr.chatModel, ccr.userText, ccr.userParams, ccr.systemText, ccr.systemParams, ccr.toolCallbacks,
					ccr.messages, ccr.toolNames, ccr.media, ccr.chatOptions, ccr.advisors, ccr.advisorParams,
					ccr.observationRegistry, ccr.observationConvention, ccr.toolContext, ccr.templateRenderer);
		}

		public DefaultChatClientRequestSpec(ChatModel chatModel, @Nullable String userText,
				Map<String, Object> userParams, @Nullable String systemText, Map<String, Object> systemParams,
				List<ToolCallback> toolCallbacks, List<Message> messages, List<String> toolNames, List<Media> media,
				@Nullable ChatOptions chatOptions, List<Advisor> advisors, Map<String, Object> advisorParams,
				ObservationRegistry observationRegistry,
				@Nullable ChatClientObservationConvention observationConvention, Map<String, Object> toolContext,
				@Nullable TemplateRenderer templateRenderer) {

			Assert.notNull(chatModel, "chatModel cannot be null");
			Assert.notNull(userParams, "userParams cannot be null");
			Assert.notNull(systemParams, "systemParams cannot be null");
			Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
			Assert.notNull(messages, "messages cannot be null");
			Assert.notNull(toolNames, "toolNames cannot be null");
			Assert.notNull(media, "media cannot be null");
			Assert.notNull(advisors, "advisors cannot be null");
			Assert.notNull(advisorParams, "advisorParams cannot be null");
			Assert.notNull(observationRegistry, "observationRegistry cannot be null");
			Assert.notNull(toolContext, "toolContext cannot be null");

			this.chatModel = chatModel;
			this.chatOptions = chatOptions != null ? chatOptions.copy()
					: (chatModel.getDefaultOptions() != null) ? chatModel.getDefaultOptions().copy() : null;

			this.userText = userText;
			this.userParams.putAll(userParams);
			this.systemText = systemText;
			this.systemParams.putAll(systemParams);

			this.toolNames.addAll(toolNames);
			this.toolCallbacks.addAll(toolCallbacks);
			this.messages.addAll(messages);
			this.media.addAll(media);
			this.advisors.addAll(advisors);
			this.advisorParams.putAll(advisorParams);
			this.observationRegistry = observationRegistry;
			this.observationConvention = observationConvention != null ? observationConvention
					: DEFAULT_CHAT_CLIENT_OBSERVATION_CONVENTION;
			this.toolContext.putAll(toolContext);
			this.templateRenderer = templateRenderer != null ? templateRenderer : DEFAULT_TEMPLATE_RENDERER;
		}

		@Nullable
		public String getUserText() {
			return this.userText;
		}

		public Map<String, Object> getUserParams() {
			return this.userParams;
		}

		@Nullable
		public String getSystemText() {
			return this.systemText;
		}

		public Map<String, Object> getSystemParams() {
			return this.systemParams;
		}

		@Nullable
		public ChatOptions getChatOptions() {
			return this.chatOptions;
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

		public Map<String, Object> getToolContext() {
			return this.toolContext;
		}

		public TemplateRenderer getTemplateRenderer() {
			return this.templateRenderer;
		}

		/**
		 * Return a {@code ChatClient2Builder} to create a new {@code ChatClient2} whose
		 * settings are replicated from this {@code ChatClientRequest}.
		 */
		public Builder mutate() {
			DefaultChatClientBuilder builder = (DefaultChatClientBuilder) ChatClient
				.builder(this.chatModel, this.observationRegistry, this.observationConvention)
				.defaultTemplateRenderer(this.templateRenderer)
				.defaultToolCallbacks(this.toolCallbacks)
				.defaultToolContext(this.toolContext)
				.defaultToolNames(StringUtils.toStringArray(this.toolNames));

			if (StringUtils.hasText(this.userText)) {
				builder.defaultUser(
						u -> u.text(this.userText).params(this.userParams).media(this.media.toArray(new Media[0])));
			}

			if (StringUtils.hasText(this.systemText)) {
				builder.defaultSystem(s -> s.text(this.systemText).params(this.systemParams));
			}

			if (this.chatOptions != null) {
				builder.defaultOptions(this.chatOptions);
			}

			builder.addMessages(this.messages);

			return builder;
		}

		public ChatClientRequestSpec advisors(Consumer<ChatClient.AdvisorSpec> consumer) {
			Assert.notNull(consumer, "consumer cannot be null");
			var advisorSpec = new DefaultAdvisorSpec();
			consumer.accept(advisorSpec);
			this.advisorParams.putAll(advisorSpec.getParams());
			this.advisors.addAll(advisorSpec.getAdvisors());
			return this;
		}

		public ChatClientRequestSpec advisors(Advisor... advisors) {
			Assert.notNull(advisors, "advisors cannot be null");
			Assert.noNullElements(advisors, "advisors cannot contain null elements");
			this.advisors.addAll(Arrays.asList(advisors));
			return this;
		}

		public ChatClientRequestSpec advisors(List<Advisor> advisors) {
			Assert.notNull(advisors, "advisors cannot be null");
			Assert.noNullElements(advisors, "advisors cannot contain null elements");
			this.advisors.addAll(advisors);
			return this;
		}

		public ChatClientRequestSpec messages(Message... messages) {
			Assert.notNull(messages, "messages cannot be null");
			Assert.noNullElements(messages, "messages cannot contain null elements");
			this.messages.addAll(List.of(messages));
			return this;
		}

		public ChatClientRequestSpec messages(List<Message> messages) {
			Assert.notNull(messages, "messages cannot be null");
			Assert.noNullElements(messages, "messages cannot contain null elements");
			this.messages.addAll(messages);
			return this;
		}

		public <T extends ChatOptions> ChatClientRequestSpec options(T options) {
			Assert.notNull(options, "options cannot be null");
			this.chatOptions = options;
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
			Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
			Assert.noNullElements(toolCallbacks, "toolCallbacks cannot contain null elements");
			this.toolCallbacks.addAll(List.of(toolCallbacks));
			return this;
		}

		@Override
		public ChatClientRequestSpec toolCallbacks(List<ToolCallback> toolCallbacks) {
			Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
			Assert.noNullElements(toolCallbacks, "toolCallbacks cannot contain null elements");
			this.toolCallbacks.addAll(toolCallbacks);
			return this;
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
			Assert.notNull(toolCallbackProviders, "toolCallbackProviders cannot be null");
			Assert.noNullElements(toolCallbackProviders, "toolCallbackProviders cannot contain null elements");
			for (ToolCallbackProvider toolCallbackProvider : toolCallbackProviders) {
				this.toolCallbacks.addAll(List.of(toolCallbackProvider.getToolCallbacks()));
			}
			return this;
		}

		public ChatClientRequestSpec toolContext(Map<String, Object> toolContext) {
			Assert.notNull(toolContext, "toolContext cannot be null");
			Assert.noNullElements(toolContext.keySet(), "toolContext keys cannot contain null elements");
			Assert.noNullElements(toolContext.values(), "toolContext values cannot contain null elements");
			this.toolContext.putAll(toolContext);
			return this;
		}

		public ChatClientRequestSpec system(String text) {
			Assert.hasText(text, "text cannot be null or empty");
			this.systemText = text;
			return this;
		}

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

		public ChatClientRequestSpec system(Resource text) {
			Assert.notNull(text, "text cannot be null");
			return this.system(text, Charset.defaultCharset());
		}

		public ChatClientRequestSpec system(Consumer<PromptSystemSpec> consumer) {
			Assert.notNull(consumer, "consumer cannot be null");

			var systemSpec = new DefaultPromptSystemSpec();
			consumer.accept(systemSpec);
			this.systemText = StringUtils.hasText(systemSpec.text()) ? systemSpec.text() : this.systemText;
			this.systemParams.putAll(systemSpec.params());

			return this;
		}

		public ChatClientRequestSpec user(String text) {
			Assert.hasText(text, "text cannot be null or empty");
			this.userText = text;
			return this;
		}

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

		public ChatClientRequestSpec user(Resource text) {
			Assert.notNull(text, "text cannot be null");
			return this.user(text, Charset.defaultCharset());
		}

		public ChatClientRequestSpec user(Consumer<PromptUserSpec> consumer) {
			Assert.notNull(consumer, "consumer cannot be null");

			var us = new DefaultPromptUserSpec();
			consumer.accept(us);
			this.userText = StringUtils.hasText(us.text()) ? us.text() : this.userText;
			this.userParams.putAll(us.params());
			this.media.addAll(us.media());
			return this;
		}

		public ChatClientRequestSpec templateRenderer(TemplateRenderer templateRenderer) {
			Assert.notNull(templateRenderer, "templateRenderer cannot be null");
			this.templateRenderer = templateRenderer;
			return this;
		}

		public CallResponseSpec call() {
			BaseAdvisorChain advisorChain = buildAdvisorChain();
			return new DefaultCallResponseSpec(DefaultChatClientUtils.toChatClientRequest(this), advisorChain,
					observationRegistry, observationConvention);
		}

		public StreamResponseSpec stream() {
			BaseAdvisorChain advisorChain = buildAdvisorChain();
			return new DefaultStreamResponseSpec(DefaultChatClientUtils.toChatClientRequest(this), advisorChain,
					observationRegistry, observationConvention);
		}

		private BaseAdvisorChain buildAdvisorChain() {
			// At the stack bottom add the model call advisors.
			// They play the role of the last advisors in the advisor chain.
			this.advisors.add(ChatModelCallAdvisor.builder().chatModel(this.chatModel).build());
			this.advisors.add(ChatModelStreamAdvisor.builder().chatModel(this.chatModel).build());

			return DefaultAroundAdvisorChain.builder(this.observationRegistry)
				.pushAll(this.advisors)
				.templateRenderer(this.templateRenderer)
				.build();
		}

	}

}
