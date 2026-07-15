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

package org.springframework.ai.deliverance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.teknek.deliverance.client.spring.model.ChatCompletionMessageToolCall;
import io.teknek.deliverance.client.spring.model.ChatCompletionMessageToolCallFunction;
import io.teknek.deliverance.client.spring.model.ChatCompletionRequestMessage;
import io.teknek.deliverance.client.spring.model.ChatCompletionTool;
import io.teknek.deliverance.client.spring.model.CreateChatCompletionRequest;
import io.teknek.deliverance.client.spring.model.CreateChatCompletionResponse;
import io.teknek.deliverance.client.spring.model.FunctionObject;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deliverance.api.DeliveranceApi;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * {@link ChatModel} implementation backed by the Deliverance chat completion API.
 *
 * @author Edward Capriolo
 * @since 2.0.1
 */
public class DeliveranceChatModel implements ChatModel {

	private final DeliveranceApi deliveranceApi;

	private final ObjectMapper objectMapper;

	private final DeliveranceChatOptions options;

	private final ToolCallingManager toolCallingManager;

	public DeliveranceChatModel(DeliveranceApi deliveranceApi, ObjectMapper objectMapper,
			DeliveranceChatOptions options) {
		this(deliveranceApi, objectMapper, options, ToolCallingManager.builder().build());
	}

