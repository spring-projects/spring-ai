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

package org.springframework.ai.mcp.annotation.context;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.modern.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.modern.McpSchema.Implementation;
import io.modelcontextprotocol.modern.server.InputResponses;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpSyncNotifier;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.mcp.annotation.context.McpRequestContextTypes.ProgressSpec;
import org.springframework.util.Assert;

/**
 * Default {@link McpModernSyncRequestContext} built from the SDK request context, the
 * MRTR fields of the request and, for streaming responses, the notifier.
 *
 * @author Christian Tzolov
 * @since 2.2.0
 */
public final class DefaultMcpModernSyncRequestContext implements McpModernSyncRequestContext {

	private static final Log logger = LogFactory.getLog(DefaultMcpModernSyncRequestContext.class);

	private final McpRequestContext context;

	private final Map<String, Object> inputResponses;

	private final @Nullable String requestState;

	private final @Nullable McpSyncNotifier notifier;

	private final McpJsonMapper jsonMapper;

	private DefaultMcpModernSyncRequestContext(Builder builder) {
		Assert.notNull(builder.context, "context must not be null");
		this.context = builder.context;
		this.inputResponses = builder.inputResponses != null ? builder.inputResponses : Map.of();
		this.requestState = builder.requestState;
		this.notifier = builder.notifier;
		this.jsonMapper = builder.jsonMapper != null ? builder.jsonMapper : McpJsonDefaults.getMapper();
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public McpRequestContext context() {
		return this.context;
	}

	@Override
	public String protocolVersion() {
		return this.context.protocolVersion();
	}

	@Override
	public ClientCapabilities clientCapabilities() {
		return this.context.clientCapabilities();
	}

	@Override
	public @Nullable Implementation clientInfo() {
		return this.context.clientInfo();
	}

	@Override
	public Map<String, Object> requestMeta() {
		return this.context.meta();
	}

	@Override
	public McpTransportContext transportContext() {
		return this.context.transportContext();
	}

	@Override
	public @Nullable Object progressToken() {
		return this.context.progressToken();
	}

	@Override
	public boolean isRetry() {
		return this.context.isRetry();
	}

	@Override
	public Map<String, Object> inputResponses() {
		return this.inputResponses;
	}

	@Override
	public @Nullable String requestState() {
		return this.requestState;
	}

	@Override
	public <T> Optional<T> inputResponse(String key, Class<T> type) {
		Assert.hasText(key, "key must not be empty");
		Assert.notNull(type, "type must not be null");
		return InputResponses.get(this.inputResponses, key, type, this.jsonMapper);
	}

	@Override
	public boolean progressEnabled() {
		return this.notifier != null && this.context.progressToken() != null;
	}

	@Override
	public void progress(int percentage) {
		Assert.isTrue(percentage >= 0 && percentage <= 100, "Percentage must be between 0 and 100");
		progress(percentage, 100.0, null);
	}

	@Override
	public void progress(double progress, @Nullable Double total, @Nullable String message) {
		if (this.notifier == null) {
			if (logger.isDebugEnabled()) {
				logger.debug("Progress notification dropped: the call is not served as a streaming response");
			}
			return;
		}
		this.notifier.progress(progress, total, message);
	}

	@Override
	public void progress(Consumer<ProgressSpec> progressSpec) {
		Assert.notNull(progressSpec, "progressSpec must not be null");
		DefaultProgressSpec spec = new DefaultProgressSpec();
		progressSpec.accept(spec);
		progress(spec.progress, spec.total, spec.message);
	}

	@Override
	public void notify(String method, Object params) {
		Assert.hasText(method, "method must not be empty");
		if (this.notifier == null) {
			if (logger.isDebugEnabled()) {
				logger.debug("Notification '" + method + "' dropped: the call is not served as a streaming response");
			}
			return;
		}
		this.notifier.notify(method, params);
	}

	/**
	 * Builds a {@link DefaultMcpModernSyncRequestContext}.
	 */
	public static final class Builder {

		private @Nullable McpRequestContext context;

		private @Nullable Map<String, Object> inputResponses;

		private @Nullable String requestState;

		private @Nullable McpSyncNotifier notifier;

		private @Nullable McpJsonMapper jsonMapper;

		private Builder() {
		}

		public Builder context(McpRequestContext context) {
			this.context = context;
			return this;
		}

		public Builder inputResponses(@Nullable Map<String, Object> inputResponses) {
			this.inputResponses = inputResponses;
			return this;
		}

		public Builder requestState(@Nullable String requestState) {
			this.requestState = requestState;
			return this;
		}

		public Builder notifier(@Nullable McpSyncNotifier notifier) {
			this.notifier = notifier;
			return this;
		}

		public Builder jsonMapper(@Nullable McpJsonMapper jsonMapper) {
			this.jsonMapper = jsonMapper;
			return this;
		}

		public DefaultMcpModernSyncRequestContext build() {
			return new DefaultMcpModernSyncRequestContext(this);
		}

	}

}
