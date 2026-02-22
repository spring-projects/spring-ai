/*
 * Copyright 2025-2026 the original author or authors.
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

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for AWS STS credential providers.
 *
 * @author Matej Nedic
 */
public class StsProperties {

	/**
	 * STS AssumeRole configuration for cross-account or role-based access.
	 */
	@Nullable
	@NestedConfigurationProperty
	private AssumeRole assumeRole;

	/**
	 * STS WebIdentity configuration for OIDC-based authentication (e.g. EKS IRSA).
	 */
	@Nullable
	@NestedConfigurationProperty
	private WebIdentity webIdentity;

	@Nullable public AssumeRole getAssumeRole() {
		return this.assumeRole;
	}

	public void setAssumeRole(@Nullable AssumeRole assumeRole) {
		this.assumeRole = assumeRole;
	}

	@Nullable public WebIdentity getWebIdentity() {
		return this.webIdentity;
	}

	public void setWebIdentity(@Nullable WebIdentity webIdentity) {
		this.webIdentity = webIdentity;
	}

	/**
	 * Properties for {@code StsAssumeRoleCredentialsProvider}. Requires base credentials
	 * (static, profile, or instance profile) to authenticate the AssumeRole call.
	 */
	public static class AssumeRole {

		/**
		 * ARN of the IAM role to assume.
		 */
		private String roleArn;

		/**
		 * Identifier for the assumed role session. Visible in CloudTrail logs.
		 */
		private String roleSessionName;

		/**
		 * External ID for cross-account role assumption. Required only when the role's
		 * trust policy mandates it.
		 */
		private String externalId;

		/**
		 * Duration of the role session in seconds. Valid range: 900 (15 min) to 43200 (12
		 * hours).
		 */
		private Integer durationSeconds = 3600;

		public String getRoleArn() {
			return this.roleArn;
		}

		public void setRoleArn(String roleArn) {
			this.roleArn = roleArn;
		}

		public String getRoleSessionName() {
			return this.roleSessionName;
		}

		public void setRoleSessionName(String roleSessionName) {
			this.roleSessionName = roleSessionName;
		}

		public String getExternalId() {
			return this.externalId;
		}

		public void setExternalId(String externalId) {
			this.externalId = externalId;
		}

		public Integer getDurationSeconds() {
			return this.durationSeconds;
		}

		public void setDurationSeconds(Integer durationSeconds) {
			this.durationSeconds = durationSeconds;
		}

	}

	/**
	 * Properties for {@code StsWebIdentityTokenFileCredentialsProvider}. Uses an OIDC
	 * token file for authentication, no base AWS credentials required.
	 */
	public static class WebIdentity {

		/**
		 * ARN of the IAM role to assume.
		 */
		private String roleArn;

		/**
		 * Identifier for the assumed role session.
		 */
		private String roleSessionName;

		/**
		 * Path to the web identity token file (OIDC token).
		 */
		private String webIdentityTokenFile;

		/**
		 * Duration of the role session in seconds. Valid range: 900 (15 min) to 43200 (12
		 * hours).
		 */
		private Integer durationSeconds = 3600;

		/**
		 * Whether to refresh credentials asynchronously before expiration.
		 */
		private boolean asyncCredentialUpdateEnabled = true;

		public String getRoleArn() {
			return this.roleArn;
		}

		public void setRoleArn(String roleArn) {
			this.roleArn = roleArn;
		}

		public String getRoleSessionName() {
			return this.roleSessionName;
		}

		public void setRoleSessionName(String roleSessionName) {
			this.roleSessionName = roleSessionName;
		}

		public String getWebIdentityTokenFile() {
			return this.webIdentityTokenFile;
		}

		public void setWebIdentityTokenFile(String webIdentityTokenFile) {
			this.webIdentityTokenFile = webIdentityTokenFile;
		}

		public Integer getDurationSeconds() {
			return this.durationSeconds;
		}

		public void setDurationSeconds(Integer durationSeconds) {
			this.durationSeconds = durationSeconds;
		}

		public boolean isAsyncCredentialUpdateEnabled() {
			return this.asyncCredentialUpdateEnabled;
		}

		public void setAsyncCredentialUpdateEnabled(boolean asyncCredentialUpdateEnabled) {
			this.asyncCredentialUpdateEnabled = asyncCredentialUpdateEnabled;
		}

	}

}
