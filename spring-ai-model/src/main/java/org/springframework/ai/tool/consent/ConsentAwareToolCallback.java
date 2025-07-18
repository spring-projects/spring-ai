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

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.RequiresConsent;
import org.springframework.ai.tool.consent.exception.ConsentDeniedException;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.Assert;

/**
 * A decorator for {@link ToolCallback} that enforces consent requirements before
 * delegating to the actual tool implementation.
 *
 * @author Hyunjoon Park
 * @since 1.0.0
 */
public class ConsentAwareToolCallback implements ToolCallback {

	private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");

	private final ToolCallback delegate;

	private final ConsentManager consentManager;

	private final RequiresConsent requiresConsent;

	/**
	 * Creates a new consent-aware tool callback.
	 * @param delegate the actual tool callback to delegate to
	 * @param consentManager the consent manager for handling consent requests
	 * @param requiresConsent the consent requirements annotation
	 */
	public ConsentAwareToolCallback(ToolCallback delegate, ConsentManager consentManager,
			RequiresConsent requiresConsent) {
		Assert.notNull(delegate, "delegate must not be null");
		Assert.notNull(consentManager, "consentManager must not be null");
		Assert.notNull(requiresConsent, "requiresConsent must not be null");
		this.delegate = delegate;
		this.consentManager = consentManager;
		this.requiresConsent = requiresConsent;
	}

	@Override
	public Object call(Map<String, Object> parameters) {
		String toolName = getName();

		// Check if consent was already granted based on consent level
		if (this.consentManager.hasValidConsent(toolName, this.requiresConsent.level(),
				this.requiresConsent.categories())) {
			return this.delegate.call(parameters);
		}

		// Prepare consent message with parameter substitution
		String message = prepareConsentMessage(this.requiresConsent.message(), parameters);

		// Request consent
		boolean consentGranted = this.consentManager.requestConsent(toolName, message, this.requiresConsent.level(),
				this.requiresConsent.categories(), parameters);

		if (!consentGranted) {
			throw new ConsentDeniedException(String.format("User denied consent for tool '%s' execution", toolName));
		}

		// Execute the tool if consent was granted
		return this.delegate.call(parameters);
	}

	@Override
	public String getName() {
		return this.delegate.getName();
	}

	@Override
	public String getDescription() {
		return this.delegate.getDescription();
	}

	@Override
	public ToolDefinition getToolDefinition() {
		return this.delegate.getToolDefinition();
	}

	/**
	 * Prepares the consent message by replacing placeholders with actual parameter
	 * values.
	 * @param template the message template with placeholders
	 * @param parameters the parameters to substitute
	 * @return the prepared message
	 */
	private String prepareConsentMessage(String template, Map<String, Object> parameters) {
		if (parameters == null || parameters.isEmpty()) {
			return template;
		}

		Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
		StringBuffer result = new StringBuffer();

		while (matcher.find()) {
			String paramName = matcher.group(1);
			Object value = parameters.get(paramName);
			String replacement = value != null ? String.valueOf(value) : "{" + paramName + "}";
			matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(result);

		return result.toString();
	}

	/**
	 * Returns the underlying delegate tool callback.
	 * @return the delegate tool callback
	 */
	public ToolCallback getDelegate() {
		return this.delegate;
	}

	/**
	 * Returns the consent requirements for this tool.
	 * @return the consent requirements
	 */
	public RequiresConsent getRequiresConsent() {
		return this.requiresConsent;
	}

}
