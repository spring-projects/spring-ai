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
//@formatter:off
package org.springframework.ai.bedrock.api;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.ai.bedrock.BedrockConverseChatGenerationMetadata;
import org.springframework.ai.bedrock.BedrockChatResponseMetadata;
import org.springframework.ai.bedrock.api.BedrockConverseApi.BedrockConverseRequest;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptions;
import org.springframework.util.Assert;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetadataEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ImageBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ImageSource;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock.Type;

/**
 * Amazon Bedrock Converse API utils.
 *
 * @author Wei Jiang
 * @since 1.0.0
 */
public class BedrockConverseApiUtils {
	private static final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * Convert {@link Prompt} to {@link ConverseRequest} with model id and options. It
	 * will merge default options and runtime options to converse inference parameters.
	 *
	 * @param modelId The Amazon Bedrock Model Id.
	 * @param prompt The prompt that needs to convert.
	 * @param defaultOptions The default options needs to convert.
	 * @return Amazon Bedrock Converse encapsulates request.
	 */
	public static BedrockConverseRequest createBedrockConverseRequest(String modelId, Prompt prompt,
			ChatOptions defaultOptions) {
		Assert.notNull(modelId, "'modelId' must not be null.");
		Assert.notNull(prompt, "'prompt' must not be null.");

		List<Message> messages = getInstructionsMessages(prompt.getInstructions());

		List<SystemContentBlock> systemMessages = getPromptSystemContentBlocks(prompt);

		Document additionalModelRequestFields = getChatOptionsAdditionalModelRequestFields(defaultOptions,
				prompt.getOptions());

		return new BedrockConverseRequest(modelId, messages, systemMessages, additionalModelRequestFields);
	}

	/**
	 * Convert {@link Prompt} to {@link ConverseRequest} with model id and options. It
	 * will merge default options and runtime options to converse inference parameters.
	 * https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html#API_runtime_Converse_RequestSyntax
	 *
	 * @param modelId The Amazon Bedrock Model Id.
	 * @param prompt The prompt that needs to convert.
	 * @return Amazon Bedrock Converse request.
	 */
	public static ConverseRequest createConverseRequest(String modelId, Prompt prompt) {
		return createConverseRequest(modelId, prompt, null);
	}

	/**
	 * Convert {@link Prompt} to {@link ConverseRequest} with model id and options. It
	 * will merge default options and runtime options to converse inference parameters.
	 * https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html#API_runtime_Converse_RequestSyntax
	 *
	 * @param modelId The Amazon Bedrock Model Id.
	 * @param prompt The prompt that needs to convert.
	 * @param defaultOptions The default options needs to convert.
	 * @return Amazon Bedrock Converse request.
	 */
	public static ConverseRequest createConverseRequest(String modelId, Prompt prompt, ChatOptions defaultOptions) {
		BedrockConverseRequest bedrockConverseRequest = createBedrockConverseRequest(modelId, prompt, defaultOptions);

		return createConverseRequest(bedrockConverseRequest);
	}

	/**
	 * Convert {@link Prompt} to {@link ConverseRequest} with model id and options. It
	 * will merge default options and runtime options to converse inference parameters.
	 * https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html#API_runtime_Converse_RequestSyntax
	 *
	 * @param bedrockConverseRequest The Amazon Bedrock Converse encapsulates request.
	 * @return Amazon Bedrock Converse request.
	 */
	public static ConverseRequest createConverseRequest(BedrockConverseRequest bedrockConverseRequest) {
		Assert.notNull(bedrockConverseRequest, "'bedrockConverseRequest' must not be null.");

		return ConverseRequest.builder()
			.modelId(bedrockConverseRequest.modelId())
			.messages(bedrockConverseRequest.messages())
			.system(bedrockConverseRequest.systemMessages())
			.additionalModelRequestFields(bedrockConverseRequest.additionalModelRequestFields())
			.toolConfig(bedrockConverseRequest.toolConfiguration())
			.build();
	}

	/**
	 * Convert {@link Prompt} to {@link ConverseStreamRequest} with model id and options.
	 * It will merge default options and runtime options to converse inference parameters.
	 * https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ConverseStream.html
	 *
	 * @param modelId The Amazon Bedrock Model Id.
	 * @param prompt The prompt that needs to convert.
	 * @param defaultOptions The default options needs to convert.
	 * @return Amazon Bedrock Converse stream request.
	 */
	public static ConverseStreamRequest createConverseStreamRequest(String modelId, Prompt prompt) {
		return createConverseStreamRequest(modelId, prompt, null);
	}

