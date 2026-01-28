/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.model.bedrock.autoconfigure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for Bedrock AWS connection.
 *
 * @author Christian Tzolov
 * @author Baojun Jiang
 * @since 0.8.0
 */
@ConfigurationProperties(BedrockAwsConnectionProperties.CONFIG_PREFIX)
public class BedrockAwsConnectionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.bedrock.aws";

	/**
	 * AWS region to use. Defaults to us-east-1.
	 */
	private String region = "us-east-1";

	/**
	 * AWS access key.
	 */
	private String accessKey;

	/**
	 * AWS secret key.
	 */
	private String secretKey;

	/**
	 * AWS session token. (optional) When provided the AwsSessionCredentials are used.
	 * Otherwise, the AwsBasicCredentials are used.
	 */
	private String sessionToken;

	/**
	 * Aws profile. (optional) When the {@link #accessKey} and {@link #secretKey} are not
	 * declared. Otherwise, the AwsBasicCredentials are used.
	 */
	@NestedConfigurationProperty
	private ProfileProperties profile;

	/**
	 * Maximum duration of the entire API call operation.
	 */
	private Duration timeout = Duration.ofMinutes(5L);

	/**
	 * Maximum time to wait while establishing connection with AWS service.
	 */
	private Duration connectionTimeout = Duration.ofSeconds(5L);

	/**
	 * Maximum duration spent reading response data.
	 */
	private Duration asyncReadTimeout = Duration.ofSeconds(30L);

	/**
	 * Maximum time to wait for a new connection from the pool.
	 */
	private Duration connectionAcquisitionTimeout = Duration.ofSeconds(30L);

	/**
	 * Maximum time to wait for response data.
	 */
	private Duration socketTimeout = Duration.ofSeconds(90L);

	public String getRegion() {
		return this.region;
	}

	public void setRegion(String awsRegion) {
		this.region = awsRegion;
	}

	public String getAccessKey() {
		return this.accessKey;
	}

	public void setAccessKey(String accessKey) {
		this.accessKey = accessKey;
	}

	public String getSecretKey() {
		return this.secretKey;
	}

	public void setSecretKey(String secretKey) {
		this.secretKey = secretKey;
	}

	public Duration getTimeout() {
		return this.timeout;
	}

	public void setTimeout(Duration timeout) {
		this.timeout = timeout;
	}

	public Duration getConnectionTimeout() {
		return this.connectionTimeout;
	}

	public void setConnectionTimeout(Duration connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
	}

	public Duration getAsyncReadTimeout() {
		return this.asyncReadTimeout;
	}

	public void setAsyncReadTimeout(Duration asyncReadTimeout) {
		this.asyncReadTimeout = asyncReadTimeout;
	}

	public Duration getConnectionAcquisitionTimeout() {
		return this.connectionAcquisitionTimeout;
	}

	public void setConnectionAcquisitionTimeout(Duration connectionAcquisitionTimeout) {
		this.connectionAcquisitionTimeout = connectionAcquisitionTimeout;
	}

	public Duration getSocketTimeout() {
		return this.socketTimeout;
	}

	public void setSocketTimeout(Duration socketTimeout) {
		this.socketTimeout = socketTimeout;
	}

	public String getSessionToken() {
		return this.sessionToken;
	}

	public void setSessionToken(String sessionToken) {
		this.sessionToken = sessionToken;
	}

	public ProfileProperties getProfile() {
		return this.profile;
	}

	public void setProfile(ProfileProperties profile) {
		this.profile = profile;
	}

}
