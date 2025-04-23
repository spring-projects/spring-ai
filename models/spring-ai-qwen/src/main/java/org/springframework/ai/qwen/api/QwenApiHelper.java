package org.springframework.ai.qwen.api;

import com.alibaba.dashscope.aigc.generation.GenerationOutput;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.generation.GenerationUsage;
import com.alibaba.dashscope.aigc.generation.SearchInfo;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationOutput;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationUsage;
import com.alibaba.dashscope.common.DashScopeResult;
import com.alibaba.dashscope.common.MessageContentBase;
import com.alibaba.dashscope.common.MessageContentImageURL;
import com.alibaba.dashscope.common.MessageContentText;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.tools.FunctionDefinition;
import com.alibaba.dashscope.tools.ToolBase;
import com.alibaba.dashscope.tools.ToolCallBase;
import com.alibaba.dashscope.tools.ToolCallFunction;
import com.alibaba.dashscope.tools.ToolFunction;
import com.alibaba.dashscope.tools.codeinterpretertool.ToolCallCodeInterpreter;
import com.alibaba.dashscope.tools.search.ToolCallQuarkSearch;
import com.alibaba.dashscope.utils.JsonUtils;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.qwen.QwenChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;

import static com.alibaba.dashscope.aigc.conversation.ConversationParam.ResultFormat.MESSAGE;
import static java.util.stream.Collectors.toList;

public class QwenApiHelper {

	private static final Logger log = LoggerFactory.getLogger(QwenApiHelper.class);

	static boolean isMultimodalModelName(String modelName) {
		// rough judgment
		return modelName.contains("-vl-") || modelName.contains("-audio-");
	}

	public static boolean isSupportingIncrementalOutputModelName(String modelName) {
		// rough judgment
		return !(modelName.contains("-vl-") || modelName.contains("-audio-") || modelName.contains("-mt-"));
	}

	static List<com.alibaba.dashscope.common.Message> toQwenMessages(List<Message> messages) {
		return sanitizeMessages(messages).stream().map(QwenApiHelper::toQwenMessage).toList();
	}

	static com.alibaba.dashscope.common.Message toQwenMessage(Message message) {
		return com.alibaba.dashscope.common.Message.builder()
			.role(roleFrom(message))
			.content(contentFrom(message))
			.name(nameFrom(message))
			.toolCallId(toolCallIdFrom(message))
			.toolCalls(toolCallsFrom(message))
			.build();
	}

	private static String roleFrom(Message message) {
		if (message.getMessageType() == MessageType.ASSISTANT) {
			return Role.ASSISTANT.getValue();
		}
		else if (message.getMessageType() == MessageType.SYSTEM) {
			return Role.SYSTEM.getValue();
		}
		else if (message.getMessageType() == MessageType.TOOL) {
			return Role.TOOL.getValue();
		}
		else {
			return Role.USER.getValue();
		}
	}

	private static String nameFrom(Message message) {
		if (message.getMessageType() == MessageType.TOOL) {
			return ((ToolResponseMessage) message).getResponses().get(0).name();
		}
		return null;
	}

	private static String contentFrom(Message message) {
		if (message.getMessageType() == MessageType.TOOL) {
			return ((ToolResponseMessage) message).getResponses().get(0).responseData();
		}
		return message.getText();
	}

	private static String toolCallIdFrom(Message message) {
		if (message.getMessageType() == MessageType.TOOL) {
			return ((ToolResponseMessage) message).getResponses().get(0).id();
		}
		return null;
	}

	private static List<ToolCallBase> toolCallsFrom(Message message) {
		if (message.getMessageType() == MessageType.ASSISTANT && ((AssistantMessage) message).hasToolCalls()) {
			return toToolCalls(((AssistantMessage) message).getToolCalls());
		}
		return null;
	}

	private static List<ToolCallBase> toToolCalls(Collection<AssistantMessage.ToolCall> toolExecutionRequests) {
		return toolExecutionRequests.stream().map(QwenApiHelper::toToolCall).toList();
	}

	private static ToolCallBase toToolCall(AssistantMessage.ToolCall toolExecutionRequest) {
		ToolCallFunction toolCallFunction = new ToolCallFunction();
		toolCallFunction.setId(toolExecutionRequest.id());
		ToolCallFunction.CallFunction callFunction = toolCallFunction.new CallFunction();
		callFunction.setName(toolExecutionRequest.name());
		callFunction.setArguments(toolExecutionRequest.arguments());
		toolCallFunction.setFunction(callFunction);
		return toolCallFunction;
	}