	public DeliveranceChatModel(DeliveranceApi deliveranceApi, ObjectMapper objectMapper,
			DeliveranceChatOptions options, ToolCallingManager toolCallingManager) {
		Assert.notNull(deliveranceApi, "deliveranceApi must not be null");
		Assert.notNull(objectMapper, "objectMapper must not be null");
		Assert.notNull(options, "options must not be null");
		Assert.notNull(toolCallingManager, "toolCallingManager must not be null");
		this.deliveranceApi = deliveranceApi;
		this.objectMapper = objectMapper;
		this.options = options;
		this.toolCallingManager = toolCallingManager;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		Prompt requestPrompt = buildRequestPrompt(prompt);
		CreateChatCompletionResponse response = this.deliveranceApi
			.createChatCompletion(toRequest(requestPrompt, false));
		if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
			return new ChatResponse(List.of());
		}
		var message = response.getChoices().get(0).getMessage();
		String content = message.getContent() != null ? message.getContent() : "";
		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content(content)
			.toolCalls(DeliveranceApi.toolCalls(message.getToolCalls()))
			.build();
		return new ChatResponse(List.of(new Generation(assistantMessage)));
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		Prompt requestPrompt = buildRequestPrompt(prompt);
		Flux<ChatResponse> responseFlux = this.deliveranceApi.streamChatCompletion(toRequest(requestPrompt, true));
		return new MessageAggregator().aggregate(responseFlux, aggregated -> {
		});
	}

	@Override
	public DeliveranceChatOptions getOptions() {
		return this.options;
	}

	public CreateChatCompletionRequest toRequest(Prompt prompt, boolean stream) {
		DeliveranceChatOptions options = mergeOptions(prompt.getOptions());
		CreateChatCompletionRequest request = new CreateChatCompletionRequest().model(requireModel(options))
			.stream(stream);
		List<ChatCompletionRequestMessage> messages = new ArrayList<>();
		for (Message message : prompt.getInstructions()) {
			messages.addAll(toMessages(message));
		}
		request.messages(messages);
		if (options.getTemperature() != null) {
			request.temperature(BigDecimal.valueOf(options.getTemperature()));
		}
		if (options.getTopP() != null) {
			request.topP(BigDecimal.valueOf(options.getTopP()));
		}
		if (options.getTopK() != null) {
			request.topK(BigDecimal.valueOf(options.getTopK()));
		}
		if (options.getMaxTokens() != null) {
			request.maxTokens(options.getMaxTokens());
		}
		if (options.getSeed() != null) {
			request.seed(options.getSeed());
		}
		if (options.getLogprobs() != null) {
			request.logprobs(options.getLogprobs());
		}
		if (options.getTopLogprobs() != null) {
			request.topLogprobs(options.getTopLogprobs());
		}
		if (options.getXtcThreshold() != null) {
			request.xtcThreshold(BigDecimal.valueOf(options.getXtcThreshold()));
		}
		if (options.getXtcProbability() != null) {
			request.xtcProbability(BigDecimal.valueOf(options.getXtcProbability()));
		}
		if (options.getGuidedRegex() != null) {
			request.guidedRegex(options.getGuidedRegex());
		}
		if (options.getGuidedJson() != null) {
			try {
				request.guidedJson(
						this.objectMapper.convertValue(this.objectMapper.readTree(options.getGuidedJson()), Map.class));
			}
			catch (Exception ex) {
				throw new IllegalArgumentException("guidedJson must be a valid JSON schema", ex);
			}
		}
		List<ToolDefinition> toolDefinitions = this.toolCallingManager.resolveToolDefinitions(options);
		if (!CollectionUtils.isEmpty(toolDefinitions)) {
			request.tools(toolDefinitions.stream().map(this::toTool).toList());
			request.parallelToolCalls(false);
		}
		return request;
	}

	private ChatCompletionTool toTool(ToolDefinition toolDefinition) {
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> parameters = this.objectMapper.readValue(toolDefinition.inputSchema(), Map.class);
			return new ChatCompletionTool().type("function")
				.function(new FunctionObject().name(toolDefinition.name())
					.description(toolDefinition.description())
					.parameters(parameters));
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Tool input schema must be valid JSON", ex);
		}
	}

	private List<ChatCompletionRequestMessage> toMessages(Message message) {
		if (message instanceof ToolResponseMessage toolResponseMessage) {
			return toolResponseMessage.getResponses()
				.stream()
				.map(response -> new ChatCompletionRequestMessage().role("tool")
					.content(response.responseData())
					.toolCallId(response.id()))
				.toList();
		}
		ChatCompletionRequestMessage requestMessage = new ChatCompletionRequestMessage().role(role(message))
			.content(message.getText());
		if (message instanceof AssistantMessage assistantMessage && !assistantMessage.getToolCalls().isEmpty()) {
			requestMessage.toolCalls(assistantMessage.getToolCalls().stream().map(this::toToolCall).toList());
		}
		return List.of(requestMessage);
	}

	private ChatCompletionMessageToolCall toToolCall(AssistantMessage.ToolCall toolCall) {
		return new ChatCompletionMessageToolCall().id(toolCall.id())
			.type(toolCall.type())
			.function(
					new ChatCompletionMessageToolCallFunction().name(toolCall.name()).arguments(toolCall.arguments()));
	}

	private Prompt buildRequestPrompt(Prompt prompt) {
		if (prompt.getOptions() == null) {
			return prompt.mutate().chatOptions(this.getOptions()).build();
		}
		return prompt;
	}

	private DeliveranceChatOptions mergeOptions(@Nullable ChatOptions promptOptions) {
		if (promptOptions == null) {
			return this.options;
		}
		if (promptOptions instanceof DeliveranceChatOptions deliveranceOptions) {
			return DeliveranceChatOptions.builder()
				.model(deliveranceOptions.getModel() == null ? this.options.getModel() : deliveranceOptions.getModel())
				.temperature(deliveranceOptions.getTemperature() == null ? this.options.getTemperature()
						: deliveranceOptions.getTemperature())
				.maxTokens(deliveranceOptions.getMaxTokens() == null ? this.options.getMaxTokens()
						: deliveranceOptions.getMaxTokens())
				.topP(deliveranceOptions.getTopP() == null ? this.options.getTopP() : deliveranceOptions.getTopP())
				.topK(deliveranceOptions.getTopK() == null ? this.options.getTopK() : deliveranceOptions.getTopK())
				.stopSequences(deliveranceOptions.getStopSequences() == null ? this.options.getStopSequences()
						: deliveranceOptions.getStopSequences())
				.seed(deliveranceOptions.getSeed() == null ? this.options.getSeed() : deliveranceOptions.getSeed())
				.logprobs(deliveranceOptions.getLogprobs() == null ? this.options.getLogprobs()
						: deliveranceOptions.getLogprobs())
				.topLogprobs(deliveranceOptions.getTopLogprobs() == null ? this.options.getTopLogprobs()
						: deliveranceOptions.getTopLogprobs())
				.xtcThreshold(deliveranceOptions.getXtcThreshold() == null ? this.options.getXtcThreshold()
						: deliveranceOptions.getXtcThreshold())
				.xtcProbability(deliveranceOptions.getXtcProbability() == null ? this.options.getXtcProbability()
						: deliveranceOptions.getXtcProbability())
				.guidedRegex(deliveranceOptions.getGuidedRegex() == null ? this.options.getGuidedRegex()
						: deliveranceOptions.getGuidedRegex())
				.guidedJson(deliveranceOptions.getGuidedJson() == null ? this.options.getGuidedJson()
						: deliveranceOptions.getGuidedJson())
				.toolCallbacks(deliveranceOptions.getToolCallbacks() == null ? this.options.getToolCallbacks()
						: deliveranceOptions.getToolCallbacks())
				.toolContext(deliveranceOptions.getToolContext() == null ? this.options.getToolContext()
						: deliveranceOptions.getToolContext())
				.build();
		}
		return DeliveranceChatOptions.builder()
			.model(promptOptions.getModel() == null ? this.options.getModel() : promptOptions.getModel())
			.temperature(promptOptions.getTemperature() == null ? this.options.getTemperature()
					: promptOptions.getTemperature())
			.maxTokens(
					promptOptions.getMaxTokens() == null ? this.options.getMaxTokens() : promptOptions.getMaxTokens())
			.topP(promptOptions.getTopP() == null ? this.options.getTopP() : promptOptions.getTopP())
			.topK(promptOptions.getTopK() == null ? this.options.getTopK() : promptOptions.getTopK())
			.stopSequences(promptOptions.getStopSequences() == null ? this.options.getStopSequences()
					: promptOptions.getStopSequences())
			.build();
	}

	private String requireModel(DeliveranceChatOptions options) {
		if (!StringUtils.hasText(options.getModel())) {
			throw new IllegalArgumentException("Deliverance model must be set");
		}
		return Objects.requireNonNull(options.getModel());
	}

	private String role(Message message) {
		MessageType type = message.getMessageType();
		if (type == MessageType.SYSTEM) {
			return "system";
		}
		if (type == MessageType.ASSISTANT) {
			return "assistant";
		}
		if (type == MessageType.TOOL) {
			return "tool";
		}
		return "user";
	}

}
