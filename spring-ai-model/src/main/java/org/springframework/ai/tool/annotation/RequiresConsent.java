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

package org.springframework.ai.tool.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to indicate that a tool method requires user consent before execution. When
 * applied to a method annotated with {@link Tool}, the execution will be intercepted to
 * request user approval before proceeding.
 *
 * <p>
 * Example usage:
 * <pre>{@code
 * @Tool(description = "Deletes a book from the database")
 * @RequiresConsent(message = "The book {bookId} will be permanently deleted. Do you approve?")
 * public void deleteBook(String bookId) {
 *     // Implementation
 * }
 * }</pre>
 *
 * @author Hyunjoon Park
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresConsent {

	/**
	 * The message to display when requesting consent. Supports placeholder syntax using
	 * curly braces (e.g., {paramName}) which will be replaced with actual parameter
	 * values at runtime.
	 * @return the consent message template
	 */
	String message() default "This action requires your approval. Do you want to proceed?";

	/**
	 * The level of consent required. This can be used to implement different consent
	 * strategies (e.g., one-time consent, session-based consent, etc.).
	 * @return the consent level
	 */
	ConsentLevel level() default ConsentLevel.EVERY_TIME;

	/**
	 * Optional categories for grouping consent requests. This can be used to manage
	 * consent preferences by category.
	 * @return array of consent categories
	 */
	String[] categories() default {};

	/**
	 * Defines the consent level for tool execution.
	 */
	enum ConsentLevel {

		/**
		 * Requires consent every time the tool is called.
		 */
		EVERY_TIME,

		/**
		 * Requires consent once per session.
		 */
		SESSION,

		/**
		 * Requires consent once and remembers the preference.
		 */
		REMEMBER

	}

}