	private static List<ToolBase> toToolFunctions(Collection<ToolCallback> toolSpecifications) {
		if (CollectionUtils.isEmpty(toolSpecifications)) {
			return Collections.emptyList();
		}

		return toolSpecifications.stream().map(QwenApiHelper::toToolFunction).toList();
	}

	private static ToolBase toToolFunction(ToolCallback toolCallback) {
		FunctionDefinition functionDefinition = FunctionDefinition.builder()
			.name(toolCallback.getToolDefinition().name())
			.description(getOrDefault(toolCallback.getToolDefinition().description(), ""))
			.parameters(toParameters(toolCallback))
			.build();
		return ToolFunction.builder().function(functionDefinition).build();
	}

	private static JsonObject toParameters(ToolCallback toolCallback) {
		if (StringUtils.hasText(toolCallback.getToolDefinition().inputSchema())) {
			return JsonUtils.parse(toolCallback.getToolDefinition().inputSchema());
		}
		else {
			return JsonUtils.toJsonObject(Collections.emptyMap());
		}
	}

	static List<MultiModalMessage> toQwenMultiModalMessages(List<Message> messages) {
		return messages.stream().map(QwenApiHelper::toQwenMultiModalMessage).collect(toList());
	}

	private static MultiModalMessage toQwenMultiModalMessage(Message message) {
		return MultiModalMessage.builder().role(roleFrom(message)).content(toMultiModalContents(message)).build();
	}

	private static List<Map<String, Object>> toMultiModalContents(Message message) {
		List<Map<String, Object>> contents = new LinkedList<>();
		if (StringUtils.hasText(message.getText())) {
			contents.add(toMultiModalContent(message.getText()));
		}

		List<Media> media = switch (message.getMessageType()) {
			case USER -> ((UserMessage) message).getMedia();
			case ASSISTANT -> ((AssistantMessage) message).getMedia();
			default -> Collections.emptyList();
		};

		media.stream().map(QwenApiHelper::toMultiModalContent).forEach(contents::add);

		if (message instanceof ToolResponseMessage toolMessage) {
            List<ToolResponseMessage.ToolResponse> toolResponses = toolMessage.getResponses();
			if (!CollectionUtils.isEmpty(toolResponses)) {
				for (ToolResponseMessage.ToolResponse toolResponse : toolResponses) {
					contents.add(Map.of("content", toolResponse.responseData(), "tool_call_id", toolResponse.id()));
				}
			}
		}

		return contents;
	}

	static Map<String, Object> toMultiModalContent(Media media) {
		MimeType mimeType = media.getMimeType();
		return switch (mimeType.getType()) {
			case "image" -> Collections.singletonMap("image", fromMediaData(mimeType, media.getData()));
			case "audio" -> Collections.singletonMap("audio", fromMediaData(mimeType, media.getData()));
			case "video" -> Collections.singletonMap("video", fromMediaData(mimeType, media.getData()));
			case "text" -> Collections.singletonMap("text", media.getData());
			default -> Collections.emptyMap();
		};
	}

	static Map<String, Object> toMultiModalContent(String text) {
		return Collections.singletonMap("text", text);
	}

	private static String fromMediaData(MimeType mimeType, Object mediaContentData) {
		if (mediaContentData instanceof byte[] bytes) {
			// Assume the bytes are an image. So, convert the bytes to a base64 encoded
			// following the prefix pattern.
			return String.format("data:%s;base64,%s", mimeType.toString(), Base64.getEncoder().encodeToString(bytes));
		}
		else if (mediaContentData instanceof String text) {
			// Assume the text is a URLs or a base64 encoded image prefixed by the user.
			return text;
		}
		else {
			throw new IllegalArgumentException(
					"Unsupported media data type: " + mediaContentData.getClass().getSimpleName());
		}
	}

	static List<Message> sanitizeMessages(List<Message> messages) {
		LinkedList<Message> sanitizedMessages = messages.stream()
			.reduce(new LinkedList<>(), messageAccumulator(), messageCombiner());

		// Ensure the last message is a user/tool_execution_result message
		while (!sanitizedMessages.isEmpty() && !isInputMessageType(sanitizedMessages.getLast())) {
			Message removedMessage = sanitizedMessages.removeLast();
			log.warn("The last message should be a user/tool_execution_result message, but found: {}", removedMessage);
		}

		return sanitizedMessages;
	}

