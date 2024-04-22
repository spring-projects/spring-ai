package org.springframework.ai.autoconfigure.zhipuai;

import org.springframework.ai.zhipuai.ZhipuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhipuAiApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * @author Ricken Bazolo
 */
@ConfigurationProperties(ZhipuAiChatProperties.CONFIG_PREFIX)
public class ZhipuAiChatProperties extends ZhipuAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.zhipuai.chat";

	public static final String DEFAULT_CHAT_MODEL = ZhipuAiApi.ChatModel.GLM_4.getValue();

	private static final Double DEFAULT_TEMPERATURE = 0.95;

	private static final Float DEFAULT_TOP_P = 0.7f;

	public ZhipuAiChatProperties() {
		super.setBaseUrl(ZhipuAiCommonProperties.DEFAULT_BASE_URL);
	}

	/**
	 * Enable chat client.
	 */
	private boolean enabled = true;

	@NestedConfigurationProperty
	private ZhipuAiChatOptions options = ZhipuAiChatOptions.builder()
		.withModel(DEFAULT_CHAT_MODEL)
		.withTemperature(DEFAULT_TEMPERATURE.floatValue())
		.withTopP(DEFAULT_TOP_P)
		.build();

	public ZhipuAiChatOptions getOptions() {
		return this.options;
	}

	public void setOptions(ZhipuAiChatOptions options) {
		this.options = options;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
