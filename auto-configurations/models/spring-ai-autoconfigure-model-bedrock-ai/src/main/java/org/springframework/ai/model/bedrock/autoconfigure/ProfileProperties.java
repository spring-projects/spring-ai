package org.springframework.ai.model.bedrock.autoconfigure;

/**
 * Configuration properties for Bedrock AWS connection using profile.
 *
 * @author Baojun Jiang
 * @since 1.1.0
 */
public class ProfileProperties {

	/**
	 * Name of the profile to use.
	 */
	private String name;

	/**
	 * Path to the credentials file. default: ~/.aws/credentials
	 */
	private String credentialsPath;

	/**
	 * Path to the configuration file. default: ~/.aws/config
	 */
	private String configurationPath;


	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getCredentialsPath() {
		return credentialsPath;
	}

	public void setCredentialsPath(String credentialsPath) {
		this.credentialsPath = credentialsPath;
	}

	public String getConfigurationPath() {
		return configurationPath;
	}

	public void setConfigurationPath(String configurationPath) {
		this.configurationPath = configurationPath;
	}
}