	private static BiFunction<LinkedList<Message>, Message, LinkedList<Message>> messageAccumulator() {
		return (acc, message) -> {
			MessageType type = message.getMessageType();
			if (acc.isEmpty()) {
				// Ensure the first message is a system message or a user message.
				if (type == MessageType.SYSTEM || type == MessageType.USER) {
					acc.add(message);
				}
				else {
					log.warn("The first message should be a system message or a user message, but found: {}", message);
				}
				return acc;
			}

			if (type == MessageType.SYSTEM) {
				if (acc.getFirst().getMessageType() == MessageType.SYSTEM) {
					log.warn("Drop existed system message: {}", acc);
					acc.removeFirst();
				}
				acc.addFirst(message);
				return acc;
			}

			MessageType lastType = acc.getLast().getMessageType();
			if (lastType == MessageType.SYSTEM && type != MessageType.USER) {
				log.warn("The first non-system message must be a user message, but found: {}", message);
				return acc;
			}

			if (type == MessageType.USER) {
				while (acc.getLast().getMessageType() != MessageType.SYSTEM && !isNormalAiType(acc.getLast())) {
					Message removedMessage = acc.removeLast();
					log.warn(
							"Tool execution result should follow a tool execution request message. Drop duplicated message: {}",
							removedMessage);
				}
			}
			else if (type == MessageType.TOOL) {
				while (!isToolCallAiType(acc.getLast())) {
					Message removedMessage = acc.removeLast();
					log.warn(
							"Tool execution result should follow a tool execution request message. Drop duplicated message: {}",
							removedMessage);
				}
			}
			else if (type == MessageType.ASSISTANT) {
				while (!isInputMessageType(acc.getLast())) {
					Message removedMessage = acc.removeLast();
					log.warn(
							"AI message should follow a user/tool_execution_result message. Drop duplicated message: {}",
							removedMessage);
				}
			}

			acc.add(message);
			return acc;
		};
	}

	private static BinaryOperator<LinkedList<Message>> messageCombiner() {
		return (acc1, acc2) -> {
			throw new UnsupportedOperationException("Parallel stream not supported");
		};
	}

	private static boolean isInputMessageType(Message message) {
		MessageType type = message.getMessageType();
		return type == MessageType.USER || type == MessageType.TOOL;
	}

	private static boolean isNormalAiType(Message message) {
		return message.getMessageType() == MessageType.ASSISTANT && !((AssistantMessage) message).hasToolCalls();
	}

	private static boolean isToolCallAiType(Message message) {
		return message.getMessageType() == MessageType.ASSISTANT && ((AssistantMessage) message).hasToolCalls();
	}

	static GenerationParam toGenerationParam(String apiKey, Prompt prompt, boolean incrementalOutput,
			Consumer<GenerationParam.GenerationParamBuilder<?, ?>> generationParamCustomizer) {
		QwenChatOptions options = (QwenChatOptions) prompt.getOptions();
		validateGenerationParameters(options);

		GenerationParam.GenerationParamBuilder<?, ?> builder = GenerationParam.builder()
			.apiKey(apiKey)
			.model(options.getModel())
			.topP(options.getTopP())
			.topK(options.getTopK())
			.enableSearch(getOrDefault(options.isEnableSearch(), false))
			.searchOptions(toQwenSearchOptions(options.getSearchOptions()))
			.seed(options.getSeed())
			.repetitionPenalty(frequencyPenaltyToRepetitionPenalty(options.getFrequencyPenalty()))
			.maxTokens(options.getMaxTokens())
			.messages(toQwenMessages(prompt.getInstructions()))
			.responseFormat(options.getResponseFormat())
			.resultFormat(MESSAGE)
			.incrementalOutput(incrementalOutput);

		if (options.getTemperature() != null) {
			builder.temperature(options.getTemperature().floatValue());
		}

		if (options.getStopSequences() != null) {
			builder.stopStrings(options.getStopSequences());
		}

		if (!CollectionUtils.isEmpty(options.getToolCallbacks())) {
			builder.tools(toToolFunctions(options.getToolCallbacks()));
			if (options.getToolChoice() != null) {
				Object toolChoiceObject = options.getToolChoice();
				if (toolChoiceObject instanceof ToolCallback toolCallback) {
					builder.toolChoice(toToolFunction(toolCallback));
				}
				else {
					builder.toolChoice(toolChoiceObject);
				}
			}
		}

		if (options.getTranslationOptions() != null) {
			// no java field is provided yet
			builder.parameter("translation_options", toQwenTranslationOptions(options.getTranslationOptions()));
		}

		if (options.getCustom() != null) {
			// no java field is provided yet
			builder.parameter("custom", options.getCustom());
		}

		if (generationParamCustomizer != null) {
			generationParamCustomizer.accept(builder);
		}

		return builder.build();
	}

