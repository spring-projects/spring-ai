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

package org.springframework.ai.mcp.client.common.autoconfigure.properties;

import java.time.Duration;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties of the MCP client that apply to all the connections that speak
 * the 2026-07-28 ("modern") protocol revision, which a connection selects with its
 * {@code protocol=MODERN} property. The settings of a single modern connection, such as
 * its {@code client-scope}, are properties of that connection.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
@ConfigurationProperties(McpClientModernProperties.CONFIG_PREFIX)
public class McpClientModernProperties {

	public static final String CONFIG_PREFIX = "spring.ai.mcp.client.modern";

	/**
	 * Upper bound on a whole call, including the time a user takes to answer an
	 * elicitation. Unset by default, in which case only each network exchange is bound,
	 * by {@code spring.ai.mcp.client.request-timeout}.
	 */
	private @Nullable Duration callTimeout;

	/**
	 * How long a server that could not be reached is left alone before it is asked for
	 * its tools again. During that time its tools are left out without contacting it, so
	 * a server that does not answer delays only one request in that time. Zero asks the
	 * server on every request.
	 * <p>
	 * It is also the longest pause before a listen stream that ended, or that could not
	 * be opened, is opened again. The first pause is one second and each further one is
	 * twice as long, up to this time, which counts as one second if it is shorter.
	 */
	private Duration unreachableCoolDown = Duration.ofSeconds(30);

	public @Nullable Duration getCallTimeout() {
		return this.callTimeout;
	}

	public void setCallTimeout(@Nullable Duration callTimeout) {
		this.callTimeout = callTimeout;
	}

	public Duration getUnreachableCoolDown() {
		return this.unreachableCoolDown;
	}

	public void setUnreachableCoolDown(Duration unreachableCoolDown) {
		this.unreachableCoolDown = unreachableCoolDown;
	}

	/**
	 * How the clients of a modern MCP connection are created and held. It is set for each
	 * connection, with the {@code client-scope} property.
	 * <p>
	 * The scope only matters for a tool listing the server marks as {@code private}: a
	 * listing the server marks as {@code public} is kept for as long as the server allows
	 * in both scopes.
	 */
	public enum ClientScope {

		/**
		 * A new client each time the tools of the connection are resolved, which a
		 * {@code ChatClient} does once for each request. The client customizers run for
		 * each new client, on the thread that resolves the tools. A {@code private} tool
		 * listing is not kept, so one caller is never given the tool listing of another.
		 * This is the default.
		 */
		PER_REQUEST,

		/**
		 * One client for the connection, shared by all requests. The client keeps the
		 * server's tool listing for as long as the server allows, even a {@code private}
		 * one. Use it when the application calls the server under a single identity, for
		 * example with client credentials.
		 */
		SHARED

	}

}
