package org.springframework.ai.autoconfigure.openai;

import org.springframework.ai.openai.OpenAiModerationOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for OpenAI moderation.
 *
 * @author Ricken Bazolo
 */
@ConfigurationProperties(OpenAiModerationProperties.CONFIG_PREFIX)
public class OpenAiModerationProperties extends OpenAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.openai.moderation";

	public static final String DEFAULT_MODEL = "text-moderation-latest";

	/**
	 * Enable OpenAI moderation client.
	 */
	private boolean enabled = true;

	@NestedConfigurationProperty
	public OpenAiModerationOptions options = OpenAiModerationOptions.builder().withModel(DEFAULT_MODEL).build();

	public OpenAiModerationOptions getOptions() {
		return options;
	}

	public void setOptions(OpenAiModerationOptions options) {
		this.options = options;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