	static void validateGenerationParameters(QwenChatOptions options) {
		if (options.getVlHighResolutionImages() != null) {
			throw new UnsupportedOperationException(
					"'vlHighResolutionImages' parameter is not supported by " + options.getModel());
		}
	}

	static MultiModalConversationParam toMultiModalConversationParam(String apiKey, Prompt prompt,
			boolean incrementalOutput,
			Consumer<MultiModalConversationParam.MultiModalConversationParamBuilder<?, ?>> multimodalConversationParamCustomizer) {
		QwenChatOptions options = (QwenChatOptions) prompt.getOptions();
		validateMultimodalConversationParameters(options);

		MultiModalConversationParam.MultiModalConversationParamBuilder<?, ?> builder = MultiModalConversationParam
			.builder()
			.apiKey(apiKey)
			.model(options.getModel())
			.topP(options.getTopP())
			.topK(options.getTopK())
			.enableSearch(getOrDefault(options.isEnableSearch(), false))
			.seed(options.getSeed())
			.maxTokens(options.getMaxTokens())
			.messages(toQwenMultiModalMessages(prompt.getInstructions()))
			.incrementalOutput(incrementalOutput);

		if (options.getTemperature() != null) {
			builder.temperature(options.getTemperature().floatValue());
		}

		if (options.getVlHighResolutionImages() != null) {
			// no java field is provided yet
			builder.parameter("vl_high_resolution_images", options.getVlHighResolutionImages());
		}

		if (options.getCustom() != null) {
			// no java field is provided yet
			builder.parameter("custom", options.getCustom());
		}

		if (multimodalConversationParamCustomizer != null) {
			multimodalConversationParamCustomizer.accept(builder);
		}

		return builder.build();
	}

	static void validateMultimodalConversationParameters(QwenChatOptions options) {
		if (options.getSearchOptions() != null) {
			throw new UnsupportedOperationException(
					"'searchOptions' parameter is not supported by " + options.getModel());
		}

		if (options.getFrequencyPenalty() != null) {
			throw new UnsupportedOperationException(
					"'frequencyPenalty' parameter is not supported by " + options.getModel());
		}

		if (!CollectionUtils.isEmpty(options.getStopSequences())) {
			throw new UnsupportedOperationException(
					"'stopSequences' parameter is not supported by " + options.getModel());
		}

		if (!CollectionUtils.isEmpty(options.getToolCallbacks()) || !CollectionUtils.isEmpty(options.getToolNames())
				|| !CollectionUtils.isEmpty(options.getToolContext()) || options.getToolChoice() != null) {
			throw new UnsupportedOperationException("'tools' parameter is not supported by " + options.getModel());
		}

		if (options.getTranslationOptions() != null) {
			throw new UnsupportedOperationException(
					"'translationOptions' parameter is not supported by " + options.getModel());
		}

		if (options.getResponseFormat() != null) {
			throw new UnsupportedOperationException(
					"'responseFormat' parameter is not supported by " + options.getModel());
		}
	}

	static com.alibaba.dashscope.aigc.generation.SearchOptions toQwenSearchOptions(
			QwenChatOptions.SearchOptions searchOptions) {
		if (searchOptions == null) {
			return null;
		}

		return com.alibaba.dashscope.aigc.generation.SearchOptions.builder()
			.citationFormat(searchOptions.citationFormat())
			.enableCitation(searchOptions.enableCitation())
			.enableSource(searchOptions.enableSource())
			.forcedSearch(searchOptions.forcedSearch())
			.searchStrategy(searchOptions.searchStrategy())
			.build();
	}

	static Map<String, Object> toQwenTranslationOptions(QwenChatOptions.TranslationOptions translationOptions) {
		if (translationOptions == null) {
			return null;
		}

		// no java class is provided yet
		Map<String, Object> translationOptionsMap = new HashMap<>(5);
		translationOptionsMap.put("source_lang", translationOptions.sourceLang());
		translationOptionsMap.put("target_lang", translationOptions.targetLang());
		translationOptionsMap.put("terms", toTermList(translationOptions.terms()));
		translationOptionsMap.put("tm_list", toTermList(translationOptions.tmList()));
		translationOptionsMap.put("domains", translationOptions.domains());
		return translationOptionsMap;
	}

