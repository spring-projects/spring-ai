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

package org.springframework.ai.azure.openai;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.test.web.servlet.MockMvc;

import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockWebServer;

/**
 * {@link SpringBootConfiguration} for testing {@literal Azure OpenAI's} API using mock
 * objects.
 * <p>
 * This test configuration allows Spring AI framework developers to mock Azure OpenAI's
 * API with Spring {@link MockMvc} and a test provided Spring Web MVC
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
@Profile("spring-ai-azure-openai-mocks")
@Import(MockAiTestConfiguration.class)
@SuppressWarnings("unused")
public class MockAzureOpenAiTestConfiguration {

	@Bean
	OpenAIClient microsoftAzureOpenAiClient(MockWebServer webServer) {

		HttpUrl baseUrl = webServer.url(MockAiTestConfiguration.SPRING_AI_API_PATH);

		return new OpenAIClientBuilder().endpoint(baseUrl.toString()).buildClient();
	}

	@Bean
	AzureOpenAiChatClient azureOpenAiChatClient(OpenAIClient microsoftAzureOpenAiClient) {
		return new AzureOpenAiChatClient(microsoftAzureOpenAiClient);
	}

}
