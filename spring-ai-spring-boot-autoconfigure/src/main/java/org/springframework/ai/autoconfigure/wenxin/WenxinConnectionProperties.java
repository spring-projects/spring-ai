package org.springframework.ai.autoconfigure.wenxin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author lvchzh
 * @date 2024年05月14日 下午5:55
 * @description:
 */
@ConfigurationProperties(WenxinConnectionProperties.CONFIG_PREFIX)
public class WenxinConnectionProperties extends WenxinParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.wenxin";

	public static final String DEFAULT_BASE_URL = "https://aip.baidubce.com";

	public WenxinConnectionProperties() {
		super.setBaseUrl(DEFAULT_BASE_URL);
	}

}
