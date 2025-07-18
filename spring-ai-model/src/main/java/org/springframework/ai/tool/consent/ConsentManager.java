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

import org.springframework.ai.tool.annotation.RequiresConsent.ConsentLevel;

/**
 * Strategy interface for managing user consent for tool execution. Implementations can
 * provide different consent mechanisms such as UI-based prompts, API calls, or
 * configuration-based consent.
 *
 * @author Hyunjoon Park
 * @since 1.0.0
 */
public interface ConsentManager {

	/**
	 * Requests consent for executing a tool with the given parameters.
	 * @param toolName the name of the tool requiring consent
	 * @param message the consent message (may contain placeholders)
	 * @param level the consent level required
	 * @param categories the categories associated with this consent request
	 * @param parameters the actual parameters that will be passed to the tool
	 * @return {@code true} if consent is granted, {@code false} otherwise
	 */
	boolean requestConsent(String toolName, String message, ConsentLevel level, String[] categories,
			Map<String, Object> parameters);

	/**
	 * Checks if consent has already been granted for a tool based on the consent level.
	 * This method is called before {@link #requestConsent} to avoid unnecessary prompts.
	 * @param toolName the name of the tool
	 * @param level the consent level
	 * @param categories the categories associated with this tool
	 * @return {@code true} if consent was previously granted and is still valid
	 */
	boolean hasValidConsent(String toolName, ConsentLevel level, String[] categories);

	/**
	 * Revokes any stored consent for the specified tool.
	 * @param toolName the name of the tool
	 * @param categories the categories to revoke consent for (empty to revoke all)
	 */
	void revokeConsent(String toolName, String[] categories);

	/**
	 * Clears all stored consents. Typically called at session end or on user request.
	 */
	void clearAllConsents();

}
