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

import java.net.URI;
import java.time.Duration;

import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.awscore.defaultsmode.DefaultsMode;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for Bedrock AWS connection.
 *
 * @author Christian Tzolov
 * @author Baojun Jiang
 * @author Matej Nedic
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
	 * Configures an instance profile credentials provider with no further configuration.
	 */
	private boolean instanceProfile = false;

	/**
	 * Aws profile. (optional) When the {@link #accessKey} and {@link #secretKey} are not
	 * declared. Otherwise, the AwsBasicCredentials are used.
	 */
	@NestedConfigurationProperty
	@Nullable private ProfileProperties profile;

	/**
	 * Maximum duration of the entire API call operation.
	 */
	private Duration timeout = Duration.ofMinutes(5L);

	/**
	 * Sync HTTP client properties (Apache).
	 */
	@NestedConfigurationProperty
	private SyncClientProperties syncClient = new SyncClientProperties();

	/**
	 * Async HTTP client properties (Netty).
	 */
	@NestedConfigurationProperty
	private AsyncClientProperties asyncClient = new AsyncClientProperties();

	/**
	 * STS credential provider configuration for assume role and web identity token
	 * authentication.
	 */
	@NestedConfigurationProperty
	@Nullable private StsProperties stsProperties;

	/**
	 * AWS SDK defaults mode.
	 */
	@Nullable private DefaultsMode defaultsMode;

	/**
	 * Whether to use FIPS endpoints.
	 */
	@Nullable private Boolean fipsEnabled;

	/**
	 * Whether to use dualstack endpoints.
	 */
	@Nullable private Boolean dualstackEnabled;

	/**
	 * Custom endpoint URI to override the default AWS service endpoint.
	 */
	@Nullable private URI endpoint;

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

	public SyncClientProperties getSyncClient() {
		return this.syncClient;
	}

	public void setSyncClient(SyncClientProperties syncClient) {
		this.syncClient = syncClient;
	}

	public AsyncClientProperties getAsyncClient() {
		return this.asyncClient;
	}

	public void setAsyncClient(AsyncClientProperties asyncClient) {
		this.asyncClient = asyncClient;
	}

	public String getSessionToken() {
		return this.sessionToken;
	}

	public void setSessionToken(String sessionToken) {
		this.sessionToken = sessionToken;
	}

	@Nullable public ProfileProperties getProfile() {
		return this.profile;
	}

	public void setProfile(@Nullable ProfileProperties profile) {
		this.profile = profile;
	}

	public boolean isInstanceProfile() {
		return this.instanceProfile;
	}

	public void setInstanceProfile(boolean instanceProfile) {
		this.instanceProfile = instanceProfile;
	}

	public StsProperties getStsProperties() {
		return this.stsProperties;
	}

	public void setStsProperties(StsProperties stsProperties) {
		this.stsProperties = stsProperties;
	}

	@Nullable public DefaultsMode getDefaultsMode() {
		return this.defaultsMode;
	}

	public void setDefaultsMode(@Nullable DefaultsMode defaultsMode) {
		this.defaultsMode = defaultsMode;
	}

	@Nullable public Boolean getFipsEnabled() {
		return this.fipsEnabled;
	}

	public void setFipsEnabled(@Nullable Boolean fipsEnabled) {
		this.fipsEnabled = fipsEnabled;
	}

	@Nullable public Boolean getDualstackEnabled() {
		return this.dualstackEnabled;
	}

	public void setDualstackEnabled(@Nullable Boolean dualstackEnabled) {
		this.dualstackEnabled = dualstackEnabled;
	}

	@Nullable public URI getEndpoint() {
		return this.endpoint;
	}

	public void setEndpoint(@Nullable URI endpoint) {
		this.endpoint = endpoint;
	}

}
