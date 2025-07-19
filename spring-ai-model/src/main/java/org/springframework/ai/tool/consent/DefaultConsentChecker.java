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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link ConsentChecker} that maintains an in-memory store of
 * granted consents.
 *
 * @author Assistant
 * @since 1.0.0
 */
public class DefaultConsentChecker implements ConsentChecker {

	private static final Logger logger = LoggerFactory.getLogger(DefaultConsentChecker.class);

	private final Set<String> grantedConsents = ConcurrentHashMap.newKeySet();

	private final ConsentRequestHandler consentRequestHandler;

	public DefaultConsentChecker(ConsentRequestHandler consentRequestHandler) {
		this.consentRequestHandler = consentRequestHandler;
	}

	@Override
	public boolean checkConsent(ConsentContext context) {
		String consentKey = buildConsentKey(context);

		// Check if consent already granted
		if (grantedConsents.contains(consentKey)) {
			logger.debug("Consent already granted for tool: {}", context.getToolName());
			return true;
		}

		// Request consent
		boolean consentGranted = consentRequestHandler.requestConsent(context);

		if (consentGranted) {
			// Store consent if granted
			if (context.getRequiresConsent() != null
					&& context.getRequiresConsent().level() != RequiresConsent.ConsentLevel.ONE_TIME) {
				grantedConsents.add(consentKey);
			}
			logger.info("Consent granted for tool: {}", context.getToolName());
		}
		else {
			logger.warn("Consent denied for tool: {}", context.getToolName());
		}

		return consentGranted;
	}

	private String buildConsentKey(ConsentContext context) {
		StringBuilder key = new StringBuilder();
		key.append(context.getToolName());

		if (context.getUserId() != null) {
			key.append(":").append(context.getUserId());
		}

		if (context.getSessionId() != null && context.getRequiresConsent() != null
				&& context.getRequiresConsent().level() == RequiresConsent.ConsentLevel.SESSION) {
			key.append(":").append(context.getSessionId());
		}

		return key.toString();
	}

	public void revokeConsent(String toolName, String userId) {
		String prefix = toolName + ":" + userId;
		grantedConsents.removeIf(key -> key.startsWith(prefix));
	}

	public void clearAllConsents() {
		grantedConsents.clear();
	}

	/**
	 * Interface for handling consent requests from users.
	 */
	@FunctionalInterface
	public interface ConsentRequestHandler {

		/**
		 * Request consent from the user.
		 * @param context the consent context
		 * @return true if consent is granted, false otherwise
		 */
		boolean requestConsent(ConsentContext context);

	}

}
