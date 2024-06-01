package org.springframework.ai.autoconfigure.dashscope;

import org.springframework.ai.dashscope.DashscopeChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * @author Nottyjay Ji
 */
@ConfigurationProperties(DashscopeChatProperties.CONFIG_PREFIX)

public class DashscopeChatProperties extends DashscopeParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.dashscope.chat";

	public static final String DEFAULT_CHAT_MODEL = "qwen-plus";

	private static final Double DEFAULT_TEMPERATURE = 0.7;

	/**
	 * Enable Dashscope chat client.
	 */
	private boolean enabled = true;

	@NestedConfigurationProperty
	private DashscopeChatOptions options = DashscopeChatOptions.builder()
		.withModel(DEFAULT_CHAT_MODEL)
		.withTemperature(DEFAULT_TEMPERATURE.floatValue())
		.build();

	public DashscopeChatOptions getOptions() {
		return options;
	}

	public void setOptions(DashscopeChatOptions options) {
		this.options = options;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
