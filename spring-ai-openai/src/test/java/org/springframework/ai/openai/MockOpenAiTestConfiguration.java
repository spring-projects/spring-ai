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

package org.springframework.ai.openai;

import static org.springframework.ai.test.config.MockAiTestConfiguration.SPRING_AI_API_PATH;

import java.time.Duration;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.client.OpenAiApi;
import com.theokanning.openai.service.OpenAiService;

import org.springframework.ai.openai.client.OpenAiClient;
import org.springframework.ai.openai.metadata.support.OpenAiHttpResponseHeadersInterceptor;
import org.springframework.ai.test.config.MockAiTestConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.test.web.servlet.MockMvc;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockWebServer;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * {@link SpringBootConfiguration} for testing {@literal OpenAI's} API using mock objects.
 * <p>
 * This test configuration allows Spring AI framework developers to mock OpenAI's API with
 * Spring {@link MockMvc} and a test provided Spring Web MVC
 * {@link org.springframework.web.bind.annotation.RestController}.
 * <p>
 * This test configuration makes use of the OkHttp3 {@link MockWebServer} and
 * {@link Dispatcher} to integrate with Spring {@link MockMvc}.
 *
 * @author John Blum
 * @see org.springframework.boot.SpringBootConfiguration
 * @see org.springframework.ai.test.config.MockAiTestConfiguration
 * @since 0.7.0
 */
@SpringBootConfiguration
@Profile("spring-ai-openai-mocks")
@Import(MockAiTestConfiguration.class)
@SuppressWarnings("unused")
public class MockOpenAiTestConfiguration {

	@Bean
	OpenAiService theoOpenAiService(MockWebServer webServer) {

		String apiKey = UUID.randomUUID().toString();
		Duration timeout = Duration.ofSeconds(60);

		ObjectMapper objectMapper = OpenAiService.defaultObjectMapper();

		OkHttpClient httpClient = new OkHttpClient.Builder(OpenAiService.defaultClient(apiKey, timeout))
			.addInterceptor(new OpenAiHttpResponseHeadersInterceptor())
			.build();

		HttpUrl baseUrl = webServer.url(SPRING_AI_API_PATH.concat("/"));

		Retrofit retrofit = new Retrofit.Builder().baseUrl(baseUrl)
			.addConverterFactory(JacksonConverterFactory.create(objectMapper))
			.addCallAdapterFactory(RxJava2CallAdapterFactory.create())
			.client(httpClient)
			.build();

		OpenAiApi api = retrofit.create(OpenAiApi.class);

		return new OpenAiService(api);
	}

	@Bean
	OpenAiClient apiClient(OpenAiService openAiService) {
		return new OpenAiClient(openAiService);
	}

}
