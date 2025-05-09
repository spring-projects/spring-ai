/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.http.client.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.*;
import org.springframework.web.client.RestClient;

/**
 * {@link AutoConfiguration Auto-configuration} for AI rest-client.
 *
 * @author Song Jaegeun
 */
@AutoConfiguration
@ConditionalOnClass({ RestClient.class })
@EnableConfigurationProperties({ SpringAiRestClientProperties.class })
public class SpringAiRestClientAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(RestClientCustomizer.class)
	public RestClientCustomizer restClientCustomizer(SpringAiRestClientProperties props) {
		// RestClient.Builder is not registered as a bean in the context,
		// so there's no need to use @ConditionalOnMissingBean(RestClient.Builder.class).
		// Spring Boot will automatically apply this RestClientCustomizer
		// to any RestClient.Builder instance created via RestClient.create().
		return restClientBuilder -> {
			ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
				.withConnectTimeout(props.getConnectionTimeout())
				.withReadTimeout(props.getReadTimeout());

			ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.detect().build(settings);
			restClientBuilder.requestFactory(factory);
		};
	}

}
