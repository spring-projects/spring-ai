package org.springframework.ai.qwen;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.qwen.api.QwenApi;
import org.springframework.ai.qwen.api.QwenModel;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import static org.springframework.ai.qwen.api.QwenApiHelper.getOrDefault;

public class QwenChatModel implements ChatModel {

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private static final ToolCallingManager DEFAULT_TOOL_CALLING_MANAGER = ToolCallingManager.builder().build();

	private final QwenApi qwenApi;

	private final QwenChatOptions defaultOptions;

	private final ObservationRegistry observationRegistry;

	private final ToolCallingManager toolCallingManager;

	private final ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate;

	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public QwenChatModel(QwenApi openAiApi, QwenChatOptions defaultOptions, ToolCallingManager toolCallingManager,
			ObservationRegistry observationRegistry) {
		this(openAiApi, defaultOptions, toolCallingManager, observationRegistry,
				new DefaultToolExecutionEligibilityPredicate());
	}

	public QwenChatModel(QwenApi qwenApi, QwenChatOptions defaultOptions, ToolCallingManager toolCallingManager,
			ObservationRegistry observationRegistry,
			ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {
		Assert.notNull(qwenApi, "qwenApi cannot be null");
		Assert.notNull(defaultOptions, "defaultOptions cannot be null");
		Assert.notNull(observationRegistry, "observationRegistry cannot be null");
		Assert.notNull(toolExecutionEligibilityPredicate, "toolExecutionEligibilityPredicate cannot be null");
		this.qwenApi = qwenApi;
		this.defaultOptions = defaultOptions;
		this.toolCallingManager = getOrDefault(toolCallingManager, DEFAULT_TOOL_CALLING_MANAGER);
		this.observationRegistry = observationRegistry;
		this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return internalCall(requestPrompt, null);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return this.internalStream(requestPrompt, null);
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return QwenChatOptions.fromOptions(this.defaultOptions);
	}

	/**
	 * Use the provided convention for reporting observation data
	 * @param observationConvention The provided convention
	 */
	public void setObservationConvention(ChatModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	private ChatResponse internalCall(Prompt prompt, ChatResponse previousChatResponse) {
		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(prompt)
			.provider(AiProvider.ALIBABA.value())
			.requestOptions(prompt.getOptions())
			.build();

		ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				ChatResponse chatResponse = qwenApi.call(prompt, previousChatResponse);
				observationContext.setResponse(chatResponse);
				return chatResponse;
			});

		if (toolExecutionEligibilityPredicate.isToolExecutionRequired(prompt.getOptions(), response)) {
			var toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, response);
			if (toolExecutionResult.returnDirect()) {
				// return tool execution result directly to the client
				return ChatResponse.builder()
					.from(response)
					.generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
					.build();
			}
			else {
				// send the tool execution result back to the model
				return internalCall(new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()),
						response);
			}
		}

		return response;
	}

	private Flux<ChatResponse> internalStream(Prompt prompt, ChatResponse previousChatResponse) {
		return Flux.deferContextual(contextView -> {
			final ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(prompt)
				.provider(AiProvider.ALIBABA.value())
				.requestOptions(prompt.getOptions())
				.build();

			Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
					this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			// @formatter:off
            Flux<ChatResponse> chatResponse = this.qwenApi.streamCall(prompt, previousChatResponse)
                    .flatMap(response -> {
                        if (this.toolExecutionEligibilityPredicate.isToolExecutionRequired(prompt.getOptions(), response)) {
                            return Flux.defer(() -> {
                                var toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, response);
                                if (toolExecutionResult.returnDirect()) {
                                    // return tool execution result directly to the client
                                    return Flux.just(ChatResponse.builder().from(response)
                                            .generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
                                            .build());
                                } else {
                                    // send the tool execution result back to the model.
                                    return this.internalStream(
                                            new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()),
                                            response);
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
            // @formatter:on

			return new MessageAggregator().aggregate(chatResponse, observationContext::setResponse);
		});
	}

	private Prompt buildRequestPrompt(Prompt prompt) {
		// process runtime options
		QwenChatOptions runtimeOptions = null;
		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
				runtimeOptions = ModelOptionsUtils.copyToTarget(toolCallingChatOptions, ToolCallingChatOptions.class,
						QwenChatOptions.class);
			}
			else if (prompt.getOptions() instanceof FunctionCallingOptions functionCallingOptions) {
				runtimeOptions = ModelOptionsUtils.copyToTarget(functionCallingOptions, FunctionCallingOptions.class,
						QwenChatOptions.class);
			}
			else {
				runtimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
						QwenChatOptions.class);
			}
		}

		QwenChatOptions requestOptions = QwenChatOptions.fromOptions(this.defaultOptions).overrideWith(runtimeOptions);

		ToolCallingChatOptions.validateToolCallbacks(requestOptions.getToolCallbacks());

		return new Prompt(prompt.getInstructions(), requestOptions);
	}

	public static final class Builder {

		private QwenApi qwenApi;

		private QwenChatOptions defaultOptions = QwenChatOptions.builder().model(QwenModel.QWEN_MAX.getName()).build();

		private ToolCallingManager toolCallingManager;

		private ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate = new DefaultToolExecutionEligibilityPredicate();

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private Builder() {
		}

		public Builder qwenApi(QwenApi qwenApi) {
			this.qwenApi = qwenApi;
			return this;
		}

		public Builder defaultOptions(QwenChatOptions defaultOptions) {
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

		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		public QwenChatModel build() {
			return new QwenChatModel(this.qwenApi, this.defaultOptions, this.toolCallingManager,
					this.observationRegistry, this.toolExecutionEligibilityPredicate);
		}

	}

}
