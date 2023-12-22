/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.autoconfigure.vectorstore.neo4j;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.File;
import java.net.URI;
import java.time.Duration;

/**
 * Properties for Neo4j driver
 *
 * @author Jingzhou Ou
 */
@ConfigurationProperties(Neo4jDriverProperties.CONFIG_PREFIX)
public class Neo4jDriverProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.neo4j.driver";

	/**
	 * supports bolt or neo4j as schemes.
	 */
	private URI uri;

	/**
	 * optional
	 */
	private Authentication authentication = new Authentication();

	/**
	 * connection pool configuration
	 */
	private PoolSettings pool = new PoolSettings();

	/**
	 * Detailed driver configuration of the driver
	 */
	private DriverSettings config = new DriverSettings();

	public URI getUri() {
		return this.uri;
	}

	public void setUri(URI uri) {
		this.uri = uri;
	}

	public Authentication getAuthentication() {
		return this.authentication;
	}

	public void setAuthentication(Authentication authentication) {
		this.authentication = authentication;
	}

	public PoolSettings getPool() {
		return this.pool;
	}

	public void setPool(PoolSettings pool) {
		this.pool = pool;
	}

	public DriverSettings getConfig() {
		return this.config;
	}

	public void setConfig(DriverSettings config) {
		this.config = config;
	}

	public static class Authentication {

		private String username;

		private String password;

		private String realm;

		/**
		 * kerberos authentication
		 */
		private String kerberosTicket;

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

		public String getRealm() {
			return this.realm;
		}

		public void setRealm(String realm) {
			this.realm = realm;
		}

		public String getKerberosTicket() {
			return this.kerberosTicket;
		}

		public void setKerberosTicket(String kerberosTicket) {
			this.kerberosTicket = kerberosTicket;
		}

	}

	public static class PoolSettings {

		private boolean metricsEnabled = false;

		private boolean logLeakedSessions = false;

		private int maxConnectionPoolSize = org.neo4j.driver.internal.async.pool.PoolSettings.DEFAULT_MAX_CONNECTION_POOL_SIZE;

		private Duration idleTimeBeforeConnectionTest;

		private Duration maxConnectionLifetime = Duration
			.ofMillis(org.neo4j.driver.internal.async.pool.PoolSettings.DEFAULT_MAX_CONNECTION_LIFETIME);

		private Duration connectionAcquisitionTimeout = Duration
			.ofMillis(org.neo4j.driver.internal.async.pool.PoolSettings.DEFAULT_CONNECTION_ACQUISITION_TIMEOUT);

		public boolean isLogLeakedSessions() {
			return this.logLeakedSessions;
		}

		public void setLogLeakedSessions(boolean logLeakedSessions) {
			this.logLeakedSessions = logLeakedSessions;
		}

		public int getMaxConnectionPoolSize() {
			return this.maxConnectionPoolSize;
		}

		public void setMaxConnectionPoolSize(int maxConnectionPoolSize) {
			this.maxConnectionPoolSize = maxConnectionPoolSize;
		}

		public Duration getIdleTimeBeforeConnectionTest() {
			return this.idleTimeBeforeConnectionTest;
		}

		public void setIdleTimeBeforeConnectionTest(Duration idleTimeBeforeConnectionTest) {
			this.idleTimeBeforeConnectionTest = idleTimeBeforeConnectionTest;
		}

		public Duration getMaxConnectionLifetime() {
			return this.maxConnectionLifetime;
		}

		public void setMaxConnectionLifetime(Duration maxConnectionLifetime) {
			this.maxConnectionLifetime = maxConnectionLifetime;
		}

		public Duration getConnectionAcquisitionTimeout() {
			return this.connectionAcquisitionTimeout;
		}

		public void setConnectionAcquisitionTimeout(Duration connectionAcquisitionTimeout) {
			this.connectionAcquisitionTimeout = connectionAcquisitionTimeout;
		}

		public boolean isMetricsEnabled() {
			return this.metricsEnabled;
		}

		public void setMetricsEnabled(boolean metricsEnabled) {
			this.metricsEnabled = metricsEnabled;
		}

	}

	public static class DriverSettings {

		private boolean encrypted = false;

		private TrustSettings trustSettings = new TrustSettings();

		private Duration connectionTimeout = Duration.ofSeconds(30);

		private Duration maxTransactionRetryTime = Duration
			.ofMillis(org.neo4j.driver.internal.retry.RetrySettings.DEFAULT.maxRetryTimeMs());

		public boolean isEncrypted() {
			return this.encrypted;
		}

		public void setEncrypted(boolean encrypted) {
			this.encrypted = encrypted;
		}

		public TrustSettings getTrustSettings() {
			return this.trustSettings;
		}

		public void setTrustSettings(TrustSettings trustSettings) {
			this.trustSettings = trustSettings;
		}

		public Duration getConnectionTimeout() {
			return this.connectionTimeout;
		}

		public void setConnectionTimeout(Duration connectionTimeout) {
			this.connectionTimeout = connectionTimeout;
		}

		public Duration getMaxTransactionRetryTime() {
			return this.maxTransactionRetryTime;
		}

		public void setMaxTransactionRetryTime(Duration maxTransactionRetryTime) {
			this.maxTransactionRetryTime = maxTransactionRetryTime;
		}

	}

	public static class TrustSettings {

		public enum Strategy {

			TRUST_ALL_CERTIFICATES,

			TRUST_CUSTOM_CA_SIGNED_CERTIFICATES,

			TRUST_SYSTEM_CA_SIGNED_CERTIFICATES

		}

		private TrustSettings.Strategy strategy = Strategy.TRUST_SYSTEM_CA_SIGNED_CERTIFICATES;

		private File certFile;

		private boolean hostnameVerificationEnabled = false;

		public TrustSettings.Strategy getStrategy() {
			return this.strategy;
		}

		public void setStrategy(TrustSettings.Strategy strategy) {
			this.strategy = strategy;
		}

		public File getCertFile() {
			return this.certFile;
		}

		public void setCertFile(File certFile) {
			this.certFile = certFile;
		}

		public boolean isHostnameVerificationEnabled() {
			return this.hostnameVerificationEnabled;
		}

		public void setHostnameVerificationEnabled(boolean hostnameVerificationEnabled) {
			this.hostnameVerificationEnabled = hostnameVerificationEnabled;
		}

	}

}
