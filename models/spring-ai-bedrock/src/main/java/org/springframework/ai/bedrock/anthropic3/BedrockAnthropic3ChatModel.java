/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.bedrock.anthropic3;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import reactor.core.publisher.Flux;

import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi;
import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi.AnthropicChatRequest;
import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi.AnthropicChatResponse;
import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi.AnthropicChatStreamingResponse.StreamingType;
import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi.ChatCompletionMessage;
import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi.ChatCompletionMessage.Role;
import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi.MediaContent;
import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi.MediaContent.Type;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.AbstractFunctionCallSupport;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.util.CollectionUtils;

/**
 * Java {@link ChatModel} and {@link StreamingChatModel} for the Bedrock Anthropic chat
 * generative.
 *
 * @author Ben Middleton
 * @author Christian Tzolov
 * @author Wei Jiang
 * @since 1.0.0
 */
public class BedrockAnthropic3ChatModel extends
		AbstractFunctionCallSupport<Anthropic3ChatBedrockApi.ChatCompletionMessage, Anthropic3ChatBedrockApi.AnthropicChatRequest, Anthropic3ChatBedrockApi.AnthropicChatResponse>
		implements ChatModel, StreamingChatModel {

	private final Anthropic3ChatBedrockApi anthropicChatApi;

	private final Anthropic3ChatOptions defaultOptions;

	public BedrockAnthropic3ChatModel(Anthropic3ChatBedrockApi chatApi) {
		this(chatApi,
				Anthropic3ChatOptions.builder()
					.withTemperature(0.8f)
					.withMaxTokens(500)
					.withTopK(10)
					.withAnthropicVersion(Anthropic3ChatBedrockApi.DEFAULT_ANTHROPIC_VERSION)
					.build());
	}

	public BedrockAnthropic3ChatModel(Anthropic3ChatBedrockApi chatApi, Anthropic3ChatOptions options) {
		this(chatApi, options, null);
	}

	public BedrockAnthropic3ChatModel(Anthropic3ChatBedrockApi chatApi, Anthropic3ChatOptions options,
			FunctionCallbackContext functionCallbackContext) {
		super(functionCallbackContext);

		this.anthropicChatApi = chatApi;
		this.defaultOptions = options;
	}

	@Override
	public ChatResponse call(Prompt prompt) {

		AnthropicChatRequest request = createRequest(prompt);

		AnthropicChatResponse response = this.callWithFunctionSupport(request);

		return new ChatResponse(List.of(new Generation(response.content().get(0).text())));
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {

		AnthropicChatRequest request = createRequest(prompt);

		Flux<Anthropic3ChatBedrockApi.AnthropicChatStreamingResponse> fluxResponse = this.anthropicChatApi
			.chatCompletionStream(request);

		AtomicReference<Integer> inputTokens = new AtomicReference<>(0);
		return fluxResponse.map(response -> {
			if (response.type() == StreamingType.MESSAGE_START) {
				inputTokens.set(response.message().usage().inputTokens());
			}
			String content = response.type() == StreamingType.CONTENT_BLOCK_DELTA ? response.delta().text() : "";

			var generation = new Generation(content);

			if (response.type() == StreamingType.MESSAGE_DELTA) {
				generation = generation.withGenerationMetadata(ChatGenerationMetadata
					.from(response.delta().stopReason(), new Anthropic3ChatBedrockApi.AnthropicUsage(inputTokens.get(),
							response.usage().outputTokens())));
			}

			return new ChatResponse(List.of(generation));
		});
	}

	/**
	 * Accessible for testing.
	 */
	AnthropicChatRequest createRequest(Prompt prompt) {

		AnthropicChatRequest request = AnthropicChatRequest.builder(toAnthropicMessages(prompt))
			.withSystem(toAnthropicSystemContext(prompt))
			.build();

		Set<String> functionsForThisRequest = new HashSet<>();

		if (this.defaultOptions != null) {
			request = ModelOptionsUtils.merge(request, this.defaultOptions, AnthropicChatRequest.class);

			Set<String> promptEnabledFunctions = this.handleFunctionCallbackConfigurations(this.defaultOptions,
					!IS_RUNTIME_CALL);
			functionsForThisRequest.addAll(promptEnabledFunctions);
		}

		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ChatOptions runtimeOptions) {
				Anthropic3ChatOptions updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(runtimeOptions,
						ChatOptions.class, Anthropic3ChatOptions.class);
				request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, AnthropicChatRequest.class);

				Set<String> defaultEnabledFunctions = this.handleFunctionCallbackConfigurations(updatedRuntimeOptions,
						IS_RUNTIME_CALL);
				functionsForThisRequest.addAll(defaultEnabledFunctions);
			}
			else {
				throw new IllegalArgumentException("Prompt options are not of type ChatOptions: "
						+ prompt.getOptions().getClass().getSimpleName());
			}
		}

		if (!CollectionUtils.isEmpty(functionsForThisRequest)) {
			List<Anthropic3ChatBedrockApi.Tool> tools = getFunctionTools(functionsForThisRequest);

			request = AnthropicChatRequest.from(request).withTools(tools).build();
		}

		return request;
	}

	/**
	 * Extracts system context from prompt.
	 * @param prompt The prompt.
	 * @return The system context.
	 */
	private String toAnthropicSystemContext(Prompt prompt) {

		return prompt.getInstructions()
			.stream()
			.filter(m -> m.getMessageType() == MessageType.SYSTEM)
			.map(Message::getContent)
			.collect(Collectors.joining(System.lineSeparator()));
	}

	/**
	 * Extracts list of messages from prompt.
	 * @param prompt The prompt.
	 * @return The list of {@link ChatCompletionMessage}.
	 */
	private List<ChatCompletionMessage> toAnthropicMessages(Prompt prompt) {

		return prompt.getInstructions()
			.stream()
			.filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
			.map(message -> {
				List<MediaContent> contents = new ArrayList<>(List.of(new MediaContent(message.getContent())));
				if (!CollectionUtils.isEmpty(message.getMedia())) {
					List<MediaContent> mediaContent = message.getMedia()
						.stream()
						.map(media -> new MediaContent(media.getMimeType().toString(),
								this.fromMediaData(media.getData())))
						.toList();
					contents.addAll(mediaContent);
				}
				return new ChatCompletionMessage(contents, Role.valueOf(message.getMessageType().name()));
			})
			.toList();
	}

	private String fromMediaData(Object mediaData) {
		if (mediaData instanceof byte[] bytes) {
			return Base64.getEncoder().encodeToString(bytes);
		}
		else if (mediaData instanceof String text) {
			return text;
		}
		else {
			throw new IllegalArgumentException("Unsupported media data type: " + mediaData.getClass().getSimpleName());
		}
	}

	private List<Anthropic3ChatBedrockApi.Tool> getFunctionTools(Set<String> functionNames) {
		return this.resolveFunctionCallbacks(functionNames).stream().map(functionCallback -> {
			var description = functionCallback.getDescription();
			var name = functionCallback.getName();
			String inputSchema = functionCallback.getInputTypeSchema();
			return new Anthropic3ChatBedrockApi.Tool(name, description, ModelOptionsUtils.jsonToMap(inputSchema));
		}).toList();
	}

	@Override
	protected AnthropicChatRequest doCreateToolResponseRequest(AnthropicChatRequest previousRequest,
			ChatCompletionMessage responseMessage, List<ChatCompletionMessage> conversationHistory) {

		List<MediaContent> toolToUseList = responseMessage.content()
			.stream()
			.filter(c -> c.type() == MediaContent.Type.TOOL_USE)
			.toList();

		List<MediaContent> toolResults = new ArrayList<>();

		for (MediaContent toolToUse : toolToUseList) {

			var functionCallId = toolToUse.id();
			var functionName = toolToUse.name();
			var functionArguments = toolToUse.input();

			if (!this.functionCallbackRegister.containsKey(functionName)) {
				throw new IllegalStateException("No function callback found for function name: " + functionName);
			}

			String functionResponse = this.functionCallbackRegister.get(functionName)
				.call(ModelOptionsUtils.toJsonString(functionArguments));

			toolResults.add(new MediaContent(Type.TOOL_RESULT, functionCallId, functionResponse));
		}

		// Add the function response to the conversation.
		conversationHistory.add(new ChatCompletionMessage(toolResults, Role.USER));

		// Recursively call chatCompletionWithTools until the model doesn't call a
		// functions anymore.
		return AnthropicChatRequest.from(previousRequest).withMessages(conversationHistory).build();
	}

	@Override
	protected List<ChatCompletionMessage> doGetUserMessages(AnthropicChatRequest request) {
		return request.messages();
	}

	@Override
	protected ChatCompletionMessage doGetToolResponseMessage(AnthropicChatResponse response) {
		return new ChatCompletionMessage(response.content(), Role.ASSISTANT);
	}

	@Override
	protected AnthropicChatResponse doChatCompletion(AnthropicChatRequest request) {
		return this.anthropicChatApi.chatCompletion(request);
	}

	@Override
	protected Flux<AnthropicChatResponse> doChatCompletionStream(AnthropicChatRequest request) {
		// https://docs.anthropic.com/en/docs/tool-use
		throw new UnsupportedOperationException(
				"Streaming (stream=true) is not yet supported. We plan to add streaming support in a future beta version.");
	}

	@Override
	protected boolean isToolFunctionCall(AnthropicChatResponse response) {
		if (response == null || CollectionUtils.isEmpty(response.content())) {
			return false;
		}
		return response.content().stream().anyMatch(content -> content.type() == MediaContent.Type.TOOL_USE);
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return Anthropic3ChatOptions.fromOptions(this.defaultOptions);
	}

}
