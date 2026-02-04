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

package org.springframework.ai.oci.cohere;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.oracle.bmc.generativeaiinference.GenerativeAiInference;
import com.oracle.bmc.generativeaiinference.model.BaseChatRequest;
import com.oracle.bmc.generativeaiinference.model.BaseChatResponse;
import com.oracle.bmc.generativeaiinference.model.ChatDetails;
import com.oracle.bmc.generativeaiinference.model.CohereChatBotMessage;
import com.oracle.bmc.generativeaiinference.model.CohereChatRequest;
import com.oracle.bmc.generativeaiinference.model.CohereChatResponse;
import com.oracle.bmc.generativeaiinference.model.CohereMessage;
import com.oracle.bmc.generativeaiinference.model.CohereSystemMessage;
import com.oracle.bmc.generativeaiinference.model.CohereToolCall;
import com.oracle.bmc.generativeaiinference.model.CohereToolMessage;
import com.oracle.bmc.generativeaiinference.model.CohereToolResult;
import com.oracle.bmc.generativeaiinference.model.CohereUserMessage;
import com.oracle.bmc.generativeaiinference.model.ServingMode;
import com.oracle.bmc.generativeaiinference.requests.ChatRequest;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.oci.ServingModeHelper;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@link ChatModel} implementation that uses the OCI GenAI Chat API.
 *
 * @author Anders Swanson
 * @author Alexandros Pappas
 * @since 1.0.0
 */
public class OCICohereChatModel implements ChatModel {

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	/**
	 * The {@link GenerativeAiInference} client used to interact with OCI GenAI service.
	 */
	private final GenerativeAiInference genAi;

	/**
	 * The configuration information for a chat completions request.
	 */
	private final OCICohereChatOptions defaultOptions;

	private final ObservationRegistry observationRegistry;

	/**
	 * Conventions to use for generating observations.
	 */
	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public OCICohereChatModel(GenerativeAiInference genAi, OCICohereChatOptions options) {
		this(genAi, options, null);
	}