	static List<Map<String, String>> toTermList(List<QwenChatOptions.TranslationOptionTerm> list) {
		if (list == null) {
			return null;
		}

		return list.stream().map(term -> Map.of("source", term.source(), "target", term.target())).toList();
	}

	static boolean isStreamingToolCall(GenerationResult result) {
		return Optional.of(result)
			.map(GenerationResult::getOutput)
			.map(GenerationOutput::getChoices)
			.filter(choices -> !choices.isEmpty())
			.map(choices -> choices.get(0))
			.map(GenerationOutput.Choice::getMessage)
			.map(com.alibaba.dashscope.common.Message::getToolCalls)
			.map(toolCalls -> !toolCalls.isEmpty())
			.orElse(false);
	}

	static boolean isStreamingDone(GenerationResult result) {
		return getFinishReason(result) != null;
	}

	private static String getFinishReason(GenerationResult result) {
		return Optional.of(result)
			.map(GenerationResult::getOutput)
			.map(GenerationOutput::getChoices)
			.filter(choices -> !choices.isEmpty())
			.map(choices -> choices.get(0))
			.map(QwenApiHelper::getFinishReason)
			.orElse(null);
	}

	private static String getFinishReason(GenerationOutput.Choice choice) {
		String finishReason = choice.getFinishReason();
		return StringUtils.hasText(finishReason) && !"null".equals(finishReason) ? finishReason : null;
	}

	private static String getFinishReason(MultiModalConversationOutput.Choice choice) {
		String finishReason = choice.getFinishReason();
		return StringUtils.hasText(finishReason) && !"null".equals(finishReason) ? finishReason : null;
	}

	static GenerationResult newGenerationResult() {
		DashScopeResult emptyResult = new DashScopeResult();
		emptyResult.setOutput(new JsonObject());
		return GenerationResult.fromDashScopeResult(emptyResult);
	}

	static GenerationResult mergeResult(GenerationResult previous, GenerationResult current) {
		String requestId = getOrDefault(current.getRequestId(), previous.getRequestId());
		GenerationUsage usage = getOrDefault(current.getUsage(), previous.getUsage());
		GenerationOutput output = mergeOutput(previous.getOutput(), current.getOutput());

		GenerationResult result = newGenerationResult();
		result.setRequestId(requestId);
		result.setUsage(usage);
		result.setOutput(output);

		return result;
	}

	private static GenerationOutput mergeOutput(GenerationOutput previous, GenerationOutput current) {
		GenerationOutput output = new GenerationOutput();

		String finishReason = getOrDefault(current.getFinishReason(), previous.getFinishReason());
		String text = merge(current.getText(), previous.getText());
		List<GenerationOutput.Choice> choices = mergeChoices(output, previous.getChoices(), current.getChoices());
		SearchInfo searchInfo = mergeSearchInfo(previous.getSearchInfo(), current.getSearchInfo());

		output.setFinishReason(finishReason);
		output.setText(text);
		output.setChoices(choices);
		output.setSearchInfo(searchInfo);

		return output;
	}

	private static SearchInfo mergeSearchInfo(SearchInfo previous, SearchInfo current) {
		if (previous == null) {
			return current;
		}
		if (current == null) {
			return previous;
		}
		List<SearchInfo.SearchResult> searchResults = merge(previous.getSearchResults(), current.getSearchResults());
		return SearchInfo.builder().searchResults(searchResults).build();
	}

	private static List<GenerationOutput.Choice> mergeChoices(GenerationOutput output,
			List<GenerationOutput.Choice> previous, List<GenerationOutput.Choice> current) {
		List<GenerationOutput.Choice> choices = new ArrayList<>(1); // in most cases,
																	// there is only one.
		GenerationOutput.Choice lastPreviousChoice = null;

		if (previous != null) {
			lastPreviousChoice = previous.get(previous.size() - 1);
			if (previous.size() > 1) {
				choices.addAll(previous.subList(0, previous.size() - 1));
			}
		}

		if (current != null) {
			if (current.size() > 1) {
				throw new IllegalStateException("Currently only one choice is supported per message!");
			}
			var currentChoice = current.iterator().next();
			choices.add(mergeChoice(output, lastPreviousChoice, currentChoice));
		}
		else {
			if (lastPreviousChoice != null) {
				choices.add(lastPreviousChoice);
			}
		}

		return choices;
	}

