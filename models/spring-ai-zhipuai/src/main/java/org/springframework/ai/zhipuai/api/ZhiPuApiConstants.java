package org.springframework.ai.zhipuai.api;

import org.springframework.ai.observation.conventions.AiProvider;

/**
 * Common value constants for ZhiPu api.
 *
 * @author Piotr Olaszewski
 * @since 1.0.0 M2
 */
public final class ZhiPuApiConstants {

	public static final String DEFAULT_BASE_URL = "https://open.bigmodel.cn/api/paas";

	public static final String PROVIDER_NAME = AiProvider.ZHIPUAI.value();

}
