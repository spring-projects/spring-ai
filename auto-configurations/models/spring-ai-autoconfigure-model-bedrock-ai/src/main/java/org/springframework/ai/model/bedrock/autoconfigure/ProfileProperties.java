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

package org.springframework.ai.model.bedrock.autoconfigure;

import org.jspecify.annotations.Nullable;

/**
 * Configuration properties for Bedrock AWS connection using profile.
 *
 * @author Baojun Jiang
 */
public class ProfileProperties {

	/**
	 * Name of the profile to use.
	 */
	private @Nullable String name;

	/**
	 * (optional) Path to the credentials file. default: ~/.aws/credentials
	 */
	private @Nullable String credentialsPath;

	/**
	 * (optional) Path to the configuration file. default: ~/.aws/config
	 */
	private @Nullable String configurationPath;

	public @Nullable String getName() {
		return this.name;
	}

	public void setName(@Nullable String name) {
		this.name = name;
	}

	public @Nullable String getCredentialsPath() {
		return this.credentialsPath;
	}

	public void setCredentialsPath(@Nullable String credentialsPath) {
		this.credentialsPath = credentialsPath;
	}

	public @Nullable String getConfigurationPath() {
		return this.configurationPath;
	}

	public void setConfigurationPath(@Nullable String configurationPath) {
		this.configurationPath = configurationPath;
	}

}