	private static GenerationOutput.Choice mergeChoice(GenerationOutput output, GenerationOutput.Choice previous,
			GenerationOutput.Choice current) {
		if (previous == null) {
			return current;
		}
		if (current == null) {
			return previous;
		}

		Integer index = getOrDefault(current.getIndex(), previous.getIndex());
		String finishReason = getOrDefault(current.getFinishReason(), previous.getFinishReason());
		com.alibaba.dashscope.common.Message message = mergeMessage(previous.getMessage(), current.getMessage());

		GenerationOutput.Choice choice = output.new Choice();
		choice.setIndex(index);
		choice.setFinishReason(finishReason);
		choice.setMessage(message);

		return choice;
	}

	private static com.alibaba.dashscope.common.Message mergeMessage(com.alibaba.dashscope.common.Message previous,
			com.alibaba.dashscope.common.Message current) {

		if (previous == null) {
			return current;
		}
		if (current == null) {
			return previous;
		}

		String content = merge(previous.getContent(), current.getContent());
		String reasoningContent = merge(previous.getReasoningContent(), current.getReasoningContent());
		String role = getOrDefault(current.getRole(), previous.getRole());
		role = getOrDefault(role, Role.ASSISTANT.getValue());
		String name = getOrDefault(current.getName(), previous.getName());
		List<MessageContentBase> contents = merge(previous.getContents(), current.getContents());
		List<ToolCallBase> toolCalls = mergeToolCalls(previous.getToolCalls(), current.getToolCalls());
		String toolCallId = getOrDefault(current.getToolCallId(), previous.getToolCallId());

		return com.alibaba.dashscope.common.Message.builder()
			.content(content)
			.contents(contents)
			.toolCalls(toolCalls)
			.toolCallId(toolCallId)
			.name(name)
			.role(role)
			.reasoningContent(reasoningContent)
			.build();
	}

	private static List<ToolCallBase> mergeToolCalls(List<ToolCallBase> previous, List<ToolCallBase> current) {
		List<ToolCallBase> toolCalls = new ArrayList<>(1); // in most cases, there is only
															// one.
		ToolCallBase lastPreviousTooCall = null;

		if (previous != null) {
			lastPreviousTooCall = previous.get(previous.size() - 1);
			if (previous.size() > 1) {
				toolCalls.addAll(previous.subList(0, previous.size() - 1));
			}
		}

		if (current != null) {
			if (current.size() > 1) {
				throw new IllegalStateException("Currently only one tool call is supported per message!");
			}
			var currentToolCall = current.iterator().next();
			if (StringUtils.hasText(currentToolCall.getId())) {
				if (lastPreviousTooCall != null) {
					toolCalls.add(lastPreviousTooCall);
				}
				toolCalls.add(currentToolCall);
			}
			else {
				toolCalls.add(mergeToolCall(lastPreviousTooCall, currentToolCall));
			}
		}
		else {
			if (lastPreviousTooCall != null) {
				toolCalls.add(lastPreviousTooCall);
			}
		}

		return toolCalls;
	}

	private static ToolCallBase mergeToolCall(ToolCallBase previous, ToolCallBase current) {
		if (previous == null) {
			return current;
		}

		String id = (StringUtils.hasText(current.getId()) ? current.getId() : previous.getId());
		String type = getOrDefault(current.getType(), previous.getType());

		if (previous instanceof ToolCallFunction previousToolCallFunction
				&& current instanceof ToolCallFunction currentToolCallFunction) {
			ToolCallFunction newToolCall = new ToolCallFunction();
			ToolCallFunction.CallFunction callFunction = mergeToolCallFunction(newToolCall,
					previousToolCallFunction.getFunction(), currentToolCallFunction.getFunction());
			newToolCall.setFunction(callFunction);
			newToolCall.setId(id);
			newToolCall.setType(type);
			return newToolCall;
		}
		else if (current instanceof ToolCallCodeInterpreter) {
			ToolCallCodeInterpreter newToolCall = new ToolCallCodeInterpreter();
			newToolCall.setId(id);
			newToolCall.setType(type);
			return newToolCall;
		}
		else if (previous instanceof ToolCallQuarkSearch previousQuarkToolCall
				&& current instanceof ToolCallQuarkSearch currentQuarkToolCall) {
			Map<String, String> quarkSearch = merge(previousQuarkToolCall.getQuarkSearch(),
					currentQuarkToolCall.getQuarkSearch());
			ToolCallQuarkSearch newToolCall = new ToolCallQuarkSearch();
			newToolCall.setId(id);
			newToolCall.setType(type);
			newToolCall.setQuarkSearch(quarkSearch);
			return newToolCall;
		}
		else {
			return current;
		}
	}

