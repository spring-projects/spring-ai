package org.springframework.ai.wenxin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.AbstractFunctionCallSupport;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.wenxin.api.WenxinApi;
import org.springframework.ai.wenxin.metadata.WenxinChatResponseMetadata;
import org.springframework.ai.wenxin.metadata.support.WenxinResponseHeaderExtractor;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @author lvchzh
 * @date 2024年05月14日 下午5:26
 * @description:
 */
public class WenxinChatModel extends
		AbstractFunctionCallSupport<WenxinApi.ChatCompletionMessage, WenxinApi.ChatCompletionRequest, ResponseEntity<WenxinApi.ChatCompletion>>
		implements ChatModel, StreamingChatModel {

	// @formatter:off
	private static final Logger logger = LoggerFactory.getLogger(WenxinChatModel.class);
	private final RetryTemplate retryTemplate;
	private final WenxinApi wenxinApi;
	private WenxinChatOptions defaultOptions;

	public WenxinChatModel(WenxinApi wenxinApi) {
		this(wenxinApi,
				WenxinChatOptions.builder().withModel(WenxinApi.DEFAULT_CHAT_MODEL).withTemperature(0.7f).build());
	}

	public WenxinChatModel(WenxinApi wenxinApi, WenxinChatOptions options) {
		this(wenxinApi, options, null, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public WenxinChatModel(WenxinApi wenxinApi, WenxinChatOptions options,
			FunctionCallbackContext functionCallbackContext, RetryTemplate retryTemplate) {
		super(functionCallbackContext);
		Assert.notNull(wenxinApi, "WenxinApi must not be null");
		Assert.notNull(options, "WenxinChatOptions must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");
		this.wenxinApi = wenxinApi;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
	}

	@Override
	public ChatResponse call(Prompt prompt) {

		WenxinApi.ChatCompletionRequest request = createRequest(prompt, false);

		return this.retryTemplate.execute(ctx -> {

			ResponseEntity<WenxinApi.ChatCompletion> completionEntity = this.callWithFunctionSupport(request);

			var chatCompletion = completionEntity.getBody();

			if (chatCompletion == null) {
				logger.warn("No chat completion returned for prompt: {}", prompt);
				return new ChatResponse(List.of());
			}

			RateLimit rateLimits = WenxinResponseHeaderExtractor.extractAiResponseHeaders(completionEntity);

			Generation generation = new Generation(chatCompletion.result(), toMap(chatCompletion.id(),
					chatCompletion));

			List<Generation> generations = List.of(generation);

			return new ChatResponse(generations,
					WenxinChatResponseMetadata.from(chatCompletion).withRateLimit(rateLimits));
		});
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return WenxinChatOptions.fromOptions(this.defaultOptions);
	}

	private Map<String, Object> toMap(String id, WenxinApi.ChatCompletion chatCompletion) {
		Map<String, Object> map = new HashMap<>();
		if (chatCompletion.finishReason() != null) {
			map.put("finishReason", chatCompletion.finishReason().name());
		}
		map.put("id", id);
		return map;
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		WenxinApi.ChatCompletionRequest request = createRequest(prompt, true);

		return this.retryTemplate.execute(ctx -> {

			Flux<WenxinApi.ChatCompletionChunk> completionChunks = this.wenxinApi.chatCompletionStream(request);

			return completionChunks.map(chunk -> chunkToChatCompletion(chunk)).map(chatCompletion -> {
				try {
					chatCompletion = handleFunctionCallOrReturn(request,
							ResponseEntity.of(Optional.of(chatCompletion))).getBody();

					@SuppressWarnings("null")
					String id = chatCompletion.id();
					String finish = chatCompletion.finishReason() != null ? chatCompletion.finishReason().name() :
							null;

					var generation = new Generation(chatCompletion.result(), Map.of("id", id, "finishReason", finish));
					if (chatCompletion.finishReason() != null) {
						generation = generation.withGenerationMetadata(
								ChatGenerationMetadata.from(chatCompletion.finishReason().name(), null));
					}
					List<Generation> generations = List.of(generation);

					return new ChatResponse(generations);
				} catch (Exception e) {
					logger.error("Error processing chat completion", e);
					return new ChatResponse(List.of());
				}
			});
		});
	}

	private WenxinApi.ChatCompletion chunkToChatCompletion(WenxinApi.ChatCompletionChunk chunk) {
		return new WenxinApi.ChatCompletion(chunk.id(), "chat.completion", chunk.created(), chunk.sentenceId(),
				chunk.isEnd(), chunk.isTruncated(), chunk.finishReason(), chunk.searchInfo(), chunk.result(),
				chunk.needClearHistory(), chunk.flag(), chunk.banRound(), null, chunk.functionCall());
	}

	WenxinApi.ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {

		Set<String> functionsForThisRequest = new HashSet<>();

		List<WenxinApi.ChatCompletionMessage> chatCompletionMessages = prompt.getInstructions().stream()
				.map(m -> new WenxinApi.ChatCompletionMessage(m.getContent(),
						WenxinApi.Role.valueOf(m.getMessageType().name()))).toList();
		WenxinApi.ChatCompletionRequest request = new WenxinApi.ChatCompletionRequest(chatCompletionMessages, stream);

		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ChatOptions runtimeOptions) {
				WenxinChatOptions updateRuntimeOptions = ModelOptionsUtils.copyToTarget(runtimeOptions,
						ChatOptions.class, WenxinChatOptions.class);

				Set<String> promptEnabledFunctions = this.handleFunctionCallbackConfigurations(updateRuntimeOptions,
						IS_RUNTIME_CALL);

				functionsForThisRequest.addAll(promptEnabledFunctions);

				request = ModelOptionsUtils.merge(updateRuntimeOptions, request,
						WenxinApi.ChatCompletionRequest.class);
			} else {
				throw new IllegalArgumentException("Prompt options are not of type ChatOptions: " +
						prompt.getOptions().getClass().getSimpleName());
			}
		}

		if (this.defaultOptions != null) {

			Set<String> defaultEnableFunctions = this.handleFunctionCallbackConfigurations(this.defaultOptions,
					!IS_RUNTIME_CALL);

			functionsForThisRequest.addAll(defaultEnableFunctions);

			request = ModelOptionsUtils.merge(this.defaultOptions, request, WenxinApi.ChatCompletionRequest.class);
		}

		if (!CollectionUtils.isEmpty(functionsForThisRequest)) {
			request = ModelOptionsUtils.merge(
					WenxinChatOptions.builder().withTools(this.getFunctionTools(functionsForThisRequest)).build(),
					request, WenxinApi.ChatCompletionRequest.class);
		}

		return request;
	}

	private List<WenxinApi.FunctionTool> getFunctionTools(Set<String> functionNames) {
		return this.resolveFunctionCallbacks(functionNames).stream().map(functioncallback -> {
			var function = new WenxinApi.FunctionTool(functioncallback.getName(), functioncallback.getDescription(),
					functioncallback.getInputTypeSchema(), null);
			return function;
		}).toList();
	}

	@Override
	protected WenxinApi.ChatCompletionRequest doCreateToolResponseRequest(
			WenxinApi.ChatCompletionRequest previousRequest, WenxinApi.ChatCompletionMessage responseMessage,
			List<WenxinApi.ChatCompletionMessage> conversationHistory) {

		var functionName = responseMessage.functionCall().name();
		String functionArguments = responseMessage.functionCall().arguments();
		if (!this.functionCallbackRegister.containsKey(functionName)) {
			throw new IllegalStateException("Function callback not found for function name: " + functionName);
		}

		String functionResponse = this.functionCallbackRegister.get(functionName).call(functionArguments);

		conversationHistory.add(
				new WenxinApi.ChatCompletionMessage(functionResponse, WenxinApi.Role.FUNCTION, functionName, null));

		WenxinApi.ChatCompletionRequest newRequest = new WenxinApi.ChatCompletionRequest(conversationHistory, false);
		newRequest = ModelOptionsUtils.merge(newRequest, previousRequest, WenxinApi.ChatCompletionRequest.class);
		return newRequest;
	}

	@Override
	protected List<WenxinApi.ChatCompletionMessage> doGetUserMessages(WenxinApi.ChatCompletionRequest request) {
		return request.messages();
	}

	@Override
	protected WenxinApi.ChatCompletionMessage doGetToolResponseMessage(
			ResponseEntity<WenxinApi.ChatCompletion> chatCompletion) {
		return new WenxinApi.ChatCompletionMessage(chatCompletion.getBody().result(), WenxinApi.Role.ASSISTANT, null,
				chatCompletion.getBody().functionCall());
	}

	@Override
	protected ResponseEntity<WenxinApi.ChatCompletion> doChatCompletion(WenxinApi.ChatCompletionRequest request) {
		return this.wenxinApi.chatCompletionEntity(request);
	}

	@Override
	protected Flux<ResponseEntity<WenxinApi.ChatCompletion>> doChatCompletionStream(
			WenxinApi.ChatCompletionRequest request) {
		return this.wenxinApi.chatCompletionStream(request)
				.map(this::chunkToChatCompletion)
				.map(Optional::ofNullable)
				.map(ResponseEntity::of);
	}

	@Override
	protected boolean isToolFunctionCall(ResponseEntity<WenxinApi.ChatCompletion> chatCompletion) {
		var body = chatCompletion.getBody();
		if (body == null) {
			return false;
		}
		return body.finishReason() == WenxinApi.ChatCompletionFinishReason.FUNCTION_CALL;
	}
	// @formatter:on

}
