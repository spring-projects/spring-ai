/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.vertex.generation;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.generativeai.preview.GenerativeModel;
import com.google.cloud.vertexai.generativeai.preview.ResponseHandler;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.metadata.ChoiceMetadata;
import org.springframework.ai.metadata.Usage;
import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.prompt.messages.Message;
import org.springframework.ai.prompt.messages.MessageType;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * {@link ChatClient} implementation for {@literal Google Gemini} backed by
 * {@link VertexAI}.
 *
 * @author Jingzhou Ou
 * @see ChatClient
 * @see com.google.cloud.vertexai.VertexAI
 */
public class VertexAiGeminiClient implements ChatClient {

	public final RetryTemplate retryTemplate = RetryTemplate.builder()
		.maxAttempts(3)
		.retryOn(RuntimeException.class)
		.exponentialBackoff(Duration.ofMillis(2000), 5, Duration.ofMillis(3 * 60000))
		.build();

	private String modelName = "gemini-pro";

	private final GenerationConfig generationConfig;

	private final VertexAI vertexAI;

	public VertexAiGeminiClient(VertexAI vertexAI, GenerationConfig generationConfig) {
		Assert.notNull(vertexAI, "VertexAI must not be null");
		this.vertexAI = vertexAI;
		if (generationConfig == null) {
			this.generationConfig = GenerationConfig.newBuilder().build();
		}
		else {
			this.generationConfig = generationConfig;
		}
	}

	@Override
	public ChatResponse generate(Prompt prompt) {
		return this.retryTemplate.execute(ctx -> {
			GenerativeModel model = new GenerativeModel(modelName, vertexAI);
			List<Message> messages = prompt.getMessages();
			List<Content> contents = messages2Contents(messages);
			try {
				GenerateContentResponse response = model.generateContent(contents, generationConfig);
				return new ChatResponse(List.of(new Generation(ResponseHandler.getText(response))
					.withChoiceMetadata(ChoiceMetadata.from(ResponseHandler.getFinishReason(response).name(),
							extractUsage(response.getUsageMetadata())))));
			}
			catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		});
	}

	public String getModelName() {
		return modelName;
	}

	public void setModelName(String modelName) {
		this.modelName = modelName;
	}

	private List<Content> messages2Contents(List<Message> messages) {
		return messages.stream()
			.map(message -> Content.newBuilder()
				.setRole(toRole(message))
				.addParts(Part.newBuilder().setText(message.getContent()).build())
				.build())
			.collect(toList());
	}

	/**
	 * The role in a conversation associated with the content.
	 * Specifying a role is required even in singleturn use cases. Acceptable values include the following:
	 * USER: Specifies content that's sent by you.
	 * MODEL: Specifies the model's response.
	 * Notice: System messages are not supported by Gemini
	 */
	private String toRole(Message message) {
		return switch (message.getMessageType()) {
			case USER -> "user";
			case SYSTEM, ASSISTANT -> "model";
			default -> throw new IllegalArgumentException("Unsupported message type: " + message.getMessageType());
		};
	}

	private Usage extractUsage(GenerateContentResponse.UsageMetadata usageMetadata) {
		return new Usage() {
			@Override
			public Long getPromptTokens() {
				return (long) usageMetadata.getPromptTokenCount();
			}

			@Override
			public Long getGenerationTokens() {
				return (long) usageMetadata.getCandidatesTokenCount();
			}
		};
	}

}
