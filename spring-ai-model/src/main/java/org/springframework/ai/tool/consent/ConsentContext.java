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

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.RequiresConsent;
import org.springframework.lang.Nullable;

/**
 * Context information for consent checking. Contains all relevant information needed to
 * make consent decisions.
 *
 * @author Assistant
 * @since 1.0.0
 */
public class ConsentContext {

	private final String toolName;

	private final String toolInput;

	@Nullable
	private final ToolContext toolContext;

	@Nullable
	private final RequiresConsent requiresConsent;

	@Nullable
	private final String userId;

	@Nullable
	private final String sessionId;

	private ConsentContext(Builder builder) {
		this.toolName = builder.toolName;
		this.toolInput = builder.toolInput;
		this.toolContext = builder.toolContext;
		this.requiresConsent = builder.requiresConsent;
		this.userId = builder.userId;
		this.sessionId = builder.sessionId;
	}

	public String getToolName() {
		return toolName;
	}

	public String getToolInput() {
		return toolInput;
	}

	@Nullable
	public ToolContext getToolContext() {
		return toolContext;
	}

	@Nullable
	public RequiresConsent getRequiresConsent() {
		return requiresConsent;
	}

	@Nullable
	public String getUserId() {
		return userId;
	}

	@Nullable
	public String getSessionId() {
		return sessionId;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private String toolName;

		private String toolInput;

		private ToolContext toolContext;

		private RequiresConsent requiresConsent;

		private String userId;

		private String sessionId;

		public Builder toolName(String toolName) {
			this.toolName = toolName;
			return this;
		}

		public Builder toolInput(String toolInput) {
			this.toolInput = toolInput;
			return this;
		}

		public Builder toolContext(ToolContext toolContext) {
			this.toolContext = toolContext;
			return this;
		}

		public Builder requiresConsent(RequiresConsent requiresConsent) {
			this.requiresConsent = requiresConsent;
			return this;
		}

		public Builder userId(String userId) {
			this.userId = userId;
			return this;
		}

		public Builder sessionId(String sessionId) {
			this.sessionId = sessionId;
			return this;
		}

		public ConsentContext build() {
			return new ConsentContext(this);
		}

	}

}
