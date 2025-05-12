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

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.integration.tests.TestApplication;
import org.springframework.ai.integration.tests.tool.domain.Author;
import org.springframework.ai.integration.tests.tool.domain.Book;
import org.springframework.ai.integration.tests.tool.domain.BookService;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MethodToolCallback}.
 *
 * @author Thomas Vitale
 */
@SpringBootTest(classes = TestApplication.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class MethodToolCallbackTests {

	@Autowired
	OpenAiChatModel openAiChatModel;

	Tools tools = new Tools(new BookService());

	@Test
	void chatMethodNoArgs() {
		var content = ChatClient.builder(this.openAiChatModel)
			.build()
			.prompt()
			.user("Welcome the user to the library")
			.tools(this.tools)
			.call()
			.content();
		assertThat(content).isNotEmpty();
	}

	@Test
	void chatMethodVoid() {
		var content = ChatClient.builder(this.openAiChatModel)
			.build()
			.prompt()
			.user("Welcome %s to the library".formatted("James Bond"))
			.tools(this.tools)
			.call()
			.content();
		assertThat(content).isNotEmpty();
	}

	@Test
	void chatMethodSingle() {
		var content = ChatClient.builder(this.openAiChatModel)
			.build()
			.prompt()
			.user("What books written by %s are available in the library?".formatted("J.R.R. Tolkien"))
			.tools(this.tools)
			.call()
			.content();
		assertThat(content).isNotEmpty()
			.containsIgnoringCase("The Hobbit")
			.containsIgnoringCase("The Lord of The Rings")
			.containsIgnoringCase("The Silmarillion");
	}

	@Test
	void chatMethodList() {
		var content = ChatClient.builder(this.openAiChatModel)
			.build()
			.prompt()
			.user("What authors wrote the books %s and %s available in the library?".formatted("The Hobbit",
					"The Lion, the Witch and the Wardrobe"))
			.tools(this.tools)
			.call()
			.content();
		assertThat(content).isNotEmpty().contains("J.R.R. Tolkien").contains("C.S. Lewis");
	}

	@Test
	void chatMethodCallback() {
		var content = ChatClient.builder(this.openAiChatModel)
			.build()
			.prompt()
			.user("What authors wrote the books %s and %s available in the library?".formatted("The Hobbit",
					"The Lion, the Witch and the Wardrobe"))
			.toolCallbacks(ToolCallbacks.from(this.tools))
			.call()
			.content();
		assertThat(content).isNotEmpty().contains("J.R.R. Tolkien").contains("C.S. Lewis");
	}

	@Test
	void chatMethodCallbackDefault() {
		var content = ChatClient.builder(this.openAiChatModel)
			.defaultTools(this.tools)
			.build()
			.prompt()
			.user("How many books written by %s are available in the library?".formatted("J.R.R. Tolkien"))
			.call()
			.content();
		assertThat(content).isNotEmpty().containsAnyOf("three", "3");
	}

	static class Tools {

		private static final Logger logger = LoggerFactory.getLogger(Tools.class);

		private final BookService bookService;

		Tools(BookService bookService) {
			this.bookService = bookService;
		}

		@Tool(description = "Welcome users to the library")
		void welcome() {
			logger.info("Welcoming users to the library");
		}

		@Tool(description = "Welcome a specific user to the library")
		void welcomeUser(String user) {
			logger.info("Welcoming {} to the library", user);
		}

		@Tool(description = "Get the list of books written by the given author available in the library")
		List<Book> booksByAuthor(String author) {
			logger.info("Getting books by author: {}", author);
			return this.bookService.getBooksByAuthor(new Author(author));
		}

		@Tool(description = "Get the list of authors who wrote the given books available in the library")
		List<Author> authorsByBooks(List<String> books) {
			logger.info("Getting authors by books: {}", String.join(", ", books));
			return this.bookService.getAuthorsByBook(books.stream().map(b -> new Book(b, "")).toList());
		}

	}

}