	private static ToolCallFunction.CallFunction mergeToolCallFunction(ToolCallFunction toolCallFunction,
			ToolCallFunction.CallFunction previous, ToolCallFunction.CallFunction current) {
		if (previous == null) {
			return current;
		}

		String name = merge(previous.getName(), current.getName());
		String arguments = merge(previous.getArguments(), current.getArguments());
		String output = merge(previous.getOutput(), current.getOutput());

		ToolCallFunction.CallFunction callFunction = toolCallFunction.new CallFunction();
		callFunction.setName(name);
		callFunction.setArguments(arguments);
		callFunction.setOutput(output);
		return callFunction;
	}

	private static <K, V> Map<K, V> merge(Map<K, V> previous, Map<K, V> current) {
		if (previous == null) {
			return current;
		}
		if (current == null) {
			return previous;
		}
		Map<K, V> merged = new HashMap<>(previous);
		merged.putAll(current);
		return merged;
	}

	private static <T> List<T> merge(List<T> previous, List<T> current) {
		if (previous == null) {
			return current;
		}
		if (current == null) {
			return previous;
		}
		List<T> merged = new ArrayList<>(previous.size() + current.size());
		merged.addAll(previous);
		merged.addAll(current);
		return merged;
	}

	private static String merge(String previous, String current) {
		if (previous == null) {
			return current;
		}
		if (current == null) {
			return previous;
		}
		return previous + current;
	}

	static Float frequencyPenaltyToRepetitionPenalty(Double frequencyPenalty) {
		// repetitionPenalty:
		// https://www.alibabacloud.com/help/en/model-studio/use-qwen-by-calling-api#2ed5ee7377fum
		// frequencyPenalty:
		// https://platform.openai.com/docs/api-reference/chat/create#chat-create-frequency_penalty
		// map: [-2, 2] -> (0, ∞), and 0 -> 1
		// use logit function (https://en.wikipedia.org/wiki/Logit)

		if (frequencyPenalty == null) {
			return null;
		}
		else if (frequencyPenalty >= 2) {
			return Float.POSITIVE_INFINITY;
		}
		else if (frequencyPenalty < -2) {
			throw new IllegalArgumentException("Value of frequencyPenalty must be within [-2.0, 2.0]");
		}

		// limit the input to 0.5 to 1 (as the repetition penalty is a positive value)
		double x = (frequencyPenalty + 6) / 8;
		// make sure repetition penalty is 1 when frequency penalty is 0
		double denominator = logit(0.75d);

		return (float) (logit(x) / denominator);
	}

	private static double logit(double x) {
		return Math.log(x / (1 - x));
	}

	static List<Generation> generationsFrom(GenerationResult result) {
		return Optional.of(result)
			.map(GenerationResult::getOutput)
			.map(GenerationOutput::getChoices)
			.orElse(Collections.emptyList())
			.stream()
			.map(choice -> buildGeneration(result.getRequestId(), choice))
			.toList();
	}

	private static Generation buildGeneration(String id, GenerationOutput.Choice choice) {
		com.alibaba.dashscope.common.Message message = choice.getMessage();
		List<AssistantMessage.ToolCall> toolCalls = Optional.ofNullable(message.getToolCalls())
			.orElse(Collections.emptyList())
			.stream()
			.filter(ToolCallFunction.class::isInstance)
			.map(ToolCallFunction.class::cast)
			.map(toolCall -> new AssistantMessage.ToolCall(toolCall.getId(), toolCall.getType(),
					toolCall.getFunction().getName(), toolCall.getFunction().getArguments()))
			.toList();

		String finishReason = getFinishReason(choice);
		List<Media> media = new LinkedList<>();
		String text = message.getContent();
		List<MessageContentBase> contents = message.getContents();
		if (!CollectionUtils.isEmpty(contents)) {
			for (MessageContentBase content : contents) {
				if (content instanceof MessageContentImageURL imageContent) {
					media
						.add(Media.builder().mimeType(Media.Format.IMAGE_PNG).data(imageContent.getImageURL()).build());
				}
				else if (content instanceof MessageContentText textContent) {
					media.add(Media.builder().mimeType(Media.Format.DOC_TXT).data(textContent.getText()).build());
				}
			}
		}
		Map<String, Object> metadata = CollectionUtils.newHashMap(6);
		putIfNotNull(metadata, "id", id);
		putIfNotNull(metadata, "role", message.getRole());
		putIfNotNull(metadata, "name", message.getName());
		putIfNotNull(metadata, "index", choice.getIndex());
		putIfNotNull(metadata, "finishReason", finishReason);
		putIfNotNull(metadata, "reasoningContent", message.getReasoningContent());

		return new Generation(new AssistantMessage(text, metadata, toolCalls, media),
				ChatGenerationMetadata.builder().finishReason(finishReason).build());
	}

