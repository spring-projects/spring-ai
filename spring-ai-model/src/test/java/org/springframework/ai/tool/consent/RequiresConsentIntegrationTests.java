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

package org.springframework.ai.tool.consent;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.annotation.RequiresConsent;
import org.springframework.ai.tool.annotation.RequiresConsent.ConsentLevel;
import org.springframework.ai.tool.annotation.Tool;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests demonstrating the use of @RequiresConsent with @Tool annotations.
 *
 * @author Hyunjoon Park
 */
class RequiresConsentIntegrationTests {

	private List<String> consentRequests;

	private ConsentManager consentManager;

	private BookService bookService;

	@BeforeEach
	void setUp() {
		consentRequests = new ArrayList<>();

		// Create a consent manager that logs requests and approves based on message
		// content
		consentManager = new DefaultConsentManager((message, params) -> {
			consentRequests.add(message);
			// Approve if message contains "approve" (case insensitive)
			return message.toLowerCase().contains("approve");
		});

		bookService = new BookService();
	}

	@Test
	void demonstrateRequiresConsentUsage() {
		// This test demonstrates how the @RequiresConsent annotation would be used
		// in practice with tool methods

		// The deleteBook method requires consent every time
		assertThat(bookService.getClass().getDeclaredMethods())
			.filteredOn(method -> method.getName().equals("deleteBook"))
			.hasSize(1)
			.allSatisfy(method -> {
				assertThat(method.isAnnotationPresent(Tool.class)).isTrue();
				assertThat(method.isAnnotationPresent(RequiresConsent.class)).isTrue();

				RequiresConsent consent = method.getAnnotation(RequiresConsent.class);
				assertThat(consent.message()).contains("{bookId}");
				assertThat(consent.level()).isEqualTo(ConsentLevel.EVERY_TIME);
			});

		// The updateBookPrice method requires session-level consent
		assertThat(bookService.getClass().getDeclaredMethods())
			.filteredOn(method -> method.getName().equals("updateBookPrice"))
			.hasSize(1)
			.allSatisfy(method -> {
				assertThat(method.isAnnotationPresent(Tool.class)).isTrue();
				assertThat(method.isAnnotationPresent(RequiresConsent.class)).isTrue();

				RequiresConsent consent = method.getAnnotation(RequiresConsent.class);
				assertThat(consent.level()).isEqualTo(ConsentLevel.SESSION);
				assertThat(consent.categories()).contains("financial");
			});
	}

	/**
	 * Example service class demonstrating @RequiresConsent usage.
	 */
	static class BookService {

		@Tool(description = "Deletes a book from the database")
		@RequiresConsent(message = "The book with ID {bookId} will be permanently deleted. Do you approve?",
				level = ConsentLevel.EVERY_TIME)
		public String deleteBook(String bookId) {
			return "Book " + bookId + " deleted";
		}

		@Tool(description = "Updates the price of a book")
		@RequiresConsent(message = "Update book {bookId} price from ${oldPrice} to ${newPrice}? Please approve.",
				level = ConsentLevel.SESSION, categories = { "financial", "data-modification" })
		public String updateBookPrice(String bookId, double oldPrice, double newPrice) {
			return String.format("Book %s price updated from %.2f to %.2f", bookId, oldPrice, newPrice);
		}

		@Tool(description = "Gets book information")
		// No @RequiresConsent - this operation doesn't need consent
		public String getBook(String bookId) {
			return "Book details for " + bookId;
		}

		@Tool(description = "Sends book recommendation email")
		@RequiresConsent(message = "Send book recommendations to {email}? (We'll remember your preference)",
				level = ConsentLevel.REMEMBER, categories = { "communication", "marketing" })
		public String sendBookRecommendations(String email) {
			return "Recommendations sent to " + email;
		}

	}

}
