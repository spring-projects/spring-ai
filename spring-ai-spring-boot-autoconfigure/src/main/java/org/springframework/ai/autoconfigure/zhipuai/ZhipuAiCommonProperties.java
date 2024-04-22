package org.springframework.ai.autoconfigure.zhipuai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Ricken Bazolo
 */
@ConfigurationProperties(ZhipuAiCommonProperties.CONFIG_PREFIX)
public class ZhipuAiCommonProperties extends ZhipuAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.zhipuai";

	public static final String DEFAULT_BASE_URL = "https://open.bigmodel.cn";

	public ZhipuAiCommonProperties() {
		super.setBaseUrl(DEFAULT_BASE_URL);
	}

}
