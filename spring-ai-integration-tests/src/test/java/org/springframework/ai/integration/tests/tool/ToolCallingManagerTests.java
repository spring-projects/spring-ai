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

package org.springframework.ai.integration.tests.tool;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.integration.tests.TestApplication;
import org.springframework.ai.integration.tests.tool.domain.Author;
import org.springframework.ai.integration.tests.tool.domain.Book;
import org.springframework.ai.integration.tests.tool.domain.BookService;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ToolCallingManager}.
 *
 * @author Thomas Vitale
 */
@SpringBootTest(classes = TestApplication.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
public class ToolCallingManagerTests {

	private final Tools tools = new Tools();

	private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

	@Autowired
	OpenAiChatModel openAiChatModel;

	@Test
	void explicitToolCallingExecutionWithNewOptions() {
		ChatOptions chatOptions = ToolCallingChatOptions.builder()
			.toolCallbacks(ToolCallbacks.from(this.tools))
			.internalToolExecutionEnabled(false)
			.build();
		Prompt prompt = new Prompt(
				new UserMessage("What books written by %s are available in the library?".formatted("J.R.R. Tolkien")),
				chatOptions);
		runExplicitToolCallingExecutionWithOptions(chatOptions, prompt);
	}

	@Test
	void explicitToolCallingExecutionWithNewOptionsStream() {
		ChatOptions chatOptions = ToolCallingChatOptions.builder()
			.toolCallbacks(ToolCallbacks.from(this.tools))
			.internalToolExecutionEnabled(false)
			.build();
		Prompt prompt = new Prompt(new UserMessage("What books written by %s, %s, and %s are available in the library?"
			.formatted("J.R.R. Tolkien", "Philip Pullman", "C.S. Lewis")), chatOptions);
		runExplicitToolCallingExecutionWithOptionsStream(chatOptions, prompt);
	}

	private void runExplicitToolCallingExecutionWithOptions(ChatOptions chatOptions, Prompt prompt) {
		ChatResponse chatResponse = this.openAiChatModel.call(prompt);

		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.hasToolCalls()).isTrue();

		ToolExecutionResult toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, chatResponse);

		assertThat(toolExecutionResult.conversationHistory()).isNotEmpty();
		assertThat(toolExecutionResult.conversationHistory().stream().anyMatch(m -> m instanceof ToolResponseMessage))
			.isTrue();

		Prompt secondPrompt = new Prompt(toolExecutionResult.conversationHistory(), chatOptions);

		ChatResponse secondChatResponse = this.openAiChatModel.call(secondPrompt);

		assertThat(secondChatResponse).isNotNull();
		assertThat(secondChatResponse.getResult().getOutput().getText()).isNotEmpty()
			.contains("The Hobbit")
			.contains("The Lord of The Rings")
			.contains("The Silmarillion");
	}

	private void runExplicitToolCallingExecutionWithOptionsStream(ChatOptions chatOptions, Prompt prompt) {
		ChatResponse chatResponse = this.openAiChatModel.stream(prompt).flatMap(response -> {
			if (response.hasToolCalls()) {
				ToolExecutionResult toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, response);

				assertThat(toolExecutionResult.conversationHistory()).isNotEmpty();
				assertThat(toolExecutionResult.conversationHistory()
					.stream()
					.anyMatch(m -> m instanceof ToolResponseMessage)).isTrue();

				Prompt secondPrompt = new Prompt(toolExecutionResult.conversationHistory(), chatOptions);
				// return openAiChatModel.stream(secondPrompt);
				return Flux.just(this.openAiChatModel.call(secondPrompt));
			}
			return Flux.just(response);
		}).blockLast();

		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.getResult().getOutput().getText()).isNotEmpty()
			.contains("His Dark Materials")
			.contains("The Lion, the Witch and the Wardrob")
			.contains("The Hobbit")
			.contains("The Lord of The Rings")
			.contains("The Silmarillion");
	}

	static class Tools {

		private static final Logger logger = LoggerFactory.getLogger(Tools.class);

		private final BookService bookService = new BookService();

		@Tool(description = "Get the list of books written by the given author available in the library")
		List<Book> booksByAuthor(String author) {
			logger.info("Getting books by author: {}", author);
			return this.bookService.getBooksByAuthor(new Author(author));
		}

	}

}
