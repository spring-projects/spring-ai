/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.model.tool;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.Generation;

/**
 * The result of a tool execution.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public interface ToolExecutionResult {

	String FINISH_REASON = "returnDirect";

	String METADATA_TOOL_ID = "toolId";

	String METADATA_TOOL_NAME = "toolName";

	/**
	 * The history of messages exchanged during the conversation, including the tool
	 * execution result.
	 */
	List<Message> conversationHistory();

	/**
	 * Whether the tool execution result should be returned directly or passed back to the
	 * model.
	 */
	default boolean returnDirect() {
		return false;
	}

	/**
	 * Whether, in streaming mode and when internal tool execution is enabled, the model
	 * should continue its response after the tool execution result is sent back.
	 */
	default boolean continuousStream() {
		return false;
	}

	/**
	 * Create a default {@link ToolExecutionResult} builder.
	 */
	static DefaultToolExecutionResult.Builder builder() {
		return DefaultToolExecutionResult.builder();
	}

	/**
	 * Build a list of {@link Generation} from the tool execution result, useful for
	 * sending the tool execution result to the client directly.
	 */
	static List<Generation> buildGenerations(ToolExecutionResult toolExecutionResult) {
		List<Message> conversationHistory = toolExecutionResult.conversationHistory();
		List<Generation> generations = new ArrayList<>();
		if (conversationHistory
			.get(conversationHistory.size() - 1) instanceof ToolResponseMessage toolResponseMessage) {
			toolResponseMessage.getResponses().forEach(response -> {
				AssistantMessage assistantMessage = new AssistantMessage(response.responseData());
				Generation generation = new Generation(assistantMessage,
						ChatGenerationMetadata.builder()
							.metadata(METADATA_TOOL_ID, response.id())
							.metadata(METADATA_TOOL_NAME, response.name())
							.finishReason(FINISH_REASON)
							.build());
				generations.add(generation);
			});
		}
		return generations;
	}

}
