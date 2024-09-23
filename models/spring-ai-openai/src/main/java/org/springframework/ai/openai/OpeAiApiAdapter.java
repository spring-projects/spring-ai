package org.springframework.ai.openai;

import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.Media;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.openai.OpenAiChatOptions.Builder;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletion;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletion.Choice;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk.ChunkChoice;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionFinishReason;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.MediaContent;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import org.springframework.ai.openai.api.OpenAiApi.FunctionTool;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

public class OpeAiApiAdapter {

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

	/**
	 * Converts the OpenAI
	 * <a href="https://platform.openai.com/docs/api-reference/chat/create">Chat
	 * completion request</a> into Spring AI {@link Prompt} with
	 * {@link OpenAiChatOptions}.
	 * @param chatCompletionRequest the OpenAI Chat Completion Request to convert.
	 * @return the converted Spring AI Prompt.
	 */
	public static Prompt toPrompt(ChatCompletionRequest chatCompletionRequest) {

		// 1. Convert the Options
		var chatOptionsBuilder = toChatOptions(chatCompletionRequest);

		// 2. Covert the Spring AI messages into OpenAi messages.
		List<ChatCompletionMessage> apiMessages = chatCompletionRequest.messages();

		List<Message> messages = apiMessages.stream().map(apiMessage -> {

			if (apiMessage.role() == ChatCompletionMessage.Role.USER
					|| apiMessage.role() == ChatCompletionMessage.Role.SYSTEM) {

				Object rawContent = apiMessage.rawContent();
				String refusal = apiMessage.refusal();
				String name = apiMessage.name();

				MessageType messageType = MessageType.valueOf(apiMessage.role().name());

				Map<String, Object> metadata = Map.of();
				// Map<String, Object> metadata = Map.of("refusal", refusal, "name",
				// name);

				if (rawContent instanceof String textContent) {
					return new UserMessage(messageType, textContent, List.of(), metadata);
				}
				else if (rawContent instanceof OpenAiApi.ChatCompletionMessage.MediaContent mediaContent) {
					try {
						var media = new Media(MimeTypeUtils.IMAGE_JPEG, new URL(mediaContent.imageUrl().url()));
						return new UserMessage(messageType, mediaContent.text(), List.of(media), metadata);
					}
					catch (Exception e) {
						throw new IllegalArgumentException(
								"Unsupported message content type: " + rawContent.getClass());
					}
				}
				else {
					throw new IllegalArgumentException("Unsupported message content type: " + rawContent.getClass());
				}

			}
			else if (apiMessage.role() == ChatCompletionMessage.Role.ASSISTANT) {

				List<AssistantMessage.ToolCall> toolCalls = null;
				if (!CollectionUtils.isEmpty(apiMessage.toolCalls())) {
					toolCalls = apiMessage.toolCalls().stream().map(toolCall -> {
						return new AssistantMessage.ToolCall(toolCall.id(), toolCall.type(), toolCall.function().name(),
								toolCall.function().arguments());
					}).toList();
				}
				return new AssistantMessage(apiMessage.content(), Map.of(), toolCalls);
			}
			else if (apiMessage.role() == ChatCompletionMessage.Role.TOOL) {
				String functionName = apiMessage.name();
				String callId = apiMessage.toolCallId();
				List<ToolResponse> toolResponses = List
					.of(new ToolResponseMessage.ToolResponse(callId, functionName, "" + apiMessage.rawContent()));
				return new ToolResponseMessage(toolResponses, Map.of());
			}
			else {
				throw new IllegalArgumentException("Unsupported message type: " + apiMessage.role());
			}
		}).map(abstractMessage -> (Message) abstractMessage).toList();

		return new Prompt(messages, chatOptionsBuilder.build());
	}

