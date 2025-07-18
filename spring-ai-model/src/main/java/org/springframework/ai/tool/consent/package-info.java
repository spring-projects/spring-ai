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

/**
 * Consent management framework for Spring AI tool execution.
 *
 * <p>
 * This package provides a comprehensive consent management system that allows tools to
 * require user approval before execution. Key components include:
 *
 * <ul>
 * <li>{@link org.springframework.ai.tool.annotation.RequiresConsent} - Annotation to mark
 * tool methods that require consent</li>
 * <li>{@link org.springframework.ai.tool.consent.ConsentManager} - Strategy interface for
 * managing consent requests and decisions</li>
 * <li>{@link org.springframework.ai.tool.consent.ConsentAwareToolCallback} - Decorator
 * that enforces consent requirements before tool execution</li>
 * <li>{@link org.springframework.ai.tool.consent.DefaultConsentManager} - Default
 * implementation with configurable consent handlers</li>
 * </ul>
 *
 * <p>
 * Example usage: <pre>{@code
 * &#64;Tool(description = "Deletes a record")
 * &#64;RequiresConsent(
 *     message = "Delete record {id}? This cannot be undone.",
 *     level = ConsentLevel.EVERY_TIME
 * )
 * public void deleteRecord(String id) {
 *     // Implementation
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
package org.springframework.ai.tool.consent;