	static Usage defaultUsageFrom(GenerationUsage qwenUsage) {
		return qwenUsage == null ? new EmptyUsage() : new DefaultUsage(qwenUsage.getInputTokens(),
				qwenUsage.getOutputTokens(), qwenUsage.getTotalTokens(), qwenUsage);
	}

	static List<Generation> generationsFrom(MultiModalConversationResult result) {
		return Optional.of(result)
			.map(MultiModalConversationResult::getOutput)
			.map(MultiModalConversationOutput::getChoices)
			.orElse(Collections.emptyList())
			.stream()
			.map(choice -> buildGeneration(result.getRequestId(), choice))
			.toList();
	}

	private static Generation buildGeneration(String id, MultiModalConversationOutput.Choice choice) {
		com.alibaba.dashscope.common.MultiModalMessage message = choice.getMessage();
		List<AssistantMessage.ToolCall> toolCalls = Collections.emptyList();

		String finishReason = getFinishReason(choice);
		List<Media> media = new LinkedList<>();
		List<String> textContents = new LinkedList<>();
		List<Map<String, Object>> contents = message.getContent();
		if (!CollectionUtils.isEmpty(contents)) {
			for (Map<String, Object> content : contents) {
				if (content.containsKey("text")) {
					textContents.add((String) content.get("text"));
				}

				if (content.containsKey("image")) {
					media.add(Media.builder().mimeType(Media.Format.IMAGE_PNG).data(content.get("image")).build());
				}
			}
		}

		String text = String.join("\n", textContents);

		Map<String, Object> metadata = CollectionUtils.newHashMap(3);
		putIfNotNull(metadata, "id", id);
		putIfNotNull(metadata, "role", message.getRole());
		putIfNotNull(metadata, "finishReason", finishReason);

		return new Generation(new AssistantMessage(text, metadata, toolCalls, media),
				ChatGenerationMetadata.builder().finishReason(finishReason).build());
	}

	static Usage defaultUsageFrom(MultiModalConversationUsage qwenUsage) {
		return qwenUsage == null ? new EmptyUsage() : new DefaultUsage(qwenUsage.getInputTokens(),
				qwenUsage.getOutputTokens(), qwenUsage.getTotalTokens(), qwenUsage);
	}

	static QwenSearchInfo toQwenSearchInfo(SearchInfo searchInfo) {
		List<QwenSearchResult> searchResults = searchInfo == null
				|| CollectionUtils.isEmpty(searchInfo.getSearchResults()) ? Collections.emptyList()
						: searchInfo.getSearchResults().stream().map(QwenApiHelper::toQwenSearchResult).toList();

		return QwenSearchInfo.builder().searchResults(searchResults).build();
	}

	private static QwenSearchResult toQwenSearchResult(SearchInfo.SearchResult searchResult) {
		return QwenSearchResult.builder()
			.siteName(searchResult.getSiteName())
			.icon(searchResult.getIcon())
			.index(searchResult.getIndex())
			.title(searchResult.getTitle())
			.url(searchResult.getUrl())
			.build();
	}

	static <T> ResultCallback<T> toQwenResultCallback(Sinks.Many<T> sink) {
		return new ResultCallback<>() {
			@Override
			public void onEvent(T result) {
				sink.tryEmitNext(result);
			}

			@Override
			public void onComplete() {
				sink.tryEmitComplete();
			}

			@Override
			public void onError(Exception e) {
				sink.tryEmitError(e);
			}
		};
	}

	public static <T> T getOrDefault(T value, T defaultValue) {
		return value != null ? value : defaultValue;
	}

	public static <T> List<T> copyIfNotNull(List<T> list) {
		return list == null ? null : Collections.unmodifiableList(list);
	}

	public static <T> Set<T> copyIfNotNull(Set<T> set) {
		return set == null ? null : Collections.unmodifiableSet(set);
	}

	public static <K, V> Map<K, V> copyIfNotNull(Map<K, V> map) {
		return map == null ? null : Collections.unmodifiableMap(map);
	}

	public static <K, V> void putIfNotNull(Map<K, V> map, K key, V value) {
		if (value != null) {
			map.put(key, value);
		}
	}

}
