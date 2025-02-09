package org.springframework.ai.model.mistralai.autoconfigure;

import org.springframework.ai.mistralai.moderation.MistralAiModerationOptions;
import org.springframework.ai.mistralai.api.MistralAiModerationApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * @author Ricken Bazolo
 */
@ConfigurationProperties(MistralAiModerationProperties.CONFIG_PREFIX)
public class MistralAiModerationProperties extends MistralAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.mistralai.moderation";

	private static final String DEFAULT_MODERATION_MODEL = MistralAiModerationApi.Model.MISTRAL_MODERATION.getValue();

	@NestedConfigurationProperty
	private MistralAiModerationOptions options = MistralAiModerationOptions.builder()
		.model(DEFAULT_MODERATION_MODEL)
		.build();

	public MistralAiModerationProperties() {
		super.setBaseUrl(MistralAiCommonProperties.DEFAULT_BASE_URL);
	}

	public MistralAiModerationOptions getOptions() {
		return this.options;
	}

	public void setOptions(MistralAiModerationOptions options) {
		this.options = options;
	}

}
