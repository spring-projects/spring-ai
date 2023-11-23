/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.autoconfigure.openai;

import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.client.OpenAiApi;
import com.theokanning.openai.service.OpenAiService;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;

import org.springframework.ai.autoconfigure.NativeHints;
import org.springframework.ai.client.AiClient;
import org.springframework.ai.client.RetryAiClient;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.embedding.RetryEmbeddingClient;
import org.springframework.ai.openai.client.OpenAiClient;
import org.springframework.ai.openai.embedding.OpenAiEmbeddingClient;
import org.springframework.ai.openai.metadata.support.OpenAiHttpResponseHeadersInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StringUtils;

@AutoConfiguration
@ConditionalOnClass(OpenAiService.class)
@EnableConfigurationProperties(OpenAiProperties.class)
@ImportRuntimeHints(NativeHints.class)
public class OpenAiAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public AiClient openAiClient(OpenAiProperties openAiProperties, RetryTemplate retryTemplate) {

		OpenAiService openAiService = theoOpenAiService(openAiProperties, openAiProperties.getBaseUrl(),
				openAiProperties.getApiKey(), openAiProperties.getDuration());

		OpenAiClient openAiClient = new OpenAiClient(openAiService);
		openAiClient.setTemperature(openAiProperties.getTemperature());
		openAiClient.setModel(openAiProperties.getModel());

		return (openAiProperties.getRetry().isEnabled()) ? new RetryAiClient(retryTemplate, openAiClient)
				: openAiClient;
	}

	@Bean
	@ConditionalOnMissingBean
	public EmbeddingClient openAiEmbeddingClient(OpenAiProperties openAiProperties, RetryTemplate retryTemplate) {

		OpenAiService openAiService = theoOpenAiService(openAiProperties, openAiProperties.getEmbedding().getBaseUrl(),
				openAiProperties.getEmbedding().getApiKey(), openAiProperties.getDuration());

		var embeddingClient = new OpenAiEmbeddingClient(openAiService, openAiProperties.getEmbedding().getModel());

		return (openAiProperties.getRetry().isEnabled()) ? new RetryEmbeddingClient(retryTemplate, embeddingClient)
				: embeddingClient;
	}

	private OpenAiService theoOpenAiService(OpenAiProperties properties, String baseUrl, String apiKey,
			Duration duration) {

		if ("https://api.openai.com".equals(baseUrl) && !StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException("You must provide an API key with the property name "
					+ OpenAiProperties.CONFIG_PREFIX + ".api-key");
		}

		ObjectMapper mapper = OpenAiService.defaultObjectMapper();

		OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder(OpenAiService.defaultClient(apiKey, duration));

		if (properties.getMetadata().isRateLimitMetricsEnabled()) {
			clientBuilder.addInterceptor(new OpenAiHttpResponseHeadersInterceptor());
		}

		OkHttpClient client = clientBuilder.build();

		// Waiting for https://github.com/TheoKanning/openai-java/issues/249 to be
		// resolved.
		Retrofit retrofit = new Retrofit.Builder().baseUrl(baseUrl)
			.client(client)
			.addConverterFactory(JacksonConverterFactory.create(mapper))
			.addCallAdapterFactory(RxJava2CallAdapterFactory.create())
			.build();

		OpenAiApi api = retrofit.create(OpenAiApi.class);

		return new OpenAiService(api);
	}

	@Bean
	@ConditionalOnMissingBean
	public RetryTemplate retryTemplate(OpenAiProperties openAiProperties) {
		var retry = openAiProperties.getRetry();
		// currentInterval = Math.min(initialInterval * Math.pow(multiplier, retryNum),
		// maxInterval)}
		return RetryTemplate.builder()
			.maxAttempts(retry.getMaxAttempts())
			.exponentialBackoff(retry.getInitialInterval(), retry.getBackoffIntervalMultiplier(),
					retry.getMaximumBackoffDuration())
			.build();
	}

}
