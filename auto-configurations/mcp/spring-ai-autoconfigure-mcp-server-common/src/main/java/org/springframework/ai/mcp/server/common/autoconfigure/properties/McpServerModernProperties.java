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

package org.springframework.ai.mcp.server.common.autoconfigure.properties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.McpSchema.CacheScope;
import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Configuration properties for the 2026-07-28 ("modern") MCP server, active when
 * {@code spring.ai.mcp.server.protocol=MODERN}.
 *
 * @author Christian Tzolov
 * @since 2.2.0
 */
@ConfigurationProperties(McpServerModernProperties.CONFIG_PREFIX)
public class McpServerModernProperties {

	public static final String CONFIG_PREFIX = "spring.ai.mcp.server.modern";

	/**
	 * The HTTP endpoint the server listens on. POST only.
	 */
	private String mcpEndpoint = "/mcp";

	/**
	 * The protocol versions the server accepts in {@code _meta}. Defaults to the latest
	 * revision the SDK implements.
	 */
	private List<String> supportedVersions = new ArrayList<>(List.of(McpSchema.LATEST_PROTOCOL_VERSION));

	/**
	 * How long clients may cache the {@code server/discover} result. Zero disables
	 * caching.
	 */
	private Duration discoverTtl = Duration.ZERO;

	/**
	 * Who may reuse a cached {@code server/discover} result.
	 */
	private CacheScope discoverCacheScope = CacheScope.PRIVATE;

	/**
	 * How long clients may cache list results ({@code tools/list}, {@code prompts/list},
	 * {@code resources/list}, {@code resources/templates/list}). Zero disables caching.
	 */
	private Duration listTtl = Duration.ZERO;

	/**
	 * Who may reuse cached list results.
	 */
	private CacheScope listCacheScope = CacheScope.PRIVATE;

	/**
	 * Maximum number of entries per list page ({@code tools/list}, {@code prompts/list},
	 * {@code resources/list}, {@code resources/templates/list}) of the in-memory
	 * repositories. Unset returns everything on a single page.
	 */
	private @Nullable Integer pageSize;

	/**
	 * Interval between keep-alive comments on streaming responses. Zero or negative
	 * disables keep-alives.
	 */
	private @Nullable Duration keepAliveInterval = Duration.ofSeconds(30);

	/**
	 * Maximum accepted request body size.
	 */
	private DataSize maxRequestSize = DataSize.ofMegabytes(16);

	/**
	 * Secret used to seal the multi round-trip {@code requestState}. Must be shared by
	 * every instance behind a load balancer. When unset a random per-instance key is used
	 * and retries must reach the same instance.
	 */
	private @Nullable String requestStateSecret;

	/**
	 * How long a sealed {@code requestState} stays valid.
	 */
	private @Nullable Duration requestStateTtl;

	/**
	 * Whether to serve {@code subscriptions/listen} for list-changed and resource-updated
	 * notifications.
	 */
	private boolean subscriptions = true;

	/**
	 * Whether the server advertises per-resource subscriptions
	 * ({@code resources.subscribe}).
	 */
	private boolean resourceSubscribe = false;

	public String getMcpEndpoint() {
		return this.mcpEndpoint;
	}

	public void setMcpEndpoint(String mcpEndpoint) {
		this.mcpEndpoint = mcpEndpoint;
	}

	public List<String> getSupportedVersions() {
		return this.supportedVersions;
	}

	public void setSupportedVersions(List<String> supportedVersions) {
		this.supportedVersions = supportedVersions;
	}

	public Duration getDiscoverTtl() {
		return this.discoverTtl;
	}

	public void setDiscoverTtl(Duration discoverTtl) {
		this.discoverTtl = discoverTtl;
	}

	public CacheScope getDiscoverCacheScope() {
		return this.discoverCacheScope;
	}

	public void setDiscoverCacheScope(CacheScope discoverCacheScope) {
		this.discoverCacheScope = discoverCacheScope;
	}

	public Duration getListTtl() {
		return this.listTtl;
	}

	public void setListTtl(Duration listTtl) {
		this.listTtl = listTtl;
	}

	public CacheScope getListCacheScope() {
		return this.listCacheScope;
	}

	public void setListCacheScope(CacheScope listCacheScope) {
		this.listCacheScope = listCacheScope;
	}

	public @Nullable Integer getPageSize() {
		return this.pageSize;
	}

	public void setPageSize(@Nullable Integer pageSize) {
		this.pageSize = pageSize;
	}

	public @Nullable Duration getKeepAliveInterval() {
		return this.keepAliveInterval;
	}

	/**
	 * The keep-alive interval to hand to a transport: {@code null} when keep-alives are
	 * disabled by an unset, zero or negative value.
	 */
	public @Nullable Duration effectiveKeepAliveInterval() {
		Duration interval = this.keepAliveInterval;
		return interval == null || interval.isZero() || interval.isNegative() ? null : interval;
	}

	public void setKeepAliveInterval(@Nullable Duration keepAliveInterval) {
		this.keepAliveInterval = keepAliveInterval;
	}

	public DataSize getMaxRequestSize() {
		return this.maxRequestSize;
	}

	public void setMaxRequestSize(DataSize maxRequestSize) {
		this.maxRequestSize = maxRequestSize;
	}

	public @Nullable String getRequestStateSecret() {
		return this.requestStateSecret;
	}

	public void setRequestStateSecret(@Nullable String requestStateSecret) {
		this.requestStateSecret = requestStateSecret;
	}

	public @Nullable Duration getRequestStateTtl() {
		return this.requestStateTtl;
	}

	public void setRequestStateTtl(@Nullable Duration requestStateTtl) {
		this.requestStateTtl = requestStateTtl;
	}

	public boolean isSubscriptions() {
		return this.subscriptions;
	}

	public void setSubscriptions(boolean subscriptions) {
		this.subscriptions = subscriptions;
	}

	public boolean isResourceSubscribe() {
		return this.resourceSubscribe;
	}

	public void setResourceSubscribe(boolean resourceSubscribe) {
		this.resourceSubscribe = resourceSubscribe;
	}

}
