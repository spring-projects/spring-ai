package org.springframework.ai.autoconfigure.wenxin;

import org.springframework.ai.wenxin.WenxinChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author lvchzh
 * @since 1.0.0
 */
@ConfigurationProperties(WenxinChatProperties.CONFIG_PREFIX)
public class WenxinChatProperties extends WenxinParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.wenxin.chat";

	public static final String DEFAULT_CHAT_MODEL = "completions";

	private static final Double DEFAULT_TEMPERATURE = 0.7;

	private boolean enable = true;

	//@formatter:off
	private WenxinChatOptions options = WenxinChatOptions.builder()
		.withModel(DEFAULT_CHAT_MODEL)
		.withTemperature(DEFAULT_TEMPERATURE.floatValue())
		.build();
	//@formatter:on
	public WenxinChatOptions getOptions() {
		return options;
	}

	public void setOptions(WenxinChatOptions options) {
		this.options = options;
	}

	public boolean isEnable() {
		return enable;
	}

	public void setEnable(boolean enable) {
		this.enable = enable;
	}

}
