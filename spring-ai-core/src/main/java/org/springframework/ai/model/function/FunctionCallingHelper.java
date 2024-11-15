/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.model.function;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.AbstractToolCallSupport;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallingOptionsBuilder.PortableFunctionCallingOptions;
import org.springframework.util.CollectionUtils;

/**
 * Helper class that reuses the {@link AbstractToolCallSupport} to implement the function
 * call handling logic on the client side. Used when the withProxyToolCalls(true) option
 * is enabled.
 */
public class FunctionCallingHelper extends AbstractToolCallSupport {

	public FunctionCallingHelper() {
		this(null, PortableFunctionCallingOptions.builder().build(), List.of());
	}

	public FunctionCallingHelper(FunctionCallbackContext functionCallbackContext,
			FunctionCallingOptions functionCallingOptions, List<FunctionCallback> toolFunctionCallbacks) {
		super(functionCallbackContext, functionCallingOptions, toolFunctionCallbacks);
	}

	@Override
	public boolean isToolCall(ChatResponse chatResponse, Set<String> toolCallFinishReasons) {
		return super.isToolCall(chatResponse, toolCallFinishReasons);
	}

	@Override
	public List<Message> buildToolCallConversation(List<Message> previousMessages, AssistantMessage assistantMessage,
			ToolResponseMessage toolResponseMessage) {
		return super.buildToolCallConversation(previousMessages, assistantMessage, toolResponseMessage);
	}

	@Override
	public List<Message> handleToolCalls(Prompt prompt, ChatResponse response) {
		return super.handleToolCalls(prompt, response);
	}

	public Flux<ChatResponse> processStream(ChatModel chatModel, Prompt prompt, Set<String> finishReasons,
			Function<AssistantMessage.ToolCall, String> customFunction) {

		Flux<ChatResponse> chatResponses = chatModel.stream(prompt);

		return chatResponses.flatMap(chatResponse -> {

			boolean isToolCall = this.isToolCall(chatResponse, finishReasons);

			if (isToolCall) {

				Optional<Generation> toolCallGeneration = chatResponse.getResults()
					.stream()
					.filter(g -> !CollectionUtils.isEmpty(g.getOutput().getToolCalls()))
					.findFirst();

				AssistantMessage assistantMessage = toolCallGeneration.get().getOutput();

				List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();

				for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {

					String functionResponse = customFunction.apply(toolCall);

					toolResponses.add(new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(),
							ModelOptionsUtils.toJsonString(functionResponse)));
				}

				ToolResponseMessage toolMessageResponse = new ToolResponseMessage(toolResponses, Map.of());

				List<Message> toolCallConversation = this.buildToolCallConversation(prompt.getInstructions(),
						assistantMessage, toolMessageResponse);

				var prompt2 = new Prompt(toolCallConversation, prompt.getOptions());

				return processStream(chatModel, prompt2, finishReasons, customFunction);
			}

			return Flux.just(chatResponse);
		});
	}

	public ChatResponse processCall(ChatModel chatModel, Prompt prompt, Set<String> finishReasons,
			Function<AssistantMessage.ToolCall, String> customFunction) {

		ChatResponse chatResponse = chatModel.call(prompt);

		boolean isToolCall = this.isToolCall(chatResponse, finishReasons);

		if (!isToolCall) {
			return chatResponse;
		}

		Optional<Generation> toolCallGeneration = chatResponse.getResults()
			.stream()
			.filter(g -> !CollectionUtils.isEmpty(g.getOutput().getToolCalls()))
			.findFirst();

		AssistantMessage assistantMessage = toolCallGeneration.get().getOutput();

		List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();

		for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {

			String functionResponse = customFunction.apply(toolCall);

			toolResponses.add(new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(),
					ModelOptionsUtils.toJsonString(functionResponse)));
		}

		ToolResponseMessage toolMessageResponse = new ToolResponseMessage(toolResponses, Map.of());

		List<Message> toolCallConversation = this.buildToolCallConversation(prompt.getInstructions(), assistantMessage,
				toolMessageResponse);

		var prompt2 = new Prompt(toolCallConversation, prompt.getOptions());

		return processCall(chatModel, prompt2, finishReasons, customFunction);
	}

	/**
	 * Helper used to provide only the function definition, without the actual function
	 * call implementation.
	 */
	public static record FunctionDefinition(String name, String description,
			String inputTypeSchema) implements FunctionCallback {

		@Override
		public String getName() {
			return this.name();
		}

		@Override
		public String getDescription() {
			return this.description();
		}

		@Override
		public String getInputTypeSchema() {
			return this.inputTypeSchema();
		}

		@Override
		public String call(String functionInput) {
			throw new UnsupportedOperationException(
					"FunctionDefinition provides only metadata. It doesn't implement the call method.");
		}

	}

}