	/**
	 * Convert {@link Prompt} to {@link ConverseStreamRequest} with model id and options.
	 * It will merge default options and runtime options to converse inference parameters.
	 * https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ConverseStream.html
	 *
	 * @param modelId The Amazon Bedrock Model Id.
	 * @param prompt The prompt that needs to convert.
	 * @param defaultOptions The default options needs to convert.
	 * @return Amazon Bedrock Converse stream request.
	 */
	public static ConverseStreamRequest createConverseStreamRequest(String modelId, Prompt prompt,
			ChatOptions defaultOptions) {
		BedrockConverseRequest bedrockConverseRequest = createBedrockConverseRequest(modelId, prompt, defaultOptions);

		return createConverseStreamRequest(bedrockConverseRequest);
	}

	/**
	 * Convert {@link Prompt} to {@link ConverseStreamRequest} with model id and options.
	 * It will merge default options and runtime options to converse inference parameters.
	 * https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ConverseStream.html
	 *
	 * @param bedrockConverseRequest The Amazon Bedrock Converse encapsulates request.
	 * @return Amazon Bedrock Converse stream request.
	 */
	public static ConverseStreamRequest createConverseStreamRequest(BedrockConverseRequest bedrockConverseRequest) {
		Assert.notNull(bedrockConverseRequest, "'bedrockConverseRequest' must not be null.");

		return ConverseStreamRequest.builder()
			.modelId(bedrockConverseRequest.modelId())
			.messages(bedrockConverseRequest.messages())
			.system(bedrockConverseRequest.systemMessages())
			.additionalModelRequestFields(bedrockConverseRequest.additionalModelRequestFields())
			.toolConfig(bedrockConverseRequest.toolConfiguration())
			.build();
	}

	/**
	 * Convert {@link ConverseResponse} to {@link ChatResponse} includes model output,
	 * stopReason, usage, metrics etc.
	 * https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html#API_runtime_Converse_ResponseSyntax
	 *
	 * @param response The Bedrock Converse response.
	 * @return The ChatResponse entity.
	 */
	public static ChatResponse convertConverseResponse(ConverseResponse response) {
		Assert.notNull(response, "'response' must not be null.");

		Message message = response.output().message();

		String text = getConverseResponseTextContent(message.content());

		Generation generation = new Generation(text)
			.withGenerationMetadata(BedrockConverseChatGenerationMetadata.from(response, message));

		return new ChatResponse(List.of(generation), BedrockChatResponseMetadata.from(response));
	}

	/**
	 * Convert {@link ConverseStreamOutput} to {@link ChatResponse} includes model output,
	 * stopReason, usage, metrics etc.
	 * https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html#API_runtime_Converse_ResponseSyntax
	 *
	 * @param output The Bedrock Converse stream output.
	 * @return The ChatResponse entity.
	 */
	public static ChatResponse convertConverseStreamOutput(ConverseStreamOutput output) {
		if (output instanceof ContentBlockDeltaEvent contentBlockDeltaEvent) {
			Generation generation = new Generation(contentBlockDeltaEvent.delta().text())
					.withGenerationMetadata(BedrockConverseChatGenerationMetadata.from(contentBlockDeltaEvent));

			return new ChatResponse(List.of(generation));
		} else if (output instanceof MessageStopEvent messageStopEvent) {
			var metadata = BedrockConverseChatGenerationMetadata.from(messageStopEvent);

			return new ChatResponse(List.of(new Generation("").withGenerationMetadata(metadata)));
		} else if (output instanceof ConverseStreamMetadataEvent converseStreamMetadataEvent) {
			return new ChatResponse(List.of(), BedrockChatResponseMetadata.from(converseStreamMetadataEvent));
		} else {
			Generation generation = new Generation("")
					.withGenerationMetadata(BedrockConverseChatGenerationMetadata.from(output));

			return new ChatResponse(List.of(generation));
		}
	}

	private static String getConverseResponseTextContent(List<ContentBlock> contents) {
		Optional<ContentBlock> optional = contents.stream().filter(content -> content.type() == Type.TEXT).findFirst();

		return optional.isPresent() ? optional.get().text() : "";
	}

