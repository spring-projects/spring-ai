/*
 * Copyright 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.hunyuan.api;

import org.springframework.ai.observation.conventions.AiProvider;

/**
 * Constants for HunYuan API.
 *
 * @author Guo Junyu
 */
public final class HunYuanConstants {

	public static final String DEFAULT_BASE_URL = "https://hunyuan.tencentcloudapi.com";

	public static final String DEFAULT_CHAT_HOST = "hunyuan.tencentcloudapi.com";

	public static final String PROVIDER_NAME = AiProvider.HUNYUAN.value();

	public static final String DEFAULT_CHAT_ACTION = "ChatCompletions";

	public static final String DEFAULT_VERSION = "2023-09-01";

	public static final String DEFAULT_SERVICE  = "hunyuan";

	public static final String DEFAULT_ALGORITHM = "TC3-HMAC-SHA256";

	public static final String CT_JSON = "application/json; charset=utf-8";

	private HunYuanConstants() {

	}

}
