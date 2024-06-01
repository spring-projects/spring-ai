package org.springframework.ai.dashscope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.dashscope.api.DashscopeApi;
import org.springframework.ai.dashscope.metadata.DashscopeChatResponseMetadata;
import org.springframework.ai.dashscope.record.chat.ChatCompletion;
import org.springframework.ai.dashscope.record.chat.ChatCompletionChoice;
import org.springframework.ai.dashscope.record.chat.ChatCompletionRequestInput;
import org.springframework.ai.dashscope.record.chat.ChatCompletionMessage;
import org.springframework.ai.dashscope.record.chat.ChatCompletionRequest;
import org.springframework.ai.dashscope.record.chat.ChatCompletionRequestParameters;
import org.springframework.ai.dashscope.record.chat.ToolCall;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.AbstractFunctionCallSupport;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author Nottyjay Ji
 */
public class DashscopeChatModel extends
		AbstractFunctionCallSupport<ChatCompletionMessage, ChatCompletionRequest, ResponseEntity<ChatCompletion>>
		implements ChatModel, StreamingChatModel {

	private static final Logger logger = LoggerFactory.getLogger(DashscopeChatModel.class);

	/** Low-level access to the Dashscope API */
	private final DashscopeApi dashscopeApi;

	/** The retry template used to retry the OpenAI API calls. */
	public final RetryTemplate retryTemplate;

	/** The default options used for the chat completion requests. */
	private DashscopeChatOptions defaultOptions;

	public DashscopeChatModel(DashscopeApi dashscopeApi) {
		this(dashscopeApi,
				DashscopeChatOptions.builder()
					.withModel(DashscopeApi.DEFAULT_CHAT_MODEL)
					.withTemperature(0.7f)
					.build());
	}

	public DashscopeChatModel(DashscopeApi dashscopeApi, DashscopeChatOptions options) {
		this(dashscopeApi, options, null, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public DashscopeChatModel(DashscopeApi dashscopeApi, DashscopeChatOptions options,
			FunctionCallbackContext functionCallbackContext, RetryTemplate retryTemplate) {
		super(functionCallbackContext);
		Assert.notNull(dashscopeApi, "DashscopeApi must not be null");
		Assert.notNull(options, "Options must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");

		this.dashscopeApi = dashscopeApi;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		ChatCompletionRequest request = createRequest(prompt, false);
		return this.retryTemplate.execute(ctx -> {
			ResponseEntity<ChatCompletion> completionEntity = this.callWithFunctionSupport(request);

			var chatCompletion = completionEntity.getBody();
			if (chatCompletion == null) {
				logger.warn("No chat completion returned for prompt: {}", prompt);
				return new ChatResponse(List.of());
			}

			List<Generation> generations = chatCompletion.output().choices().stream().map(choice -> {
				return new Generation(choice.message().content(), toMap(chatCompletion.requestId(), choice))
					.withGenerationMetadata(ChatGenerationMetadata.from(choice.finishReason(), null));
			}).toList();

			return new ChatResponse(generations,
					DashscopeChatResponseMetadata.from(chatCompletion.usage(), chatCompletion.requestId()));
		});
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return DashscopeChatOptions.fromOptions(this.defaultOptions);
	}

	private Map<String, Object> toMap(String id, ChatCompletionChoice choice) {
		Map<String, Object> map = new HashMap<>();

		var message = choice.message();
		if (message.role() != null) {
			map.put("role", message.role().name());
		}
		if (choice.finishReason() != null) {
			map.put("finishReason", choice.finishReason());
		}
		map.put("id", id);
		return map;
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		ChatCompletionRequest request = createRequest(prompt, true);
		return this.retryTemplate.execute(ctx -> {
			Flux<ChatCompletion> chatCompletionFlux = this.dashscopeApi.chatCompletionStream(request);
			ConcurrentHashMap<String, String> roleMap = new ConcurrentHashMap<>();
			return chatCompletionFlux.map(chatCompletion -> {
				String id = chatCompletion.requestId();
				List<Generation> generations = chatCompletion.output().choices().stream().map(choice -> {
					if (choice.message().role() != null) {
						roleMap.putIfAbsent(id, choice.message().role().name());
					}
					String finish = (choice.finishReason() != null ? choice.finishReason() : "");
					var generation = new Generation(choice.message().content(),
							Map.of("requestId", id, "role", roleMap.get(id), "finishReason", finish));
					if (choice.finishReason() != null) {
						generation = generation
							.withGenerationMetadata(ChatGenerationMetadata.from(choice.finishReason(), null));
					}
					return generation;
				}).toList();

				return new ChatResponse(generations,
						DashscopeChatResponseMetadata.from(chatCompletion.usage(), chatCompletion.requestId()));
			});
		});
	}

	@Override
	protected ChatCompletionRequest doCreateToolResponseRequest(ChatCompletionRequest previousRequest,
			ChatCompletionMessage responseMessage, List<ChatCompletionMessage> conversationHistory) {
		// Every tool-call item requires a separate function call and a response (TOOL)
		// message.
		for (ToolCall toolCall : responseMessage.toolCalls()) {

			var functionName = toolCall.function().name();
			String functionArguments = toolCall.function().arguments();

			if (!this.functionCallbackRegister.containsKey(functionName)) {
				throw new IllegalStateException("No function callback found for function name: " + functionName);
			}

			String functionResponse = this.functionCallbackRegister.get(functionName).call(functionArguments);

			// Add the function response to the conversation.
			conversationHistory
				.add(new ChatCompletionMessage(ChatCompletionMessage.Role.TOOL, functionResponse, functionName, null));
		}

		// Recursively call chatCompletionWithTools until the model doesn't call a
		// functions anymore.
		ChatCompletionRequest newRequest = new ChatCompletionRequest(
				new ChatCompletionRequestInput(conversationHistory), false);
		newRequest = ModelOptionsUtils.merge(newRequest, previousRequest, ChatCompletionRequest.class);

		return newRequest;
	}

	@Override
	protected List<ChatCompletionMessage> doGetUserMessages(ChatCompletionRequest request) {
		return request.chatCompletionInput().messages();
	}

	@Override
	protected ChatCompletionMessage doGetToolResponseMessage(ResponseEntity<ChatCompletion> chatCompletion) {
		return chatCompletion.getBody().output().choices().iterator().next().message();
	}

	@Override
	protected ResponseEntity<ChatCompletion> doChatCompletion(ChatCompletionRequest request) {
		return this.dashscopeApi.chatCompletionEntity(request);
	}

	@Override
	protected Flux<ResponseEntity<ChatCompletion>> doChatCompletionStream(ChatCompletionRequest request) {
		return null;
	}

	@Override
	protected boolean isToolFunctionCall(ResponseEntity<ChatCompletion> chatCompletion) {
		var body = chatCompletion.getBody();
		if (body == null) {
			return false;
		}

		var choices = body.output().choices();
		if (CollectionUtils.isEmpty(choices)) {
			return false;
		}

		var choice = choices.get(0);
		return !CollectionUtils.isEmpty(choice.message().toolCalls());
	}

	private ChatCompletionRequest createRequest(Prompt prompt, boolean isStream) {
		Set<String> functionsForThisRequest = new HashSet<>();
		String model = this.defaultOptions.getModel();

		// 构造请求中的messages
		List<ChatCompletionMessage> chatCompletionInputsMessages = prompt.getInstructions().stream().map(m -> {
			return new ChatCompletionMessage(ChatCompletionMessage.Role.valueOf(m.getMessageType().name()),
					m.getContent());
		}).collect(Collectors.toList());

		// 构造请求中的parameters
		ChatCompletionRequestParameters chatCompletionRequestParameters = new ChatCompletionRequestParameters(null,
				null, null, isStream, null, null, null, null, null, null, null);
		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof DashscopeChatOptions) {
				model = ((DashscopeChatOptions) prompt.getOptions()).getModel();
			}
			if (prompt.getOptions() instanceof ChatOptions runtimeOptions) {

				DashscopeChatOptions updateRuntimeOptions = ModelOptionsUtils.copyToTarget(runtimeOptions,
						ChatOptions.class, DashscopeChatOptions.class);

				Set<String> promptEnabledFunctions = this.handleFunctionCallbackConfigurations(updateRuntimeOptions,
						IS_RUNTIME_CALL);
				functionsForThisRequest.addAll(promptEnabledFunctions);

				chatCompletionRequestParameters = ModelOptionsUtils.merge(chatCompletionRequestParameters,
						updateRuntimeOptions, ChatCompletionRequestParameters.class);
			}
		}

		if (this.defaultOptions != null) {
			Set<String> defaultEnabledFunctions = this.handleFunctionCallbackConfigurations(this.defaultOptions,
					!IS_RUNTIME_CALL);

			functionsForThisRequest.addAll(defaultEnabledFunctions);

			chatCompletionRequestParameters = ModelOptionsUtils.merge(chatCompletionRequestParameters,
					this.defaultOptions, ChatCompletionRequestParameters.class);
		}

		// Add the enabled functions definitions to the request's tools parameter.
		if (!CollectionUtils.isEmpty(functionsForThisRequest)) {

			chatCompletionRequestParameters = ModelOptionsUtils.merge(
					DashscopeChatOptions.builder().withTools(this.getFunctionTools(functionsForThisRequest)).build(),
					chatCompletionRequestParameters, ChatCompletionRequestParameters.class);
		}

		return new ChatCompletionRequest(model, isStream, new ChatCompletionRequestInput(chatCompletionInputsMessages),
				chatCompletionRequestParameters);
	}

	private List<DashscopeApi.FunctionTool> getFunctionTools(Set<String> functionNames) {
		return this.resolveFunctionCallbacks(functionNames).stream().map(functionCallback -> {
			var function = new DashscopeApi.FunctionTool.Function(functionCallback.getDescription(),
					functionCallback.getName(), functionCallback.getInputTypeSchema());
			return new DashscopeApi.FunctionTool(function);
		}).toList();
	}

}
