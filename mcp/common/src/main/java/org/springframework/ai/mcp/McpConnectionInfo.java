/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * MCP connection info record containing the client and server related metadata.
 *
 * @param clientCapabilities the MCP client capabilities
 * @param clientInfo the MCP client information
 * @param initializeResult the MCP server initialization result
 * @author Ilayaperumal Gopinathan
 * @author Christian Tzolov
 */
public record McpConnectionInfo(// @formatter:off
	McpSchema.ClientCapabilities clientCapabilities,
	McpSchema.Implementation clientInfo,
	McpSchema.@Nullable InitializeResult initializeResult) { // @formatter:on

	/**
	 * Creates a new Builder instance for constructing McpConnectionInfo.
	 * @return a new Builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder class for constructing McpConnectionInfo instances.
	 */
	public static final class Builder {

		private McpSchema.@Nullable ClientCapabilities clientCapabilities;

		private McpSchema.@Nullable Implementation clientInfo;

		private McpSchema.@Nullable InitializeResult initializeResult;

		/**
		 * Private constructor to enforce builder pattern.
		 */
		private Builder() {
		}

		/**
		 * Sets the client capabilities.
		 * @param clientCapabilities the MCP client capabilities
		 * @return this builder instance for method chaining
		 */
		public Builder clientCapabilities(McpSchema.ClientCapabilities clientCapabilities) {
			this.clientCapabilities = clientCapabilities;
			return this;
		}

		/**
		 * Sets the client information.
		 * @param clientInfo the MCP client information
		 * @return this builder instance for method chaining
		 */
		public Builder clientInfo(McpSchema.Implementation clientInfo) {
			this.clientInfo = clientInfo;
			return this;
		}

		/**
		 * Sets the initialize result.
		 * @param initializeResult the MCP server initialization result
		 * @return this builder instance for method chaining
		 */
		public Builder initializeResult(McpSchema.InitializeResult initializeResult) {
			this.initializeResult = initializeResult;
			return this;
		}

		/**
		 * Builds and returns a new McpConnectionInfo instance with the configured values.
		 * @return a new McpConnectionInfo instance
		 */
		public McpConnectionInfo build() {
			Assert.state(this.clientCapabilities != null, "clientCapabilities should not be null");
			Assert.state(this.clientInfo != null, "clientInfo should not be null");
			return new McpConnectionInfo(this.clientCapabilities, this.clientInfo, this.initializeResult);
		}

	}

}
