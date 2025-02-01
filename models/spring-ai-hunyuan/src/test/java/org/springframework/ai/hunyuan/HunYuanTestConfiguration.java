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

package org.springframework.ai.hunyuan;

import org.springframework.ai.hunyuan.api.HunYuanApi;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * @author Guo Junyu
 */
@SpringBootConfiguration
public class HunYuanTestConfiguration {

	@Bean
	public HunYuanApi hunYuanApi() {
		var secretId = System.getenv("HUNYUAN_SECRET_ID");
		var secretKey = System.getenv("HUNYUAN_SECRET_KEY");
		if (!StringUtils.hasText(secretId) && !StringUtils.hasText(secretKey)) {
			throw new IllegalArgumentException(
					"Missing HUNYUAN_SECRET_ID & HUNYUAN_SECRET_KEY environment variable. Please set it to your HUNYUAN API info.");
		}
		return new HunYuanApi(secretId, secretKey);
	}

	@Bean
	public HunYuanChatModel hunYuanChatModel(HunYuanApi hunYuanApi) {
		return new HunYuanChatModel(hunYuanApi);
	}

}
