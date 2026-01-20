/*
 * Copyright 2025-2026 the original author or authors.
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

package org.springframework.ai.skill.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.skill.core.SkillKit;

/**
 * Skill Advisor - manages skill lifecycle and system prompt injection.
 *
 * <p>
 * This advisor implements CallAdvisor and StreamAdvisor to:
 *
 * <ul>
 * <li>Inject skill system prompt before conversation
 * <li>Clean up skill state after conversation
 * </ul>
 *
 * <p>
 * <b>Tool Injection:</b>
 * <ul>
 * <li>Dynamic tool injection is handled by {@code SkillAwareToolCallingManager}
 * <li>SkillAwareToolCallingManager.resolveToolDefinitions() provides tools to LLM
 * <li>SkillAwareToolCallbackResolver.resolve() finds tools during execution
 * <li>This advisor ONLY injects skill system prompt and manages skill lifecycle
 * </ul>
 *
 * <p>
 * <b>Usage with SkillAwareToolCallingManager (recommended):</b>
 *
 * <pre>{@code
 * // 1. Create SkillKit and register skills
 * SkillBox skillBox = new SimpleSkillBox();
 * SkillPoolManager poolManager = new DefaultSkillPoolManager();
 * SkillKit skillKit = new DefaultSkillKit(skillBox, poolManager);
 * skillKit.registerSkill(metadata, loader);
 *
 * // 2. Create SkillAwareToolCallingManager for tool injection
 * SkillAwareToolCallingManager toolManager =
 *     SkillAwareToolCallingManager.builder()
 *         .skillKit(skillKit)
 *         .build();
 *
 * // 3. Create ChatModel with SkillAwareToolCallingManager
 * OpenAiChatModel chatModel = OpenAiChatModel.builder()
 *     .toolCallingManager(toolManager)  // Handles tool injection
 *     .build();
 *
 * // 4. Create SkillAwareAdvisor for prompt injection
 * SkillAwareAdvisor advisor = new SkillAwareAdvisor(skillKit);
 *
 * // Build ChatClient with ONLY the advisor (NO tools registered at build time!)
 * ChatClient client = ChatClient.builder(chatModel)
 *     .defaultSystem("Your base system prompt here")
 *     .defaultAdvisors(advisor)
 *     .build();
 * }</pre>
 *
 * <p>
 * <b>Alternative usage with static helpers (simpler):</b>
 *
 * <pre>{@code
 * String systemPrompt = SkillAwareAdvisor.buildSystemPrompt(basePrompt, skillKit);
 * ChatClient client = ChatClient.builder(chatModel)
 *     .defaultSystem(systemPrompt)
 *     .build();
 * // Don't forget to cleanup after each turn:
 * SkillAwareAdvisor.cleanupSkills(skillKit);
 * }</pre>
 */
public class SkillAwareAdvisor implements CallAdvisor, StreamAdvisor {

	private static final Logger logger = LoggerFactory.getLogger(SkillAwareAdvisor.class);

	private final SkillKit skillKit;

	private SkillAwareAdvisor(Builder builder) {
		this.skillKit = Objects.requireNonNull(builder.skillKit, "skillKit cannot be null");
	}

	/**
	 * Creates a new builder instance.
	 * @return new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Build system prompt with skill information (static utility method).
	 *
	 * <p>
	 * This is a simplified helper method for cases where you don't want to use the full
	 * Advisor pattern. Useful for simple use cases or when you need manual control.
	 * @param basePrompt The base system prompt
	 * @param skillKit The SkillKit containing skill management and prompt generation
	 * @return Combined system prompt with skill information
	 */
	public static String buildSystemPrompt(String basePrompt, SkillKit skillKit) {
		String skillPrompt = skillKit.getSkillSystemPrompt();

		if (skillPrompt == null || skillPrompt.isEmpty()) {
			return basePrompt;
		}

		if (basePrompt == null || basePrompt.isEmpty()) {
			return skillPrompt;
		}

		return basePrompt + "\n\n" + skillPrompt;
	}

