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
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.integration.tests.TestApplication;
import org.springframework.ai.integration.tests.tool.domain.Author;
import org.springframework.ai.integration.tests.tool.domain.Book;
import org.springframework.ai.integration.tests.tool.domain.BookService;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.context.annotation.Import;
import org.springframework.core.log.LogAccessor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link FunctionToolCallback}.
 *
 * @author Thomas Vitale
 */
@SpringBootTest(classes = TestApplication.class)
@Import(FunctionToolCallbackTests.Tools.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
public class FunctionToolCallbackTests {

	// @formatter:off

	private static final LogAccessor logger = new LogAccessor(LogFactory.getLog(FunctionToolCallbackTests.class));

	@Autowired
	OpenAiChatModel openAiChatModel;

	@Test
	void chatVoidInputFromBean() {
		var content = ChatClient.builder(this.openAiChatModel)
			.build()
			.prompt()
			.user("Welcome the users to the library")
			.tools(Tools.WELCOME)
			.call()
			.content();
		assertThat(content).isNotEmpty();
	}

	@Test
	void chatVoidInputFromCallback() {
		var content = ChatClient.builder(this.openAiChatModel)
			.build()
			.prompt()
			.user("Welcome the users to the library")
			.toolCallbacks(FunctionToolCallback.builder("sayWelcome", (input) -> {
						logger.info("CALLBACK - Welcoming users to the library");
					})
					.description("Welcome users to the library")
					.inputType(Void.class)
					.build())
			.call()
			.content();
		assertThat(content).isNotEmpty();
	}

	@Test
	void chatVoidOutputFromBean() {
		var content = ChatClient.builder(this.openAiChatModel)
			.build()
			.prompt()
			.user("Welcome %s to the library".formatted("James Bond"))
			.tools(Tools.WELCOME_USER)
			.call()
			.content();
		assertThat(content).isNotEmpty();
	}

	@Test
	void chatVoidOutputFromCallback() {
		var content = ChatClient.builder(this.openAiChatModel)
			.build()
			.prompt()
			.user("Welcome %s to the library".formatted("James Bond"))
			.toolCallbacks(FunctionToolCallback.builder("welcomeUser", (user) -> {
						logger.info("CALLBACK - Welcoming "+ ((User) user).name() +" to the library");
					})
					.description("Welcome a specific user to the library")
					.inputType(User.class)
					.build())
			.call()
			.content();
		assertThat(content).contains("Bond");
	}

	@Test
	void chatSingleFromBean() {
		var content = ChatClient.builder(this.openAiChatModel)
			.build()
			.prompt()
			.user("What books written by %s are available in the library?".formatted("J.R.R. Tolkien"))
			.tools(Tools.BOOKS_BY_AUTHOR)
			.call()
			.content();
		assertThat(content).isNotEmpty()
			.contains("The Hobbit")
			.contains("The Lord of The Rings")
			.contains("The Silmarillion");
	}

	@Test
	void chatSingleFromCallback() {
		Function<Author, List<Book>> function = author -> {
			logger.info("CALLBACK - Getting books by author: "+ author.name());
			return new BookService().getBooksByAuthor(author);
		};
		var content = ChatClient.builder(this.openAiChatModel)
			.build()
			.prompt()
			.user("What books written by %s are available in the library?".formatted("J.R.R. Tolkien"))
			.toolCallbacks(FunctionToolCallback.builder("availableBooksByAuthor", function)
				.description("Get the list of books written by the given author available in the library")
				.inputType(Author.class)
				.build())
			.call()
			.content();
		assertThat(content).isNotEmpty()
			.contains("The Hobbit")
			.contains("The Lord of The Rings")
			.contains("The Silmarillion");
	}

	@Test
	void chatListFromBean() {
		var content = ChatClient.builder(this.openAiChatModel)
			.build()
			.prompt()
			.user("What authors wrote the books %s and %s available in the library?".formatted("The Hobbit", "Narnia"))
			.tools(Tools.AUTHORS_BY_BOOKS)
			.call()
			.content();
		assertThat(content).isNotEmpty().contains("J.R.R. Tolkien").contains("C.S. Lewis");
	}

	@Test
	void chatListFromCallback() {
		Function<Books, List<Author>> function = books -> {
			logger.info("CALLBACK - Getting authors by books: "+ books.books().stream().map(Book::title).toList());
			return new BookService().getAuthorsByBook(books.books());
		};
		var content = ChatClient.builder(this.openAiChatModel)
			.build()
			.prompt()
			.user("What authors wrote the books %s and %s available in the library?".formatted("The Hobbit", "Narnia"))
			.toolCallbacks(FunctionToolCallback.builder("authorsByAvailableBooks", function)
				.description("Get the list of authors who wrote the given books available in the library")
				.inputType(Books.class)
				.build())
			.call()
			.content();
		assertThat(content).isNotEmpty().contains("J.R.R. Tolkien").contains("C.S. Lewis");
	}

	@Configuration(proxyBeanMethods = false)
	static class Tools {

		public static final String AUTHORS_BY_BOOKS = "authorsByBooks";

		public static final String BOOKS_BY_AUTHOR = "booksByAuthor";

		public static final String WELCOME = "welcome";

		public static final String WELCOME_USER = "welcomeUser";

		private static final LogAccessor logger = new LogAccessor(Tools.class);

		private final BookService bookService = new BookService();

		@Bean(WELCOME)
		@Description("Welcome users to the library")
		Consumer<Void> welcome() {
			return (input) -> logger.info("Welcoming users to the library");
		}

		@Bean(WELCOME_USER)
		@Description("Welcome a specific user to the library")
		Consumer<User> welcomeUser() {
			return user -> logger.info("Welcoming "+ user.name() +" to the library");
		}

		@Bean(BOOKS_BY_AUTHOR)
		@Description("Get the list of books written by the given author available in the library")
		Function<Author, List<Book>> booksByAuthor() {
			return author -> {
				logger.info("Getting books by author: "+ author.name());
				return bookService.getBooksByAuthor(author);
			};
		}

		@Bean(AUTHORS_BY_BOOKS)
		@Description("Get the list of authors who wrote the given books available in the library")
		Function<Books, List<Author>> authorsByBooks() {
			return books -> {
				logger.info("Getting authors by books: "+ books.books().stream().map(Book::title).toList());
				return bookService.getAuthorsByBook(books.books());
			};
		}

	}

	public record User(String name) {
	}

	public record Books(List<Book> books) {
	}

	// @formatter:on

}
