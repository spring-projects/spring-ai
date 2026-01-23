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

/**
 * Configuration properties for Bedrock AWS connection using profile.
 *
 * @author Baojun Jiang
 */
public class ProfileProperties {

	/**
	 * Name of the profile to use.
	 */
	private String name;

	/**
	 * (optional) Path to the credentials file. default: ~/.aws/credentials
	 */
	private String credentialsPath;

	/**
	 * (optional) Path to the configuration file. default: ~/.aws/config
	 */
	private String configurationPath;

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getCredentialsPath() {
		return this.credentialsPath;
	}

	public void setCredentialsPath(String credentialsPath) {
		this.credentialsPath = credentialsPath;
	}

	public String getConfigurationPath() {
		return this.configurationPath;
	}

	public void setConfigurationPath(String configurationPath) {
		this.configurationPath = configurationPath;
	}

}
