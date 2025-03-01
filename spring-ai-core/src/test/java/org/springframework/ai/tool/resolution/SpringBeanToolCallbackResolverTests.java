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

package org.springframework.ai.tool.resolution;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.util.json.schema.SchemaType;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.context.support.GenericApplicationContext;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SpringBeanToolCallbackResolver}.
 *
 * @author Thomas Vitale
 */
class SpringBeanToolCallbackResolverTests {

	@Test
	void whenApplicationContextIsNullThenThrow() {
		assertThatThrownBy(() -> new SpringBeanToolCallbackResolver(null, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("applicationContext cannot be null");

		assertThatThrownBy(() -> SpringBeanToolCallbackResolver.builder().build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("applicationContext cannot be null");
	}

	@Test
	void whenSchemaTypeIsNullThenUseDefault() {
		SpringBeanToolCallbackResolver resolver = new SpringBeanToolCallbackResolver(new GenericApplicationContext(),
				null);
		assertThat(resolver.getSchemaType()).isEqualTo(SchemaType.JSON_SCHEMA);

		SpringBeanToolCallbackResolver resolver2 = SpringBeanToolCallbackResolver.builder()
			.applicationContext(new GenericApplicationContext())
			.build();
		assertThat(resolver2.getSchemaType()).isEqualTo(SchemaType.JSON_SCHEMA);
	}

	@Test
	void whenSchemaTypeIsNotNullThenUseIt() {
		SchemaType schemaType = SchemaType.OPEN_API_SCHEMA;
		SpringBeanToolCallbackResolver resolver = new SpringBeanToolCallbackResolver(new GenericApplicationContext(),
				schemaType);
		assertThat(resolver.getSchemaType()).isEqualTo(schemaType);

		SpringBeanToolCallbackResolver resolver2 = SpringBeanToolCallbackResolver.builder()
			.applicationContext(new GenericApplicationContext())
			.schemaType(schemaType)
			.build();
		assertThat(resolver2.getSchemaType()).isEqualTo(schemaType);
	}

	@Test
	void whenRequiredArgumentsAreProvidedThenCreateInstance() {
		GenericApplicationContext applicationContext = new GenericApplicationContext();
		SchemaType schemaType = SchemaType.OPEN_API_SCHEMA;
		SpringBeanToolCallbackResolver resolver = new SpringBeanToolCallbackResolver(applicationContext, schemaType);
		assertThat(resolver).isNotNull();

		SpringBeanToolCallbackResolver resolver2 = SpringBeanToolCallbackResolver.builder()
			.applicationContext(applicationContext)
			.schemaType(schemaType)
			.build();
		assertThat(resolver2).isNotNull();
	}

	@Test
	void whenToolCallbackWithVoidConsumerIsResolvedThenReturnIt() {
		GenericApplicationContext applicationContext = new AnnotationConfigApplicationContext(Functions.class);
		SpringBeanToolCallbackResolver resolver = new SpringBeanToolCallbackResolver(applicationContext,
				SchemaType.JSON_SCHEMA);
		ToolCallback resolvedToolCallback = resolver.resolve(Functions.WELCOME_TOOL_NAME);
		assertThat(resolvedToolCallback).isNotNull();
		assertThat(resolvedToolCallback.getToolDefinition().name()).isEqualTo(Functions.WELCOME_TOOL_NAME);
		assertThat(resolvedToolCallback.getToolDefinition().description())
			.isEqualTo(Functions.WELCOME_TOOL_DESCRIPTION);
	}

	@Test
	void whenToolCallbackWithConsumerIsResolvedThenReturnIt() {
		GenericApplicationContext applicationContext = new AnnotationConfigApplicationContext(Functions.class);
		SpringBeanToolCallbackResolver resolver = new SpringBeanToolCallbackResolver(applicationContext,
				SchemaType.JSON_SCHEMA);
		ToolCallback resolvedToolCallback = resolver.resolve(Functions.WELCOME_USER_TOOL_NAME);
		assertThat(resolvedToolCallback).isNotNull();
		assertThat(resolvedToolCallback.getToolDefinition().name()).isEqualTo(Functions.WELCOME_USER_TOOL_NAME);
		assertThat(resolvedToolCallback.getToolDefinition().description())
			.isEqualTo(Functions.WELCOME_USER_TOOL_DESCRIPTION);
	}

	@Test
	void whenToolCallbackWithFunctionIsResolvedThenReturnIt() {
		GenericApplicationContext applicationContext = new AnnotationConfigApplicationContext(Functions.class);
		SpringBeanToolCallbackResolver resolver = new SpringBeanToolCallbackResolver(applicationContext,
				SchemaType.JSON_SCHEMA);
		ToolCallback resolvedToolCallback = resolver.resolve(Functions.BOOKS_BY_AUTHOR_TOOL_NAME);
		assertThat(resolvedToolCallback).isNotNull();
		assertThat(resolvedToolCallback.getToolDefinition().name()).isEqualTo(Functions.BOOKS_BY_AUTHOR_TOOL_NAME);
		assertThat(resolvedToolCallback.getToolDefinition().description())
			.isEqualTo(Functions.BOOKS_BY_AUTHOR_TOOL_DESCRIPTION);
	}

	@Configuration(proxyBeanMethods = false)
	static class Functions {

		public static final String BOOKS_BY_AUTHOR_TOOL_NAME = "booksByAuthor";

		public static final String BOOKS_BY_AUTHOR_TOOL_DESCRIPTION = "Get the list of books written by the given author available in the library";

		public static final String WELCOME_TOOL_NAME = "welcome";

		public static final String WELCOME_TOOL_DESCRIPTION = "Welcome users to the library";

		public static final String WELCOME_USER_TOOL_NAME = "welcomeUser";

		public static final String WELCOME_USER_TOOL_DESCRIPTION = "Welcome a specific user to the library";

		@Bean(WELCOME_TOOL_NAME)
		@Description(WELCOME_TOOL_DESCRIPTION)
		Consumer<Void> welcome() {
			return (input) -> {
			};
		}

		@Bean(WELCOME_USER_TOOL_NAME)
		@Description(WELCOME_USER_TOOL_DESCRIPTION)
		Consumer<User> welcomeUser() {
			return user -> {
			};
		}

		@Bean(BOOKS_BY_AUTHOR_TOOL_NAME)
		@Description(BOOKS_BY_AUTHOR_TOOL_DESCRIPTION)
		Function<Author, List<Book>> booksByAuthor() {
			return author -> List.of(new Book("Book 1", author.name()), new Book("Book 2", author.name()));
		}

		public record User(String name) {
		}

		public record Author(String name) {
		}

		public record Book(String title, String author) {
		}

	}

}
