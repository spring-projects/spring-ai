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

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.tool.annotation.RequiresConsent.ConsentLevel;

/**
 * Default implementation of {@link ConsentManager} that uses a configurable consent
 * handler function and stores consent decisions in memory.
 *
 * @author Hyunjoon Park
 * @since 1.0.0
 */
public class DefaultConsentManager implements ConsentManager {

	private static final Logger logger = LoggerFactory.getLogger(DefaultConsentManager.class);

	private final BiFunction<String, Map<String, Object>, Boolean> consentHandler;

	private final Map<String, ConsentRecord> consentStore = new ConcurrentHashMap<>();

	/**
	 * Creates a default consent manager with the given consent handler.
	 * @param consentHandler function that takes a consent message and parameters, returns
	 * true if consent is granted
	 */
	public DefaultConsentManager(BiFunction<String, Map<String, Object>, Boolean> consentHandler) {
		this.consentHandler = consentHandler;
	}

	/**
	 * Creates a default consent manager that always denies consent. Useful for testing or
	 * when consent should be managed externally.
	 */
	public DefaultConsentManager() {
		this((message, params) -> {
			logger.warn("No consent handler configured. Denying consent for: {}", message);
			return false;
		});
	}

	@Override
	public boolean requestConsent(String toolName, String message, ConsentLevel level, String[] categories,
			Map<String, Object> parameters) {

		logger.debug("Requesting consent for tool '{}' with message: {}", toolName, message);

		// Check if we already have valid consent
		if (hasValidConsent(toolName, level, categories)) {
			logger.debug("Valid consent already exists for tool '{}'", toolName);
			return true;
		}

		// Request new consent
		boolean granted = this.consentHandler.apply(message, parameters);

		if (granted) {
			// Store consent decision based on level
			if (level != ConsentLevel.EVERY_TIME) {
				String key = createConsentKey(toolName, categories);
				this.consentStore.put(key, new ConsentRecord(level, System.currentTimeMillis()));
				logger.debug("Stored consent for tool '{}' with level '{}'", toolName, level);
			}
		}

		return granted;
	}

	@Override
	public boolean hasValidConsent(String toolName, ConsentLevel level, String[] categories) {
		if (level == ConsentLevel.EVERY_TIME) {
			return false; // Always require new consent
		}

		String key = createConsentKey(toolName, categories);
		ConsentRecord record = this.consentStore.get(key);

		if (record == null) {
			return false;
		}

		// For REMEMBER level, consent is valid indefinitely
		// For SESSION level, we could implement session timeout logic here
		return true;
	}

	@Override
	public void revokeConsent(String toolName, String[] categories) {
		if (categories == null || categories.length == 0) {
			// Revoke all consents for this tool
			this.consentStore.entrySet().removeIf(entry -> entry.getKey().startsWith(toolName + ":"));
		}
		else {
			// Revoke specific category consent
			String key = createConsentKey(toolName, categories);
			this.consentStore.remove(key);
		}
		logger.debug("Revoked consent for tool '{}'", toolName);
	}

	@Override
	public void clearAllConsents() {
		this.consentStore.clear();
		logger.debug("Cleared all stored consents");
	}

	/**
	 * Creates a unique key for storing consent based on tool name and categories.
	 */
	private String createConsentKey(String toolName, String[] categories) {
		if (categories == null || categories.length == 0) {
			return toolName + ":default";
		}
		String sortedCategories = Arrays.stream(categories).sorted().reduce((a, b) -> a + "," + b).orElse("");
		return toolName + ":" + sortedCategories;
	}

	/**
	 * Internal record for storing consent information.
	 */
	private static class ConsentRecord {

		final ConsentLevel level;

		final long timestamp;

		ConsentRecord(ConsentLevel level, long timestamp) {
			this.level = level;
			this.timestamp = timestamp;
		}

	}

}
