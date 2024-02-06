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

import org.neo4j.driver.AuthToken;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.internal.Scheme;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.vectorstore.Neo4jVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.source.InvalidConfigurationPropertyValueException;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import java.io.File;
import java.net.URI;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * @author Jingzhou Ou
 */
@AutoConfiguration
@ConditionalOnClass({ Neo4jVectorStore.class, EmbeddingClient.class })
@EnableConfigurationProperties({ Neo4jVectorStoreProperties.class, Neo4jDriverProperties.class })
public class Neo4jVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public VectorStore vectorStore(Driver driver, EmbeddingClient embeddingClient,
			Neo4jVectorStoreProperties properties) {
		Neo4jVectorStore.Neo4jVectorStoreConfig config = Neo4jVectorStore.Neo4jVectorStoreConfig.builder()
			.withDatabaseName(properties.getDatabaseName())
			.withEmbeddingDimension(properties.getEmbeddingDimension())
			.withDistanceType(properties.getDistanceType())
			.withLabel(properties.getLabel())
			.withEmbeddingProperty(properties.getEmbeddingProperty())
			.withIndexName(properties.getIndexName())
			.build();

		return new Neo4jVectorStore(driver, embeddingClient, config);
	}

	@Bean
	@ConditionalOnMissingBean(Driver.class)
	Driver neo4jDriver(Neo4jDriverProperties driverProperties) {
		AuthToken authToken = getAuthToken(driverProperties);
		Config config = getDriverConfig(driverProperties);
		return GraphDatabase.driver(driverProperties.getUri(), authToken, config);
	}

	private Config getDriverConfig(Neo4jDriverProperties driverProperties) {
		Config.ConfigBuilder builder = Config.builder();
		buildWithPoolSettings(builder, driverProperties.getPool());
		URI uri = driverProperties.getUri();
		String scheme = uri == null ? "bolt" : uri.getScheme();
		buildWithDriverSettings(builder, driverProperties.getConfig(), isSimpleScheme(scheme));
		return builder.build();
	}

	private AuthToken getAuthToken(Neo4jDriverProperties driverProperties) {
		String username = driverProperties.getAuthentication().getUsername();
		String password = driverProperties.getAuthentication().getPassword();
		String kerberosTicket = driverProperties.getAuthentication().getKerberosTicket();
		String realm = driverProperties.getAuthentication().getRealm();

		boolean hasUsername = StringUtils.hasText(username);
		boolean hasPassword = StringUtils.hasText(password);
		boolean hasKerberosTicket = StringUtils.hasText(kerberosTicket);

		if (hasUsername && hasKerberosTicket) {
			throw new InvalidConfigurationPropertyValueException("spring.ai.vectorstore.neo4j.driver.authentication",
					"username=" + username + ",kerberos-ticket=" + kerberosTicket,
					"Cannot specify both username and kerberos ticket.");
		}

		if (hasUsername && hasPassword) {
			return AuthTokens.basic(username, password, realm);
		}

		if (hasKerberosTicket) {
			return AuthTokens.kerberos(kerberosTicket);
		}

		return AuthTokens.none();
	}

	private void buildWithPoolSettings(Config.ConfigBuilder builder, Neo4jDriverProperties.PoolSettings poolSettings) {
		if (poolSettings.isLogLeakedSessions()) {
			builder.withLeakedSessionsLogging();
		}
		builder.withMaxConnectionPoolSize(poolSettings.getMaxConnectionPoolSize());
		if (poolSettings.getIdleTimeBeforeConnectionTest() != null) {
			builder.withConnectionLivenessCheckTimeout(poolSettings.getIdleTimeBeforeConnectionTest().toMillis(),
					TimeUnit.MILLISECONDS);
		}
		builder.withMaxConnectionLifetime(poolSettings.getMaxConnectionLifetime().toMillis(), TimeUnit.MILLISECONDS);
		builder.withConnectionAcquisitionTimeout(poolSettings.getConnectionAcquisitionTimeout().toMillis(),
				TimeUnit.MILLISECONDS);

		if (poolSettings.isMetricsEnabled()) {
			builder.withDriverMetrics();
		}
		else {
			builder.withoutDriverMetrics();
		}
	}

	private void buildWithDriverSettings(Config.ConfigBuilder builder,
			Neo4jDriverProperties.DriverSettings driverSettings, boolean withEncryptionAndTrustSettings) {
		if (withEncryptionAndTrustSettings) {
			if (driverSettings.isEncrypted()) {
				builder.withEncryption();
			}
			else {
				builder.withoutEncryption();
			}
			builder.withTrustStrategy(getTrustStrategy(driverSettings.getTrustSettings()));
		}
		builder.withConnectionTimeout(driverSettings.getConnectionTimeout().toMillis(), TimeUnit.MILLISECONDS);
		builder.withMaxTransactionRetryTime(driverSettings.getMaxTransactionRetryTime().toMillis(),
				TimeUnit.MILLISECONDS);
	}

	private boolean isSimpleScheme(String scheme) {
		String lowerCaseScheme = scheme.toLowerCase(Locale.ENGLISH);
		try {
			Scheme.validateScheme(lowerCaseScheme);
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException(String.format("'%s' is not a supported scheme.", scheme));
		}
		return lowerCaseScheme.equals("bolt") || lowerCaseScheme.equals("neo4j");
	}

	private Config.TrustStrategy getTrustStrategy(Neo4jDriverProperties.TrustSettings trustSettings) {
		String propertyName = "spring.ai.vectorstore.neo4j.driver.config.trust-settings";
		Config.TrustStrategy internalRepresentation;
		File certFile = trustSettings.getCertFile();
		switch (trustSettings.getStrategy()) {
			case TRUST_ALL_CERTIFICATES:
				internalRepresentation = Config.TrustStrategy.trustAllCertificates();
				break;
			case TRUST_SYSTEM_CA_SIGNED_CERTIFICATES:
				internalRepresentation = Config.TrustStrategy.trustSystemCertificates();
				break;
			case TRUST_CUSTOM_CA_SIGNED_CERTIFICATES:
				if (certFile == null || !certFile.isFile()) {
					throw new InvalidConfigurationPropertyValueException(propertyName,
							trustSettings.getStrategy().name(),
							"Configured trust strategy requires a certificate file.");
				}
				internalRepresentation = Config.TrustStrategy.trustCustomCertificateSignedBy(certFile);
				break;
			default:
				throw new InvalidConfigurationPropertyValueException(propertyName, trustSettings.getStrategy().name(),
						"Unknown strategy.");
		}
		if (trustSettings.isHostnameVerificationEnabled()) {
			internalRepresentation.withHostnameVerification();
		}
		else {
			internalRepresentation.withoutHostnameVerification();
		}
		return internalRepresentation;
	}

}
