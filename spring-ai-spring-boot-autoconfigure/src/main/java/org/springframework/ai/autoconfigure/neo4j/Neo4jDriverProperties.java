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

package org.springframework.ai.autoconfigure.neo4j;

import org.neo4j.driver.AuthToken;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.internal.Scheme;
import org.neo4j.driver.net.ServerAddressResolver;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.source.InvalidConfigurationPropertyValueException;
import org.springframework.util.StringUtils;

import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.springframework.ai.autoconfigure.neo4j.Neo4jDriverProperties.CONFIG_PREFIX;

/**
 * Properties for Neo4j driver
 *
 * @author Jingzhou Ou
 */
@ConfigurationProperties(CONFIG_PREFIX)
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

	public AuthToken getAuthToken() {
		return this.authentication.asAuthToken();
	}

	public Config asDriverConfig() {
		Config.ConfigBuilder builder = Config.builder();
		this.pool.applyTo(builder);
		String scheme = uri == null ? "bolt" : uri.getScheme();
		this.config.applyTo(builder, isSimpleScheme(scheme));
		return builder.build();
	}

	static boolean isSimpleScheme(String scheme) {

		String lowerCaseScheme = scheme.toLowerCase(Locale.ENGLISH);
		try {
			Scheme.validateScheme(lowerCaseScheme);
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException(String.format("'%s' is not a supported scheme.", scheme));
		}

		return lowerCaseScheme.equals("bolt") || lowerCaseScheme.equals("neo4j");
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

		AuthToken asAuthToken() {

			boolean hasUsername = StringUtils.hasText(this.username);
			boolean hasPassword = StringUtils.hasText(this.password);
			boolean hasKerberosTicket = StringUtils.hasText(this.kerberosTicket);

			if (hasUsername && hasKerberosTicket) {
				throw new InvalidConfigurationPropertyValueException(
						"spring.ai.vectorstore.neo4j.driver.authentication",
						"username=" + username + ",kerberos-ticket=" + kerberosTicket,
						"Cannot specify both username and kerberos ticket.");
			}

			if (hasUsername && hasPassword) {
				return AuthTokens.basic(this.username, this.password, this.realm);
			}

			if (hasKerberosTicket) {
				return AuthTokens.kerberos(this.kerberosTicket);
			}

			return AuthTokens.none();
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

		private void applyTo(Config.ConfigBuilder builder) {

			if (this.logLeakedSessions) {
				builder.withLeakedSessionsLogging();
			}
			builder.withMaxConnectionPoolSize(this.maxConnectionPoolSize);
			if (this.idleTimeBeforeConnectionTest != null) {
				builder.withConnectionLivenessCheckTimeout(this.idleTimeBeforeConnectionTest.toMillis(),
						TimeUnit.MILLISECONDS);
			}
			builder.withMaxConnectionLifetime(this.maxConnectionLifetime.toMillis(), TimeUnit.MILLISECONDS);
			builder.withConnectionAcquisitionTimeout(this.connectionAcquisitionTimeout.toMillis(),
					TimeUnit.MILLISECONDS);

			if (metricsEnabled) {
				builder.withDriverMetrics();
			}
			else {
				builder.withoutDriverMetrics();
			}
		}

	}

	public static class DriverSettings {

		private boolean encrypted = false;

		private TrustSettings trustSettings = new TrustSettings();

		private Duration connectionTimeout = Duration.ofSeconds(30);

		private Duration maxTransactionRetryTime = Duration
			.ofMillis(org.neo4j.driver.internal.retry.RetrySettings.DEFAULT.maxRetryTimeMs());

		private Class<? extends ServerAddressResolver> serverAddressResolverClass;

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

		public Class<? extends ServerAddressResolver> getServerAddressResolverClass() {
			return this.serverAddressResolverClass;
		}

		public void setServerAddressResolverClass(Class<? extends ServerAddressResolver> serverAddressResolverClass) {
			this.serverAddressResolverClass = serverAddressResolverClass;
		}

		private void applyTo(Config.ConfigBuilder builder, boolean withEncryptionAndTrustSettings) {

			if (withEncryptionAndTrustSettings) {
				applyEncryptionAndTrustSettings(builder);
			}

			builder.withConnectionTimeout(this.connectionTimeout.toMillis(), TimeUnit.MILLISECONDS);
			builder.withMaxTransactionRetryTime(this.maxTransactionRetryTime.toMillis(), TimeUnit.MILLISECONDS);

			if (this.serverAddressResolverClass != null) {
				builder.withResolver(BeanUtils.instantiateClass(this.serverAddressResolverClass));
			}
		}

		private void applyEncryptionAndTrustSettings(Config.ConfigBuilder builder) {
			if (this.encrypted) {
				builder.withEncryption();
			}
			else {
				builder.withoutEncryption();
			}
			builder.withTrustStrategy(this.trustSettings.toInternalRepresentation());
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

		Config.TrustStrategy toInternalRepresentation() {
			String propertyName = "spring.ai.vectorstore.neo4j.driver.config.trust-settings";

			Config.TrustStrategy internalRepresentation;
			switch (this.strategy) {
				case TRUST_ALL_CERTIFICATES:
					internalRepresentation = Config.TrustStrategy.trustAllCertificates();
					break;
				case TRUST_SYSTEM_CA_SIGNED_CERTIFICATES:
					internalRepresentation = Config.TrustStrategy.trustSystemCertificates();
					break;
				case TRUST_CUSTOM_CA_SIGNED_CERTIFICATES:
					if (this.certFile == null || !this.certFile.isFile()) {
						throw new InvalidConfigurationPropertyValueException(propertyName, this.strategy.name(),
								"Configured trust strategy requires a certificate file.");
					}
					internalRepresentation = Config.TrustStrategy.trustCustomCertificateSignedBy(this.certFile);
					break;
				default:
					throw new InvalidConfigurationPropertyValueException(propertyName, this.strategy.name(),
							"Unknown strategy.");
			}

			if (this.hostnameVerificationEnabled) {
				internalRepresentation.withHostnameVerification();
			}
			else {
				internalRepresentation.withoutHostnameVerification();
			}

			return internalRepresentation;
		}

	}

}