	public static OpenAiChatOptions.Builder toChatOptions(ChatCompletionRequest chatCompletionRequest) {

		// 1. Convert the Options
		Builder optionsBuilder = OpenAiChatOptions.builder();

		List<FunctionTool> tools = chatCompletionRequest.tools();

		if (!CollectionUtils.isEmpty(tools)) {
			List<FunctionCallback> tooDefinitions = tools.stream().map(tool -> {
				return new FunctionDefinition(tool.function().name(), tool.function().description(),
						ModelOptionsUtils.toJsonString(tool.function().parameters()));
			}).map(fd -> (FunctionCallback) fd).toList();

			optionsBuilder.withFunctionCallbacks(tooDefinitions);
		}

		if (chatCompletionRequest.model() != null) {
			optionsBuilder.withModel(chatCompletionRequest.model());
		}
		if (chatCompletionRequest.frequencyPenalty() != null) {
			optionsBuilder.withFrequencyPenalty(chatCompletionRequest.frequencyPenalty());
		}
		if (chatCompletionRequest.logitBias() != null) {
			optionsBuilder.withLogitBias(chatCompletionRequest.logitBias());
		}
		if (chatCompletionRequest.logprobs() != null) {
			optionsBuilder.withLogprobs(chatCompletionRequest.logprobs());
		}
		if (chatCompletionRequest.topLogprobs() != null) {
			optionsBuilder.withTopLogprobs(chatCompletionRequest.topLogprobs());
		}
		if (chatCompletionRequest.maxTokens() != null) {
			optionsBuilder.withMaxTokens(chatCompletionRequest.maxTokens());
		}
		if (chatCompletionRequest.n() != null) {
			optionsBuilder.withN(chatCompletionRequest.n());
		}
		if (chatCompletionRequest.presencePenalty() != null) {
			optionsBuilder.withPresencePenalty(chatCompletionRequest.presencePenalty());
		}
		if (chatCompletionRequest.responseFormat() != null) {
			optionsBuilder.withResponseFormat(chatCompletionRequest.responseFormat());
		}
		if (chatCompletionRequest.seed() != null) {
			optionsBuilder.withSeed(chatCompletionRequest.seed());
		}
		if (chatCompletionRequest.stop() != null) {
			optionsBuilder.withStop(chatCompletionRequest.stop());
		}
		if (chatCompletionRequest.stream() != null) {
			// ???
		}
		if (chatCompletionRequest.temperature() != null) {
			optionsBuilder.withTemperature(chatCompletionRequest.temperature());
		}
		if (chatCompletionRequest.topP() != null) {
			optionsBuilder.withTopP(chatCompletionRequest.topP());
		}
		if (chatCompletionRequest.toolChoice() != null) {
			optionsBuilder.withToolChoice("" + chatCompletionRequest.toolChoice());
		}
		if (chatCompletionRequest.parallelToolCalls() != null) {
			optionsBuilder.withParallelToolCalls(chatCompletionRequest.parallelToolCalls());
		}
		if (chatCompletionRequest.user() != null) {
			optionsBuilder.withUser(chatCompletionRequest.user());
		}

		return optionsBuilder;
	}

	/**
	 * Converts the Spring AI {@link ChatResponse} into OpenAI {@link ChatCompletion}.
	 * @param chatResponse the Spring AI Chat Response to convert.
	 * @return the converted OpenAI Chat Completion.
	 */
	public static ChatCompletion toChatCompletion(ChatResponse chatResponse) {

		List<Choice> choices = new ArrayList<>(chatResponse.getResults().size());

		int index = 0;

		for (Generation generation : chatResponse.getResults()) {
			var openAiMessage = toOpenAiMessage(generation.getOutput());
			var finishReason = ChatCompletionFinishReason.valueOf(generation.getMetadata().getFinishReason());
			choices.add(new Choice(finishReason, index, openAiMessage, null));
			index++;
		}

		String id = chatResponse.getMetadata().getId();
		String model = chatResponse.getMetadata().getModel();
		Usage springAiUsage = chatResponse.getMetadata().getUsage();
		OpenAiApi.Usage usage = new OpenAiApi.Usage(springAiUsage.getGenerationTokens().intValue(),
				springAiUsage.getPromptTokens().intValue(), springAiUsage.getTotalTokens().intValue());

		return new ChatCompletion(id, choices, new Date().getTime(), model, null, "chat.completion", usage);
	}

	public static ChatCompletionChunk toChatCompletionChunk(ChatResponse chatResponse) {

		List<ChunkChoice> choices = new ArrayList<>(chatResponse.getResults().size());

		int index = 0;

		for (Generation generation : chatResponse.getResults()) {
			var openAiMessage = toOpenAiMessage(generation.getOutput());
			ChatCompletionFinishReason finishReason = (!StringUtils.hasText(generation.getMetadata().getFinishReason()))
					? null : ChatCompletionFinishReason.valueOf(generation.getMetadata().getFinishReason());
			choices.add(new ChunkChoice(finishReason, index, openAiMessage, null));
			index++;
		}

		String id = chatResponse.getMetadata().getId();
		String model = chatResponse.getMetadata().getModel();
		Usage springAiUsage = chatResponse.getMetadata().getUsage();
		OpenAiApi.Usage usage = new OpenAiApi.Usage(springAiUsage.getGenerationTokens().intValue(),
				springAiUsage.getPromptTokens().intValue(), springAiUsage.getTotalTokens().intValue());

		return new ChatCompletionChunk(id, choices, new Date().getTime(), model, null, "chat.completion.chunk", usage);
	}