	/**
	 * Cleanup skills after conversation (static utility method).
	 *
	 * <p>
	 * This is a simplified helper method for cases where you don't want to use the full
	 * Advisor pattern. When using the Advisor pattern, cleanup is automatic.
	 * @param skillKit The SkillKit to clean up
	 */
	public static void cleanupSkills(SkillKit skillKit) {
		skillKit.deactivateAllSkills();
	}

	@Override
	public String getName() {
		return "SpringAiSkillAdvisor";
	}

	@Override
	public int getOrder() {
		return 0; // Execute first to inject system prompt
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
		ChatClientRequest modifiedRequest = this.injectSkillSystemPrompt(request);

		try {
			ChatClientResponse response = chain.nextCall(modifiedRequest);
			cleanupSkills(this.skillKit);
			return response;
		}
		catch (Exception e) {
			logger.error("Error in skill advisor", e);
			cleanupSkills(this.skillKit);
			throw e;
		}
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
		ChatClientRequest modifiedRequest = this.injectSkillSystemPrompt(request);
		Flux<ChatClientResponse> responses = chain.nextStream(modifiedRequest);
		return new ChatClientMessageAggregator().aggregateChatClientResponse(responses,
				response -> cleanupSkills(this.skillKit));
	}

	// ==================== Static Helper Methods (Extension API) ====================

	/**
	 * Inject skill system prompt into the request.
	 *
	 * <p>
	 * This method:
	 * <ul>
	 * <li>Extracts base system prompt from request
	 * <li>Combines it with skill system prompt using buildSystemPrompt()
	 * <li>Returns modified request with updated system message
	 * </ul>
	 *
	 * <p>
	 * <b>Note:</b> Tool injection is handled by
	 * {@code SkillAwareToolCallingManager.resolveToolDefinitions()}, not by this Advisor.
	 * @param request The original request
	 * @return Modified request with skill prompt
	 */
	private ChatClientRequest injectSkillSystemPrompt(ChatClientRequest request) {
		// 1. Extract and combine system prompt
		List<Message> messages = new ArrayList<>(request.prompt().getInstructions());

		String basePrompt = "";
		boolean hasSystemMessage = false;
		int systemMessageIndex = -1;

		for (int i = 0; i < messages.size(); i++) {
			if (messages.get(i) instanceof SystemMessage systemMessage) {
				String text = systemMessage.getText();
				basePrompt = (text != null) ? text : "";
				hasSystemMessage = true;
				systemMessageIndex = i;
				break;
			}
		}

		// Build combined prompt with skill information
		String combinedPrompt = buildSystemPrompt(basePrompt, this.skillKit);

		// Replace existing system message or add new one
		if (hasSystemMessage) {
			messages.set(systemMessageIndex, new SystemMessage(combinedPrompt));
		}
		else {
			messages.add(0, new SystemMessage(combinedPrompt));
		}

		// 2. Create modified request with updated prompt
		// Note: Tools are NOT injected here - SkillAwareToolCallingManager handles that!
		ChatOptions originalOptions = request.prompt().getOptions();
		return request.mutate().prompt(new Prompt(messages, originalOptions)).build();
	}

	/**
	 * Builder for SkillAwareAdvisor.
	 */
	public static class Builder {

		private @Nullable SkillKit skillKit;

		private Builder() {
		}

		/**
		 * Sets the skill kit.
		 * @param skillKit skill kit instance
		 * @return this builder
		 */
		public Builder skillKit(SkillKit skillKit) {
			this.skillKit = skillKit;
			return this;
		}

		/**
		 * Builds the SkillAwareAdvisor instance.
		 * @return new SkillAwareAdvisor instance
		 */
		public SkillAwareAdvisor build() {
			if (this.skillKit == null) {
				throw new IllegalArgumentException("skillKit cannot be null");
			}
			return new SkillAwareAdvisor(this);
		}

	}

}
