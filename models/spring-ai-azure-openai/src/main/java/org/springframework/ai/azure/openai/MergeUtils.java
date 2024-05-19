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
package org.springframework.ai.azure.openai;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.azure.ai.openai.models.AzureChatExtensionsMessageContext;
import com.azure.ai.openai.models.ChatChoice;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsFunctionToolCall;
import com.azure.ai.openai.models.ChatCompletionsToolCall;
import com.azure.ai.openai.models.ChatResponseMessage;
import com.azure.ai.openai.models.CompletionsFinishReason;
import com.azure.ai.openai.models.CompletionsUsage;
import com.azure.ai.openai.models.ContentFilterResultsForChoice;
import com.azure.ai.openai.models.ContentFilterResultsForPrompt;
import com.azure.ai.openai.models.FunctionCall;

import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Utility class for merging ChatCompletions instances and their associated objects. Uses
 * reflection to create instances with private constructors and set private fields.
 *
 * @author Grogdunn
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class MergeUtils {

	/**
	 * Create a new instance of the given class. Can be used to create instances with
	 * private constructors.
	 * @param <T> the type of the class to be created.
	 * @param clazz the class to create an instance of.
	 * @param args the arguments to pass to the constructor.
	 * @return a new instance of the given class.
	 */
	private static <T> T newInstance(Class<T> clazz, Object... args) {
		return newInstance(0, clazz, args);
	}

	/**
	 * Create a new instance of the given class using the constructor at the given index.
	 * Can be used to create instances with private constructors.
	 * @param <T> the type of the class to be created.
	 * @param index the index of the constructor to use.
	 * @param clazz the class to create an instance of.
	 * @param args the arguments to pass to the constructor.
	 * @return a new instance of the given class.
	 */
	private static <T> T newInstance(int index, Class<T> clazz, Object... args) {
		try {
			@SuppressWarnings("unchecked")
			Constructor<T> constructor = (Constructor<T>) clazz.getDeclaredConstructors()[index];
			constructor.setAccessible(true);
			return constructor.newInstance(args);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Set the value of a private field in the given class instance.
	 * @param classInstance the class instance to set the field on.
	 * @param fieldName the name of the field to set.
	 * @param fieldValue the value to set the field to.
	 */
	private static void setField(Object classInstance, String fieldName, Object fieldValue) {
		try {
			Field field = classInstance.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			field.set(classInstance, fieldValue);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @return an empty ChatCompletions instance.
	 */
	public static ChatCompletions emptyChatCompletions() {
		String id = null;
		List<ChatChoice> choices = new ArrayList<>();
		CompletionsUsage usage = null;
		long createdAt = 0;
		ChatCompletions chatCompletionsInstance = newInstance(ChatCompletions.class, id, createdAt, choices, usage);
		List<ContentFilterResultsForPrompt> promptFilterResults = new ArrayList<>();
		setField(chatCompletionsInstance, "promptFilterResults", promptFilterResults);
		String systemFingerprint = null;
		setField(chatCompletionsInstance, "systemFingerprint", systemFingerprint);

		return chatCompletionsInstance;
	}

	/**
	 * Merge two ChatCompletions instances into a single ChatCompletions instance.
	 * @param left the left ChatCompletions instance.
	 * @param right the right ChatCompletions instance.
	 * @return a merged ChatCompletions instance.
	 */
	public static ChatCompletions mergeChatCompletions(ChatCompletions left, ChatCompletions right) {

		Assert.isTrue(left != null, "");
		if (right == null) {
			Assert.isTrue(left.getId() != null, "");
			return left;
		}
		Assert.isTrue(left.getId() != null || right.getId() != null, "");

		String id = left.getId() != null ? left.getId() : right.getId();

		List<ChatChoice> choices = null;
		if (right.getChoices() == null) {
			choices = left.getChoices();
		}
		else {
			if (CollectionUtils.isEmpty(left.getChoices())) {
				choices = right.getChoices();
			}
			else {
				choices = List.of(mergeChatChoice(left.getChoices().get(0), right.getChoices().get(0)));
			}
		}

		// For these properties if right contains that use it!
		CompletionsUsage usage = right.getUsage() == null ? left.getUsage() : right.getUsage();

		OffsetDateTime createdAt = left.getCreatedAt().isAfter(right.getCreatedAt()) ? left.getCreatedAt()
				: right.getCreatedAt();

		ChatCompletions instance = newInstance(1, ChatCompletions.class, id, createdAt, choices, usage);

		List<ContentFilterResultsForPrompt> promptFilterResults = right.getPromptFilterResults() == null
				? left.getPromptFilterResults() : right.getPromptFilterResults();
		setField(instance, "promptFilterResults", promptFilterResults);

		String systemFingerprint = right.getSystemFingerprint() == null ? left.getSystemFingerprint()
				: right.getSystemFingerprint();
		setField(instance, "systemFingerprint", systemFingerprint);
		return instance;
	}

	/**
	 * Merge two ChatChoice instances into a single ChatChoice instance.
	 * @param left the left ChatChoice instance to merge.
	 * @param right the right ChatChoice instance to merge.
	 * @return a merged ChatChoice instance.
	 */
	private static ChatChoice mergeChatChoice(ChatChoice left, ChatChoice right) {

		int index = Math.max(left.getIndex(), right.getIndex());

		CompletionsFinishReason finishReason = left.getFinishReason() != null ? left.getFinishReason()
				: right.getFinishReason();

		var logprobs = left.getLogprobs() != null ? left.getLogprobs() : right.getLogprobs();

		final ChatChoice instance = newInstance(ChatChoice.class, logprobs, index, finishReason);

		ChatResponseMessage message = null;
		if (left.getMessage() == null) {
			message = right.getMessage();
		}
		else {
			message = mergeChatResponseMessage(left.getMessage(), right.getMessage());
		}

		setField(instance, "message", message);

		ChatResponseMessage delta = null;
		if (left.getDelta() == null) {
			delta = right.getDelta();
		}
		else {
			delta = mergeChatResponseMessage(left.getDelta(), right.getDelta());
		}
		setField(instance, "delta", delta);

		ContentFilterResultsForChoice contentFilterResults = left.getContentFilterResults() != null
				? left.getContentFilterResults() : right.getContentFilterResults();
		setField(instance, "contentFilterResults", contentFilterResults);

		var finishDetails = left.getFinishDetails() != null ? left.getFinishDetails() : right.getFinishDetails();
		setField(instance, "finishDetails", finishDetails);

		var enhancements = left.getEnhancements() != null ? left.getEnhancements() : right.getEnhancements();
		setField(instance, "enhancements", enhancements);

		return instance;
	}

	/**
	 * Merge two ChatResponseMessage instances into a single ChatResponseMessage instance.
	 * @param left the left ChatResponseMessage instance to merge.
	 * @param right the right ChatResponseMessage instance to merge.
	 * @return a merged ChatResponseMessage instance.
	 */
	private static ChatResponseMessage mergeChatResponseMessage(ChatResponseMessage left, ChatResponseMessage right) {

		var role = left.getRole() != null ? left.getRole() : right.getRole();
		String content = null;
		if (left.getContent() != null && right.getContent() != null) {
			content = left.getContent().concat(right.getContent());
		}
		else if (left.getContent() == null) {
			content = right.getContent();
		}
		else {
			content = left.getContent();
		}

		ChatResponseMessage instance = newInstance(ChatResponseMessage.class, role, content);

		List<ChatCompletionsToolCall> toolCalls = new ArrayList<>();
		if (left.getToolCalls() == null) {
			if (right.getToolCalls() != null) {
				toolCalls.addAll(right.getToolCalls());
			}
		}
		else if (right.getToolCalls() == null) {
			toolCalls.addAll(left.getToolCalls());
		}
		else {
			toolCalls.addAll(left.getToolCalls());
			final var lastToolIndex = toolCalls.size() - 1;
			ChatCompletionsToolCall lastTool = toolCalls.get(lastToolIndex);
			if (right.getToolCalls().get(0).getId() == null) {

				lastTool = mergeChatCompletionsToolCall(lastTool, right.getToolCalls().get(0));

				toolCalls.remove(lastToolIndex);
				toolCalls.add(lastTool);
			}
			else {
				toolCalls.add(right.getToolCalls().get(0));
			}
		}

		setField(instance, "toolCalls", toolCalls);

		FunctionCall functionCall = null;

		if (left.getFunctionCall() == null) {
			functionCall = right.getFunctionCall();
		}
		else {
			functionCall = MergeUtils.mergeFunctionCall(left.getFunctionCall(), right.getFunctionCall());
		}

		setField(instance, "functionCall", functionCall);

		AzureChatExtensionsMessageContext context = left.getContext() != null ? left.getContext() : right.getContext();
		setField(instance, "context", context);

		return instance;
	}

	/**
	 * Merge two ChatCompletionsToolCall instances into a single ChatCompletionsToolCall
	 * instance.
	 * @param left the left ChatCompletionsToolCall instance to merge.
	 * @param right the right ChatCompletionsToolCall instance to merge.
	 * @return a merged ChatCompletionsToolCall instance.
	 */
	private static ChatCompletionsToolCall mergeChatCompletionsToolCall(ChatCompletionsToolCall left,
			ChatCompletionsToolCall right) {
		Assert.isTrue(Objects.equals(left.getType(), right.getType()),
				"Cannot merge different type of AccessibleChatCompletionsToolCall");
		if (!"function".equals(left.getType())) {
			throw new UnsupportedOperationException("Only function chat completion tool is supported");
		}

		String id = left.getId() != null ? left.getId() : right.getId();
		var mergedFunction = mergeFunctionCall(((ChatCompletionsFunctionToolCall) left).getFunction(),
				((ChatCompletionsFunctionToolCall) right).getFunction());

		return new ChatCompletionsFunctionToolCall(id, mergedFunction);
	}

	/**
	 * Merge two FunctionCall instances into a single FunctionCall instance.
	 * @param left the left, input FunctionCall instance.
	 * @param right the right, input FunctionCall instance.
	 * @return a merged FunctionCall instance.
	 */
	private static FunctionCall mergeFunctionCall(FunctionCall left, FunctionCall right) {
		var name = left.getName() != null ? left.getName() : right.getName();
		String arguments = null;
		if (left.getArguments() != null && right.getArguments() != null) {
			arguments = left.getArguments() + right.getArguments();
		}
		else if (left.getArguments() == null) {
			arguments = right.getArguments();
		}
		else {
			arguments = left.getArguments();
		}
		return new FunctionCall(name, arguments);
	}

}