	private static List<SystemContentBlock> getPromptSystemContentBlocks(Prompt prompt) {
		return prompt.getInstructions()
			.stream()
			.filter(message -> message.getMessageType() == MessageType.SYSTEM)
			.map(instruction -> SystemContentBlock.builder().text(instruction.getContent()).build())
			.toList();
	}

	public static List<Message> getInstructionsMessages(
			List<org.springframework.ai.chat.messages.Message> instructions) {
		return instructions.stream()
			.filter(message -> message.getMessageType() == MessageType.USER
					|| message.getMessageType() == MessageType.ASSISTANT)
			.map(instruction -> createMessage(getInstructionContents(instruction),
					instruction.getMessageType() == MessageType.USER ? ConversationRole.USER
							: ConversationRole.ASSISTANT))
			.toList();
	}

	public static Message createMessage(List<ContentBlock> contentBlocks, ConversationRole role) {
		return Message.builder().content(contentBlocks).role(role).build();
	}

	private static List<ContentBlock> getInstructionContents(org.springframework.ai.chat.messages.Message instruction) {
		List<ContentBlock> contents = new ArrayList<>();

		ContentBlock textContentBlock = ContentBlock.builder().text(instruction.getContent()).build();

		contents.add(textContentBlock);

		List<ContentBlock> mediaContentBlocks = instruction.getMedia()
			.stream()
			.map(media -> ContentBlock.builder()
				.image(ImageBlock.builder()
					.format(media.getMimeType().getSubtype())
					.source(ImageSource.fromBytes(SdkBytes.fromByteArray(getContentMediaData(media.getData()))))
					.build())
				.build())
			.toList();

		contents.addAll(mediaContentBlocks);

		return contents;
	}

	private static byte[] getContentMediaData(Object mediaData) {
		if (mediaData instanceof byte[] bytes) {
			return bytes;
		} else if (mediaData instanceof String text) {
			return text.getBytes();
		} else {
			throw new IllegalArgumentException("Unsupported media data type: " + mediaData.getClass().getSimpleName());
		}
	}

	@SuppressWarnings("unchecked")
	private static Document getChatOptionsAdditionalModelRequestFields(ChatOptions defaultOptions,
			ModelOptions promptOptions) {
		if (defaultOptions == null && promptOptions == null) {
			return null;
		}

		Map<String, Object> attributes = new HashMap<>();

		if (defaultOptions != null) {
			Map<String, Object> options = objectMapper.convertValue(defaultOptions, Map.class);

			attributes.putAll(options);
		}

		if (promptOptions != null) {
			if (promptOptions instanceof ChatOptions runtimeOptions) {
				Map<String, Object> options = objectMapper.convertValue(runtimeOptions, Map.class);

				attributes.putAll(options);
			} else {
				throw new IllegalArgumentException(
						"Prompt options are not of type ChatOptions:" + promptOptions.getClass().getSimpleName());
			}
		}

		return convertObjectToDocument(attributes);
	}

	@SuppressWarnings("unchecked")
	public static Document convertObjectToDocument(Object value) {
		if (value == null) {
			return Document.fromNull();
		} else if (value instanceof String stringValue) {
			return Document.fromString(stringValue);
		} else if (value instanceof Boolean booleanValue) {
			return Document.fromBoolean(booleanValue);
		} else if (value instanceof Integer integerValue) {
			return Document.fromNumber(integerValue);
		} else if (value instanceof Long longValue) {
			return Document.fromNumber(longValue);
		} else if (value instanceof Float floatValue) {
			return Document.fromNumber(floatValue);
		} else if (value instanceof Double doubleValue) {
			return Document.fromNumber(doubleValue);
		} else if (value instanceof BigDecimal bigDecimalValue) {
			return Document.fromNumber(bigDecimalValue);
		} else if (value instanceof BigInteger bigIntegerValue) {
			return Document.fromNumber(bigIntegerValue);
		} else if (value instanceof List listValue) {
			return Document.fromList(listValue.stream().map(v -> convertObjectToDocument(v)).toList());
		} else if (value instanceof Map mapValue) {
			return convertMapToDocument(mapValue);
		} else {
			throw new IllegalArgumentException("Unsupported value type:" + value.getClass().getSimpleName());
		}
	}

	private static Document convertMapToDocument(Map<String, Object> value) {
		Map<String, Document> attr = value.entrySet()
			.stream()
			.collect(Collectors.toMap(e -> e.getKey(), e -> convertObjectToDocument(e.getValue())));

		return Document.fromMap(attr);
	}

}
//@formatter:on