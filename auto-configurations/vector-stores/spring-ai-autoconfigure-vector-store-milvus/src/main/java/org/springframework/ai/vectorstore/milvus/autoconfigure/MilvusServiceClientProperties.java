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

package org.springframework.ai.vectorstore.milvus.autoconfigure;

import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parameters for Milvus client connection.
 *
 * @author Christian Tzolov
 */
@ConfigurationProperties(MilvusServiceClientProperties.CONFIG_PREFIX)
public class MilvusServiceClientProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.milvus.client";

	/**
	 * Secure the authorization for this connection, set to True to enable TLS.
	 */
	protected boolean secure = false;

	/**
	 * Milvus host name/address.
	 */
	private String host = "localhost";

	/**
	 * Milvus the connection port. Value must be greater than zero and less than 65536.
	 */
	private int port = 19530;

	/**
	 * The uri of Milvus instance
	 */
	private @Nullable String uri;

	/**
	 * Token serving as the key for identification and authentication purposes.
	 */
	private @Nullable String token;

	/**
	 * Connection timeout value of client channel. The timeout value must be greater than
	 * zero.
	 */
	private long connectTimeoutMs = 10000;

	/**
	 * Keep-alive time value of client channel. The keep-alive value must be greater than
	 * zero.
	 */
	private long keepAliveTimeMs = 55000;

	/**
	 * Enables the keep-alive function for client channel.
	 */
	// private boolean keepAliveWithoutCalls = false;

	/**
	 * The keep-alive timeout value of client channel. The timeout value must be greater
	 * than zero.
	 */
	private long keepAliveTimeoutMs = 20000;

	/**
	 * Deadline for how long you are willing to wait for a reply from the server. With a
	 * deadline setting, the client will wait when encounter fast RPC fail caused by
	 * network fluctuations. The deadline value must be larger than or equal to zero.
	 * Default value is 0, deadline is disabled.
	 */
	private long rpcDeadlineMs = 0; // Disabling deadline

	/**
	 * The client.key path for tls two-way authentication, only takes effect when "secure"
	 * is True.
	 */
	private @Nullable String clientKeyPath;

	/**
	 * The client.pem path for tls two-way authentication, only takes effect when "secure"
	 * is True.
	 */
	private @Nullable String clientPemPath;

	/**
	 * The ca.pem path for tls two-way authentication, only takes effect when "secure" is
	 * True.
	 */
	private @Nullable String caPemPath;

	/**
	 * server.pem path for tls one-way authentication, only takes effect when "secure" is
	 * True.
	 */
	private @Nullable String serverPemPath;

	/**
	 * Sets the target name override for SSL host name checking, only takes effect when
	 * "secure" is True. Note: this value is passed to grpc.ssl_target_name_override
	 */
	private @Nullable String serverName;

	/**
	 * Idle timeout value of client channel. The timeout value must be larger than zero.
	 */
	private long idleTimeoutMs = TimeUnit.MILLISECONDS.convert(24, TimeUnit.HOURS);

	/**
	 * The username and password for this connection.
	 */
	private String username = "root";

	/**
	 * The password for this connection.
	 */
	private String password = "milvus";

	public String getHost() {
		return this.host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return this.port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public @Nullable String getUri() {
		return this.uri;
	}

	public void setUri(@Nullable String uri) {
		this.uri = uri;
	}

	public @Nullable String getToken() {
		return this.token;
	}

	public void setToken(@Nullable String token) {
		this.token = token;
	}

	public long getConnectTimeoutMs() {
		return this.connectTimeoutMs;
	}

	public void setConnectTimeoutMs(long connectTimeoutMs) {
		this.connectTimeoutMs = connectTimeoutMs;
	}

	public long getKeepAliveTimeMs() {
		return this.keepAliveTimeMs;
	}

	public void setKeepAliveTimeMs(long keepAliveTimeMs) {
		this.keepAliveTimeMs = keepAliveTimeMs;
	}

	public long getKeepAliveTimeoutMs() {
		return this.keepAliveTimeoutMs;
	}

	public void setKeepAliveTimeoutMs(long keepAliveTimeoutMs) {
		this.keepAliveTimeoutMs = keepAliveTimeoutMs;
	}

	// public boolean isKeepAliveWithoutCalls() {
	// return keepAliveWithoutCalls;
	// }

	// public void setKeepAliveWithoutCalls(boolean keepAliveWithoutCalls) {
	// this.keepAliveWithoutCalls = keepAliveWithoutCalls;
	// }

	public long getRpcDeadlineMs() {
		return this.rpcDeadlineMs;
	}

	public void setRpcDeadlineMs(long rpcDeadlineMs) {
		this.rpcDeadlineMs = rpcDeadlineMs;
	}

	public @Nullable String getClientKeyPath() {
		return this.clientKeyPath;
	}

	public void setClientKeyPath(@Nullable String clientKeyPath) {
		this.clientKeyPath = clientKeyPath;
	}

	public @Nullable String getClientPemPath() {
		return this.clientPemPath;
	}

	public void setClientPemPath(@Nullable String clientPemPath) {
		this.clientPemPath = clientPemPath;
	}

	public @Nullable String getCaPemPath() {
		return this.caPemPath;
	}

	public void setCaPemPath(@Nullable String caPemPath) {
		this.caPemPath = caPemPath;
	}

	public @Nullable String getServerPemPath() {
		return this.serverPemPath;
	}

	public void setServerPemPath(@Nullable String serverPemPath) {
		this.serverPemPath = serverPemPath;
	}

	public @Nullable String getServerName() {
		return this.serverName;
	}

	public void setServerName(@Nullable String serverName) {
		this.serverName = serverName;
	}

	public boolean isSecure() {
		return this.secure;
	}

	public void setSecure(boolean secure) {
		this.secure = secure;
	}

	public long getIdleTimeoutMs() {
		return this.idleTimeoutMs;
	}

	public void setIdleTimeoutMs(long idleTimeoutMs) {
		this.idleTimeoutMs = idleTimeoutMs;
	}

	public String getUsername() {
		return this.username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return this.password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

}
