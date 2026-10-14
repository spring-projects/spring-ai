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

package org.springframework.ai.model.tool;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * A caller's decision on one tool call that waits for confirmation: allow it to run, or
 * deny it with a message that the model receives as the call's result.
 *
 * @param toolCallId the id of the tool call the decision is about
 * @param allowed whether the call may run
 * @param message for a denied call, the result the model receives; {@code null} for the
 * {@linkplain #DEFAULT_DENY_MESSAGE default}, never blank
 * @author Christian Tzolov
 * @since 2.1.0
 * @see ToolCallDecisions
 */
public record ToolCallDecision(String toolCallId, boolean allowed, @Nullable String message) {

	/** The result a denied call answers the model with when the decision gives none. */
	public static final String DEFAULT_DENY_MESSAGE = "The user did not allow this tool call.";

	public ToolCallDecision {
		Assert.hasText(toolCallId, "toolCallId must not be empty");
		Assert.isTrue(message == null || !message.isBlank(),
				"message must not be blank; leave it null for the default deny message");
	}

	/** Allows the tool call to run. */
	public static ToolCallDecision allow(String toolCallId) {
		return new ToolCallDecision(toolCallId, true, null);
	}

	/**
	 * Denies the tool call; the model receives the {@linkplain #DEFAULT_DENY_MESSAGE
	 * default} message.
	 */
	public static ToolCallDecision deny(String toolCallId) {
		return new ToolCallDecision(toolCallId, false, null);
	}

	/** Denies the tool call; the model receives the message as the call's result. */
	public static ToolCallDecision deny(String toolCallId, String message) {
		return new ToolCallDecision(toolCallId, false, message);
	}

	/** The result a denied call answers the model with. */
	public String denyMessage() {
		return (this.message != null) ? this.message : DEFAULT_DENY_MESSAGE;
	}

}