	private static ChatCompletionMessage toOpenAiMessage(Message message) {

		if (message.getMessageType() == MessageType.USER || message.getMessageType() == MessageType.SYSTEM) {
			Object content = message.getContent();
			if (message instanceof UserMessage userMessage) {
				if (!CollectionUtils.isEmpty(userMessage.getMedia())) {
					List<MediaContent> contentList = new ArrayList<>(List.of(new MediaContent(message.getContent())));

					contentList.addAll(userMessage.getMedia()
						.stream()
						.map(media -> new MediaContent(
								new MediaContent.ImageUrl(fromMediaData(media.getMimeType(), media.getData()))))
						.toList());

					content = contentList;
				}
			}

			return new ChatCompletionMessage(content,
					ChatCompletionMessage.Role.valueOf(message.getMessageType().name()));
		}
		else if (message.getMessageType() == MessageType.ASSISTANT) {
			var assistantMessage = (AssistantMessage) message;
			List<ToolCall> toolCalls = null;
			if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
				toolCalls = new ArrayList<>();
				for (int toolCallIndex = 0; toolCallIndex < assistantMessage.getToolCalls().size(); toolCallIndex++) {
					var toolCall = assistantMessage.getToolCalls().get(toolCallIndex);
					var function = new ChatCompletionFunction(toolCall.name(), toolCall.arguments());
					toolCalls.add(new ToolCall(toolCallIndex, toolCall.id(), toolCall.type(), function));
				}
			}
			return new ChatCompletionMessage(assistantMessage.getContent(), ChatCompletionMessage.Role.ASSISTANT, null,
					null, toolCalls, null);
		}
		else {
			throw new IllegalArgumentException("Unsupported message type: " + message.getMessageType());
		}
	}

	private static String fromMediaData(MimeType mimeType, Object mediaContentData) {
		if (mediaContentData instanceof byte[] bytes) {
			// Assume the bytes are an image. So, convert the bytes to a base64
			// encoded following the prefix pattern.
			return String.format("data:%s;base64,%s", mimeType.toString(), Base64.getEncoder().encodeToString(bytes));
		}
		else if (mediaContentData instanceof String text) {
			// Assume the text is a URLs or a base64 encoded image prefixed by the
			// user.
			return text;
		}
		else {
			throw new IllegalArgumentException(
					"Unsupported media data type: " + mediaContentData.getClass().getSimpleName());
		}
	}

	public static List<ChatCompletionMessage> toOpenAiMessages(List<Message> messages) {
		List<ChatCompletionMessage> chatCompletionMessages = messages.stream().map(message -> {
			if (message.getMessageType() == MessageType.USER || message.getMessageType() == MessageType.SYSTEM) {
				Object content = message.getContent();
				if (message instanceof UserMessage userMessage) {
					if (!CollectionUtils.isEmpty(userMessage.getMedia())) {
						List<MediaContent> contentList = new ArrayList<>(
								List.of(new MediaContent(message.getContent())));

						contentList.addAll(userMessage.getMedia()
							.stream()
							.map(media -> new MediaContent(
									new MediaContent.ImageUrl(fromMediaData(media.getMimeType(), media.getData()))))
							.toList());

						content = contentList;
					}
				}

				return List.of(new ChatCompletionMessage(content,
						ChatCompletionMessage.Role.valueOf(message.getMessageType().name())));
			}
			else if (message.getMessageType() == MessageType.ASSISTANT) {
				var assistantMessage = (AssistantMessage) message;
				List<ToolCall> toolCalls = null;
				if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
					toolCalls = assistantMessage.getToolCalls().stream().map(toolCall -> {
						var function = new ChatCompletionFunction(toolCall.name(), toolCall.arguments());
						return new ToolCall(toolCall.id(), toolCall.type(), function);
					}).toList();
				}
				return List.of(new ChatCompletionMessage(assistantMessage.getContent(),
						ChatCompletionMessage.Role.ASSISTANT, null, null, toolCalls, null));
			}
			else if (message.getMessageType() == MessageType.TOOL) {
				ToolResponseMessage toolMessage = (ToolResponseMessage) message;

				toolMessage.getResponses().forEach(response -> {
					Assert.isTrue(response.id() != null, "ToolResponseMessage must have an id");
					Assert.isTrue(response.name() != null, "ToolResponseMessage must have a name");
				});

				return toolMessage.getResponses()
					.stream()
					.map(tr -> new ChatCompletionMessage(tr.responseData(), ChatCompletionMessage.Role.TOOL, tr.name(),
							tr.id(), null, null))
					.toList();
			}
			else {
				throw new IllegalArgumentException("Unsupported message type: " + message.getMessageType());
			}
		}).flatMap(List::stream).toList();

		return chatCompletionMessages;
	}

	public static void main(String[] args) {
		String request1 = """
				{
				    "messages": [
				        {
				            "content": "What's the weather like in San Francisco, Tokyo, and Paris?",
				            "role": "user"
				        }
				    ],
				    "model": "gpt-4o-mini",
				    "stream": false,
				    "tools": [
				        {
				            "type": "function",
				            "function": {
				                "description": "Get the weather in location",
				                "name": "getCurrentWeather",
				                "parameters": {
				                    "type": "object",
				                    "properties": {
				                        "location": {
				                            "type": "string",
				                            "description": "The city and state e.g. San Francisco, CA"
				                        },
				                        "lat": {
				                            "type": "number",
				                            "description": "The city latitude"
				                        },
				                        "lon": {
				                            "type": "number",
				                            "description": "The city longitude"
				                        },
				                        "unit": {
				                            "type": "string",
				                            "enum": [
				                                "C",
				                                "F"
				                            ]
				                        }
				                    },
				                    "required": [
				                        "location",
				                        "lat",
				                        "lon",
				                        "unit"
				                    ]
				                }
				            }
				        }
				    ]
				}
				""";

		String request2 = """
				{
				    "messages": [
				        {
				            "content": "What's the weather like in San Francisco, Tokyo, and Paris?",
				            "role": "user"
				        },
				        {
				            "role": "assistant",
				            "tool_calls": [
				                {
				                    "id": "call_rzR55tsCemPcEXcyvXtt9v5H",
				                    "type": "function",
				                    "function": {
				                        "name": "getCurrentWeather",
				                        "arguments": "{\\"location\\": \\"San Francisco, CA\\", \\"lat\\": 37.7749, \\"lon\\": -122.4194, \\"unit\\": \\"C\\"}"
				                    }
				                },
				                {
				                    "id": "call_ZOEyq4knGZxFn9eLYBncHzuE",
				                    "type": "function",
				                    "function": {
				                        "name": "getCurrentWeather",
				                        "arguments": "{\\"location\\": \\"Tokyo, Japan\\", \\"lat\\": 35.682839, \\"lon\\": 139.759455, \\"unit\\": \\"C\\"}"
				                    }
				                },
				                {
				                    "id": "call_tZwspDn3nxkl4yodtAvlfeLt",
				                    "type": "function",
				                    "function": {
				                        "name": "getCurrentWeather",
				                        "arguments": "{\\"location\\": \\"Paris, France\\", \\"lat\\": 48.8566, \\"lon\\": 2.3522, \\"unit\\": \\"C\\"}"
				                    }
				                }
				            ]
				        },
				        {
				            "content": "{\\"temp\\":30.0,\\"feels_like\\":15.0,\\"temp_min\\":20.0,\\"temp_max\\":2.0,\\"pressure\\":53,\\"humidity\\":45,\\"unit\\":\\"C\\"}",
				            "role": "tool",
				            "name": "getCurrentWeather",
				            "tool_call_id": "call_rzR55tsCemPcEXcyvXtt9v5H"
				        },
				        {
				            "content": "{\\"temp\\":10.0,\\"feels_like\\":15.0,\\"temp_min\\":20.0,\\"temp_max\\":2.0,\\"pressure\\":53,\\"humidity\\":45,\\"unit\\":\\"C\\"}",
				            "role": "tool",
				            "name": "getCurrentWeather",
				            "tool_call_id": "call_ZOEyq4knGZxFn9eLYBncHzuE"
				        },
				        {
				            "content": "{\\"temp\\":15.0,\\"feels_like\\":15.0,\\"temp_min\\":20.0,\\"temp_max\\":2.0,\\"pressure\\":53,\\"humidity\\":45,\\"unit\\":\\"C\\"}",
				            "role": "tool",
				            "name": "getCurrentWeather",
				            "tool_call_id": "call_tZwspDn3nxkl4yodtAvlfeLt"
				        }
				    ],
				    "model": "gpt-4o-mini",
				    "stream": false,
				    "tools": [
				        {
				            "type": "function",
				            "function": {
				                "description": "Get the weather in location",
				                "name": "getCurrentWeather",
				                "parameters": {
				                    "type": "object",
				                    "properties": {
				                        "location": {
				                            "type": "string",
				                            "description": "The city and state e.g. San Francisco, CA"
				                        },
				                        "lat": {
				                            "type": "number",
				                            "description": "The city latitude"
				                        },
				                        "lon": {
				                            "type": "number",
				                            "description": "The city longitude"
				                        },
				                        "unit": {
				                            "type": "string",
				                            "enum": [
				                                "C",
				                                "F"
				                            ]
				                        }
				                    },
				                    "required": [
				                        "location",
				                        "lat",
				                        "lon",
				                        "unit"
				                    ]
				                }
				            }
				        }
				    ]
				}
				""";

		ChatCompletionRequest chatCompletionRequest1 = ModelOptionsUtils.jsonToObject(request1,
				ChatCompletionRequest.class);

		ChatCompletionRequest chatCompletionRequest2 = ModelOptionsUtils.jsonToObject(request2,
				ChatCompletionRequest.class);

		System.out.println(chatCompletionRequest2);
	}

}