	public OCICohereChatModel(GenerativeAiInference genAi, OCICohereChatOptions options,
			ObservationRegistry observationRegistry) {
		Assert.notNull(genAi, "com.oracle.bmc.generativeaiinference.GenerativeAiInference must not be null");
		Assert.notNull(options, "OCIChatOptions must not be null");

		this.genAi = genAi;
		this.defaultOptions = options;
		this.observationRegistry = observationRegistry;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		Prompt requestPrompt = this.buildRequestPrompt(prompt);
		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(requestPrompt)
			.provider(AiProvider.OCI_GENAI.value())
			.build();

		return ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				ChatResponse chatResponse = doChatRequest(prompt);
				observationContext.setResponse(chatResponse);
				return chatResponse;
			});
	}

	Prompt buildRequestPrompt(Prompt prompt) {
		// Process runtime options
		OCICohereChatOptions runtimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
				OCICohereChatOptions.class);

		// Define request options by merging runtime options and default options
		OCICohereChatOptions requestOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions,
				OCICohereChatOptions.class);

		return new Prompt(prompt.getInstructions(), requestOptions);
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return OCICohereChatOptions.fromOptions(this.defaultOptions);
	}

	/**
	 * Use the provided convention for reporting observation data
	 * @param observationConvention The provided convention
	 */
	public void setObservationConvention(ChatModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	private ChatResponse doChatRequest(Prompt prompt) {
		OCICohereChatOptions options = mergeOptions(prompt.getOptions(), this.defaultOptions);
		validateChatOptions(options);

		ChatResponseMetadata metadata = ChatResponseMetadata.builder()
			.model(options.getModel())
			.keyValue("compartment", options.getCompartment())
			.build();
		return new ChatResponse(getGenerations(prompt, options), metadata);

	}

	private OCICohereChatOptions mergeOptions(ChatOptions chatOptions, OCICohereChatOptions defaultOptions) {
		if (chatOptions instanceof OCICohereChatOptions override) {
			OCICohereChatOptions dynamicOptions = ModelOptionsUtils.merge(override, defaultOptions,
					OCICohereChatOptions.class);

			if (dynamicOptions != null) {
				return dynamicOptions;
			}
		}
		return defaultOptions;
	}

	private void validateChatOptions(OCICohereChatOptions options) {
		if (!StringUtils.hasText(options.getModel())) {
			throw new IllegalArgumentException("Model is not set!");
		}
		if (!StringUtils.hasText(options.getCompartment())) {
			throw new IllegalArgumentException("Compartment is not set!");
		}
		if (!StringUtils.hasText(options.getServingMode())) {
			throw new IllegalArgumentException("ServingMode is not set!");
		}
	}

	private List<Generation> getGenerations(Prompt prompt, OCICohereChatOptions options) {
		com.oracle.bmc.generativeaiinference.responses.ChatResponse cr = this.genAi
			.chat(toCohereChatRequest(prompt, options));
		return toGenerations(cr, options);

	}

	private List<Generation> toGenerations(com.oracle.bmc.generativeaiinference.responses.ChatResponse ociChatResponse,
			OCICohereChatOptions options) {
		BaseChatResponse cr = ociChatResponse.getChatResult().getChatResponse();
		if (cr instanceof CohereChatResponse resp) {
			List<Generation> generations = new ArrayList<>();
			ChatGenerationMetadata metadata = ChatGenerationMetadata.builder()
				.finishReason(resp.getFinishReason().getValue())
				.build();
			AssistantMessage message = AssistantMessage.builder().content(resp.getText()).properties(Map.of()).build();
			generations.add(new Generation(message, metadata));
			return generations;
		}
		throw new IllegalStateException(String.format("Unexpected chat response type: %s", cr.getClass().getName()));
	}

	private ChatRequest toCohereChatRequest(Prompt prompt, OCICohereChatOptions options) {
		List<Message> messages = prompt.getInstructions();
		Message message = messages.get(0);
		List<CohereMessage> chatHistory = getCohereMessages(messages);
		return newChatRequest(options, message, chatHistory);
	}

	private List<CohereMessage> getCohereMessages(List<Message> messages) {
		List<CohereMessage> chatHistory = new ArrayList<>();
		for (int i = 1; i < messages.size(); i++) {
			Message message = messages.get(i);
			switch (message.getMessageType()) {
				case USER -> chatHistory.add(CohereUserMessage.builder().message(message.getText()).build());
				case ASSISTANT -> chatHistory.add(CohereChatBotMessage.builder().message(message.getText()).build());
				case SYSTEM -> chatHistory.add(CohereSystemMessage.builder().message(message.getText()).build());
				case TOOL -> {
					if (message instanceof ToolResponseMessage tm) {
						chatHistory.add(toToolMessage(tm));
					}
				}
			}
		}
		return chatHistory;
	}

	private CohereToolMessage toToolMessage(ToolResponseMessage tm) {
		List<CohereToolResult> results = tm.getResponses().stream().map(r -> {
			CohereToolCall call = CohereToolCall.builder().name(r.name()).build();
			return CohereToolResult.builder().call(call).outputs(List.of(r.responseData())).build();
		}).toList();
		return CohereToolMessage.builder().toolResults(results).build();
	}

	private ChatRequest newChatRequest(OCICohereChatOptions options, Message message, List<CohereMessage> chatHistory) {
		BaseChatRequest baseChatRequest = CohereChatRequest.builder()
			.frequencyPenalty(options.getFrequencyPenalty())
			.presencePenalty(options.getPresencePenalty())
			.maxTokens(options.getMaxTokens())
			.topK(options.getTopK())
			.topP(options.getTopP())
			.temperature(options.getTemperature())
			.preambleOverride(options.getPreambleOverride())
			.stopSequences(options.getStopSequences())
			.documents(options.getDocuments())
			.tools(options.getTools())
			.chatHistory(chatHistory)
			.message(message.getText())
			.build();
		ServingMode servingMode = ServingModeHelper.get(options.getServingMode(), options.getModel());
		ChatDetails chatDetails = ChatDetails.builder()
			.compartmentId(options.getCompartment())
			.servingMode(servingMode)
			.chatRequest(baseChatRequest)
			.build();
		return ChatRequest.builder().body$(chatDetails).build();
	}

}
