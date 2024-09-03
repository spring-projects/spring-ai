/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.qianfan;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.qianfan.api.QianFanApi;
import org.springframework.ai.qianfan.api.QianFanImageApi;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * @author Geng Rong
 */
@SpringBootConfiguration
public class QianFanTestConfiguration {

	@Bean
	public QianFanApi qianFanApi() {
		return new QianFanApi(getApiKey(), getSecretKey());
	}

	@Bean
	public QianFanImageApi qianFanImageApi() {
		return new QianFanImageApi(getApiKey(), getSecretKey());
	}

	private String getApiKey() {
		String apiKey = System.getenv("QIANFAN_API_KEY");
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException(
					"You must provide an API key. Put it in an environment variable under the name QIANFAN_API_KEY");
		}
		return apiKey;
	}

	private String getSecretKey() {
		String apiKey = System.getenv("QIANFAN_SECRET_KEY");
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException(
					"You must provide a secret key. Put it in an environment variable under the name QIANFAN_SECRET_KEY");
		}
		return apiKey;
	}

	@Bean
	public QianFanChatModel qianFanChatModel(QianFanApi api) {
		return new QianFanChatModel(api);
	}

	@Bean
	public EmbeddingModel qianFanEmbeddingModel(QianFanApi api) {
		return new QianFanEmbeddingModel(api);
	}

	@Bean
	public ImageModel qianFanImageModel(QianFanImageApi api) {
		return new QianFanImageModel(api);
	}

}
