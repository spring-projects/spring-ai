/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.chat.client.advisor.governance;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PiiScanner}.
 *
 * @author Spring AI Contributors
 */
class PiiScannerTests {

	private final PiiScanner scanner = PiiScanner.builder().build();

	// -------------------------------------------------------------------------
	// SSN
	// -------------------------------------------------------------------------

	@Test
	void detectsSsn() {
		assertThat(this.scanner.containsPii("My SSN is 123-45-6789")).isTrue();
	}

	@Test
	void detectsSsnDescription() {
		assertThat(this.scanner.firstMatchDescription("SSN 123-45-6789")).isEqualTo("SSN");
	}

	@Test
	void doesNotFlagNonSsn() {
		// Phone-like pattern that does not match the SSN regex
		assertThat(this.scanner.containsPii("Call me on 800-123-4567")).isFalse();
	}

	// -------------------------------------------------------------------------
	// Credit card
	// -------------------------------------------------------------------------

	@Test
	void detectsCreditCardWithSpaces() {
		assertThat(this.scanner.containsPii("Card: 4111 1111 1111 1111")).isTrue();
	}

	@Test
	void detectsCreditCardWithDashes() {
		assertThat(this.scanner.containsPii("Card: 4111-1111-1111-1111")).isTrue();
	}

	@Test
	void detectsCreditCardContinuous() {
		assertThat(this.scanner.containsPii("pay with 4111111111111111 now")).isTrue();
	}

	// -------------------------------------------------------------------------
	// E-mail
	// -------------------------------------------------------------------------

	@Test
	void detectsEmail() {
		assertThat(this.scanner.containsPii("Contact user@example.com for details")).isTrue();
	}

	@Test
	void detectsEmailDescription() {
		assertThat(this.scanner.firstMatchDescription("email: alice@example.org")).isEqualTo("email");
	}

	// -------------------------------------------------------------------------
	// API key / Bearer token
	// -------------------------------------------------------------------------

	@Test
	void detectsBearerToken() {
		assertThat(this.scanner.containsPii("Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9")).isTrue();
	}

	@Test
	void detectsApiKeyAssignment() {
		assertThat(this.scanner.containsPii("api_key=sk-abcdefghijklmnopqrstuvwxyz0123456789")).isTrue();
	}

	// -------------------------------------------------------------------------
	// Clean prompts
	// -------------------------------------------------------------------------

	@Test
	void allowsCleanPrompt() {
		assertThat(this.scanner.containsPii("Explain Spring Boot security best practices")).isFalse();
	}

	@Test
	void nullTextIsClean() {
		assertThat(this.scanner.containsPii(null)).isFalse();
	}

	@Test
	void blankTextIsClean() {
		assertThat(this.scanner.containsPii("   ")).isFalse();
	}

	@Test
	void firstMatchDescriptionNullForCleanText() {
		assertThat(this.scanner.firstMatchDescription("hello world")).isNull();
	}

	// -------------------------------------------------------------------------
	// Disabling built-in checks
	// -------------------------------------------------------------------------

	@Test
	void ssnCheckCanBeDisabled() {
		PiiScanner noSsn = PiiScanner.builder().ssnEnabled(false).build();
		assertThat(noSsn.containsPii("SSN 123-45-6789")).isFalse();
	}

	@Test
	void emailCheckCanBeDisabled() {
		PiiScanner noEmail = PiiScanner.builder().emailEnabled(false).build();
		assertThat(noEmail.containsPii("user@example.com")).isFalse();
	}

	// -------------------------------------------------------------------------
	// Extra patterns
	// -------------------------------------------------------------------------

	@Test
	void customPatternIsApplied() {
		PiiScanner custom = PiiScanner.builder()
			.ssnEnabled(false)
			.creditCardEnabled(false)
			.emailEnabled(false)
			.apiKeyEnabled(false)
			.extraPatterns(List.of(Pattern.compile("SECRET_WORD")))
			.build();
		assertThat(custom.containsPii("do not share SECRET_WORD with anyone")).isTrue();
		assertThat(custom.firstMatchDescription("SECRET_WORD found")).isEqualTo("custom pattern");
	}

}
