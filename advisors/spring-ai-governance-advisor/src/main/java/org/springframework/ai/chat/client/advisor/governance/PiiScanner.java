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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Scans text for common personally identifiable information (PII) patterns.
 *
 * <p>
 * The default scanner recognises:
 * <ul>
 * <li>US Social Security Numbers — e.g. {@code 123-45-6789}</li>
 * <li>Credit / debit card numbers — groups of 4 digits separated by spaces or dashes,
 * e.g. {@code 4111 1111 1111 1111}</li>
 * <li>E-mail addresses</li>
 * <li>Bearer / API tokens in common header formats — e.g.
 * {@code Authorization: Bearer eyJ…}</li>
 * </ul>
 *
 * <p>
 * Additional patterns can be supplied via {@link Builder#extraPatterns(List)}. Built-in
 * checks can be disabled individually through the builder.
 *
 * <p>
 * This scanner performs regex-based heuristics and is intended as a <em>first line of
 * defence</em>, not a complete PII detection solution. It does not handle obfuscated
 * input (homoglyphs, zero-width characters, etc.).
 *
 * @author Spring AI Contributors
 * @since 2.0.0
 */
public final class PiiScanner {

	// -------------------------------------------------------------------------
	// Built-in patterns
	// -------------------------------------------------------------------------

	/** US Social Security Number: 3-2-4 digit groups separated by hyphens. */
	static final Pattern SSN_PATTERN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");

	/**
	 * Credit / debit card: four groups of four digits with space or hyphen separators, or
	 * a continuous 16-digit string.
	 */
	static final Pattern CREDIT_CARD_PATTERN = Pattern.compile("\\b(?:\\d{4}[- ]){3}\\d{4}\\b|\\b\\d{16}\\b");

	/** E-mail address. */
	static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}\\b");

	/** API key / Bearer token appearing in a header-style context. */
	static final Pattern API_KEY_PATTERN = Pattern
		.compile("(?i)\\b(?:Authorization\\s*:\\s*Bearer\\s+|api[_\\-]?key\\s*[=:]\\s*)[A-Za-z0-9\\-._~+/]{16,}");

	// -------------------------------------------------------------------------
	// Fields
	// -------------------------------------------------------------------------

	private final boolean ssnEnabled;

	private final boolean creditCardEnabled;

	private final boolean emailEnabled;

	private final boolean apiKeyEnabled;

	private final List<Pattern> extraPatterns;

	// -------------------------------------------------------------------------
	// Constructor (package-private, use Builder)
	// -------------------------------------------------------------------------

	private PiiScanner(boolean ssnEnabled, boolean creditCardEnabled, boolean emailEnabled, boolean apiKeyEnabled,
			List<Pattern> extraPatterns) {
		this.ssnEnabled = ssnEnabled;
		this.creditCardEnabled = creditCardEnabled;
		this.emailEnabled = emailEnabled;
		this.apiKeyEnabled = apiKeyEnabled;
		this.extraPatterns = List.copyOf(extraPatterns);
	}

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	/**
	 * Returns {@code true} when any enabled PII pattern matches inside {@code text}.
	 * @param text the text to inspect; may be {@code null} (returns {@code false})
	 */
	public boolean containsPii(@Nullable String text) {
		if (text == null || text.isBlank()) {
			return false;
		}
		return (this.ssnEnabled && SSN_PATTERN.matcher(text).find())
				|| (this.creditCardEnabled && CREDIT_CARD_PATTERN.matcher(text).find())
				|| (this.emailEnabled && EMAIL_PATTERN.matcher(text).find())
				|| (this.apiKeyEnabled && API_KEY_PATTERN.matcher(text).find())
				|| this.extraPatterns.stream().anyMatch(p -> p.matcher(text).find());
	}

	/**
	 * Returns a human-readable description of the first PII type detected in
	 * {@code text}, or {@code null} when nothing is detected.
	 * @param text the text to inspect; may be {@code null}
	 * @return a description like {@code "SSN"}, {@code "credit card"}, {@code "email"},
	 * {@code "API key/token"}, or {@code "custom pattern"}, or {@code null}
	 */
	public @Nullable String firstMatchDescription(@Nullable String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		if (this.ssnEnabled && SSN_PATTERN.matcher(text).find()) {
			return "SSN";
		}
		if (this.creditCardEnabled && CREDIT_CARD_PATTERN.matcher(text).find()) {
			return "credit card";
		}
		if (this.emailEnabled && EMAIL_PATTERN.matcher(text).find()) {
			return "email";
		}
		if (this.apiKeyEnabled && API_KEY_PATTERN.matcher(text).find()) {
			return "API key/token";
		}
		if (this.extraPatterns.stream().anyMatch(p -> p.matcher(text).find())) {
			return "custom pattern";
		}
		return null;
	}

	// -------------------------------------------------------------------------
	// Builder
	// -------------------------------------------------------------------------

	/**
	 * Returns a new {@link Builder} with all built-in checks enabled.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link PiiScanner}.
	 */
	public static final class Builder {

		private boolean ssnEnabled = true;

		private boolean creditCardEnabled = true;

		private boolean emailEnabled = true;

		private boolean apiKeyEnabled = true;

		private final List<Pattern> extraPatterns = new ArrayList<>();

		private Builder() {
		}

		/**
		 * Enables or disables SSN detection (default: {@code true}).
		 */
		public Builder ssnEnabled(boolean ssnEnabled) {
			this.ssnEnabled = ssnEnabled;
			return this;
		}

		/**
		 * Enables or disables credit/debit card detection (default: {@code true}).
		 */
		public Builder creditCardEnabled(boolean creditCardEnabled) {
			this.creditCardEnabled = creditCardEnabled;
			return this;
		}

		/**
		 * Enables or disables e-mail detection (default: {@code true}).
		 */
		public Builder emailEnabled(boolean emailEnabled) {
			this.emailEnabled = emailEnabled;
			return this;
		}

		/**
		 * Enables or disables API key / Bearer token detection (default: {@code true}).
		 */
		public Builder apiKeyEnabled(boolean apiKeyEnabled) {
			this.apiKeyEnabled = apiKeyEnabled;
			return this;
		}

		/**
		 * Appends additional compiled {@link Pattern}s to scan for. All patterns are
		 * always applied after the built-in ones.
		 * @param patterns the extra patterns; must not be {@code null}
		 */
		public Builder extraPatterns(List<Pattern> patterns) {
			Assert.notNull(patterns, "patterns must not be null");
			this.extraPatterns.addAll(patterns);
			return this;
		}

		/**
		 * Builds the {@link PiiScanner}.
		 */
		public PiiScanner build() {
			return new PiiScanner(this.ssnEnabled, this.creditCardEnabled, this.emailEnabled, this.apiKeyEnabled,
					this.extraPatterns);
		}

	}

}
