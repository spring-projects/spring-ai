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

package org.springframework.ai.tool.resolution

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.ai.template.NoOpTemplateRenderer
import org.springframework.ai.util.json.schema.SchemaType
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Description
import org.springframework.context.support.GenericApplicationContext
import java.util.function.Consumer
import java.util.function.Function

/**
 * Unit tests for {@link SpringBeanToolCallbackResolver}.
 *
 * @author Thomas Vitale
 */
class SpringBeanToolCallbackResolverKotlinTests {

	@Test
	fun whenToolCallbackWithVoidConsumerIsResolvedThenReturnIt() {
		val applicationContext: GenericApplicationContext = AnnotationConfigApplicationContext(Functions::class.java)
		val resolver = SpringBeanToolCallbackResolver(applicationContext, SchemaType.JSON_SCHEMA)
		val resolvedToolCallback = resolver.resolve(Functions.WELCOME_TOOL_NAME)
		Assertions.assertThat(resolvedToolCallback).isNotNull()
		Assertions.assertThat(resolvedToolCallback!!.toolDefinition.name()).isEqualTo(Functions.WELCOME_TOOL_NAME)
		Assertions.assertThat(resolvedToolCallback.toolDefinition.description())
			.isEqualTo(Functions.WELCOME_TOOL_DESCRIPTION)
	}

	@Test
	fun whenToolCallbackWithConsumerIsResolvedThenReturnIt() {
		val applicationContext: GenericApplicationContext = AnnotationConfigApplicationContext(Functions::class.java)
		val resolver = SpringBeanToolCallbackResolver(applicationContext, SchemaType.JSON_SCHEMA)
		val resolvedToolCallback = resolver.resolve(Functions.WELCOME_USER_TOOL_NAME)
		Assertions.assertThat(resolvedToolCallback).isNotNull()
		Assertions.assertThat(resolvedToolCallback!!.toolDefinition.name()).isEqualTo(Functions.WELCOME_USER_TOOL_NAME)
		Assertions.assertThat(resolvedToolCallback.toolDefinition.description())
			.isEqualTo(Functions.WELCOME_USER_TOOL_DESCRIPTION)
	}

	@Test
	fun whenToolCallbackWithFunctionIsResolvedThenReturnIt() {
		val applicationContext: GenericApplicationContext = AnnotationConfigApplicationContext(Functions::class.java)
		val resolver = SpringBeanToolCallbackResolver(applicationContext, SchemaType.JSON_SCHEMA)
		val resolvedToolCallback = resolver.resolve(Functions.BOOKS_BY_AUTHOR_TOOL_NAME)
		Assertions.assertThat(resolvedToolCallback).isNotNull()
		Assertions.assertThat(resolvedToolCallback!!.toolDefinition.name()).isEqualTo(Functions.BOOKS_BY_AUTHOR_TOOL_NAME)
		Assertions.assertThat(resolvedToolCallback.toolDefinition.description())
			.isEqualTo(Functions.BOOKS_BY_AUTHOR_TOOL_DESCRIPTION)
	}

	@Configuration(proxyBeanMethods = false)
	open class Functions {

		@Bean(WELCOME_TOOL_NAME)
		@Description(WELCOME_TOOL_DESCRIPTION)
		open fun welcome(): Consumer<Void> {
			return Consumer { _: Void? -> }
		}

		@Bean(WELCOME_USER_TOOL_NAME)
		@Description(WELCOME_USER_TOOL_DESCRIPTION)
		open fun welcomeUser(): Consumer<User> {
			return Consumer { _: User? -> }
		}

		@Bean(BOOKS_BY_AUTHOR_TOOL_NAME)
		@Description(BOOKS_BY_AUTHOR_TOOL_DESCRIPTION)
		open fun booksByAuthor(): Function<Author, List<Book>> {
			return Function { author: Author ->
				java.util.List.of(
					Book("Book 1", author.name),
					Book("Book 2", author.name)
				)
			}
		}

		data class User(val name: String)

		data class Author(val name: String)

		data class Book(val title: String, val author: String)

		companion object {
			const val BOOKS_BY_AUTHOR_TOOL_NAME: String = "booksByAuthor"
			const val BOOKS_BY_AUTHOR_TOOL_DESCRIPTION: String = "Get the list of books written by the given author available in the library"

			const val WELCOME_TOOL_NAME: String = "welcome"
			const val WELCOME_TOOL_DESCRIPTION: String = "Welcome users to the library"

			const val WELCOME_USER_TOOL_NAME: String = "welcomeUser"
			const val WELCOME_USER_TOOL_DESCRIPTION: String = "Welcome a specific user to the library"
		}
	}

}
