package org.springframework.ai.autoconfigure.azure.openai;

import org.springframework.ai.azure.openai.AzureOpenAiImageOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for Azure OpenAI image generation options.
 *
 * @author Benoit Moussaud
 * @since 1.0.0 M1
 */
@ConfigurationProperties(AzureOpenAiImageOptionsProperties.CONFIG_PREFIX)
public class AzureOpenAiImageOptionsProperties {

	public static final String CONFIG_PREFIX = "spring.ai.azure.openai.image";

	/**
	 * Enable Azure OpenAI chat client.
	 */
	private boolean enabled = true;

	@NestedConfigurationProperty
	private AzureOpenAiImageOptions options = AzureOpenAiImageOptions.builder().build();

	public AzureOpenAiImageOptions getOptions() {
		return options;
	}

	public void setOptions(AzureOpenAiImageOptions options) {
		this.options = options;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
