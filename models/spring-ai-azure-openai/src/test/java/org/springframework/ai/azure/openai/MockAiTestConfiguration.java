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

package org.springframework.ai.azure.openai;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.log.LogAccessor;
import org.springframework.lang.Nullable;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring {@link Configuration} for AI integration testing using mock objects.
 * <p>
 * This test configuration allows Spring AI framework developers to mock an AI provider's
 * APIs with Spring {@link MockMvc} and a test provided Spring Web MVC
 * {@link org.springframework.web.bind.annotation.RestController}.
 * <p>
 * This test configuration makes use of the OkHttp3 {@link MockWebServer} and
 * {@link Dispatcher} to integrate with Spring {@link MockMvc}. This allows you to mock
 * the AI response (e.g. JSON) coming back from the AI provider API and let it pass
 * through the underlying AI client library and infrastructure components responsible for
 * accessing the provider's AI with its API all the way back to Spring AI.
 *
 * @author John Blum
 * @see okhttp3.mockwebserver.Dispatcher
 * @see okhttp3.mockwebserver.MockWebServer
 * @see org.springframework.boot.SpringBootConfiguration
 * @see org.springframework.test.web.servlet.MockMvc
 * @since 0.7.0
 */
@Configuration
@SuppressWarnings("unused")
public class MockAiTestConfiguration {

	public static final Charset FALLBACK_CHARSET = StandardCharsets.UTF_8;

	public static final String SPRING_AI_API_PATH = "/spring-ai/api";

	@Bean
	MockWebServerFactoryBean mockWebServer(MockMvc mockMvc) {
		MockWebServerFactoryBean factoryBean = new MockWebServerFactoryBean();
		factoryBean.setDispatcher(new MockMvcDispatcher(mockMvc));
		return factoryBean;
	}

	/**
	 * OkHttp {@link Dispatcher} implementation integrated with Spring Web MVC.
	 *
	 * @see okhttp3.mockwebserver.Dispatcher
	 * @see org.springframework.test.web.servlet.MockMvc
	 */
	static class MockMvcDispatcher extends Dispatcher {

		private final MockMvc mockMvc;

		MockMvcDispatcher(MockMvc mockMvc) {
			Assert.notNull(mockMvc, "Spring MockMvc must not be null");
			this.mockMvc = mockMvc;
		}

		protected MockMvc getMockMvc() {
			return this.mockMvc;
		}

		@Override
		@SuppressWarnings("all")
		public MockResponse dispatch(RecordedRequest request) {

			try {
				MvcResult result = getMockMvc().perform(requestBuilderFrom(request))
					.andExpect(status().isOk())
					.andReturn();

				MockHttpServletResponse response = result.getResponse();

				return mockResponseFrom(response);
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		private RequestBuilder requestBuilderFrom(RecordedRequest request) {

			String requestMethod = request.getMethod();
			String requestPath = resolveRequestPath(request);

			URI uri = URI.create(requestPath);

			Buffer requestBody = request.getBody();

			String content = requestBody.readUtf8();

			return MockMvcRequestBuilders.request(requestMethod, uri).content(content);
		}

		private String resolveRequestPath(RecordedRequest request) {

			String requestPath = request.getPath();
			String pavedRequestPath = StringUtils.hasText(requestPath) ? requestPath : "/";

			return pavedRequestPath.startsWith(SPRING_AI_API_PATH) ? pavedRequestPath
					: SPRING_AI_API_PATH.concat(pavedRequestPath);
		}

		private MockResponse mockResponseFrom(MockHttpServletResponse response) {

			MockResponse mockResponse = new MockResponse();

			for (String headerName : response.getHeaderNames()) {
				String headerValue = response.getHeader(headerName);
				if (StringUtils.hasText(headerValue)) {
					mockResponse.addHeader(headerName, headerValue);
				}
			}

			mockResponse.setResponseCode(response.getStatus());
			mockResponse.setBody(getBody(response));

			return mockResponse;
		}

		private String getBody(MockHttpServletResponse response) {

			Charset responseCharacterEncoding = Charset.forName(response.getCharacterEncoding());

			try {
				return response.getContentAsString(FALLBACK_CHARSET);
			}
			catch (UnsupportedEncodingException e) {
				throw new RuntimeException("Failed to decode content using HttpServletResponse Charset [%s]"
					.formatted(responseCharacterEncoding), e);
			}
		}

	}

	/**
	 * Spring {@link FactoryBean} used to construct, configure and initialize the
	 * {@link MockWebServer} inside the Spring container.
	 * <p>
	 * Unfortunately, {@link MockWebServerFactoryBean} cannot implement the Spring
	 * {@link SmartLifecycle} interface as originally intended. The problem is, the
	 * {@link MockWebServer} class is poorly designed and does not adhere to the
	 * {@literal Open/Closed principle}:
	 * <ul>
	 * <li>The class does not provide a isRunning() lifecycle method, despite the start()
	 * and shutdown() methods</li>
	 * <li>The MockWebServer.started is a private state variable</li>
	 * <li>The overridden before() function is protected</li>
	 * <li>The class is final and cannot be extended</li>
	 * <li>Calling MockWebServer.url(:String) is needed to construct Retrofit client in
	 * the theoOpenAiService bean necessarily starts the MockWebServer</li>
	 * </ul>
	 * <p>
	 * TODO: Figure out a way to implement the Spring {@link SmartLifecycle} interface
	 * without scrambling bean dependencies, bean phases, and other bean lifecycle
	 * methods.
	 *
	 * @see org.springframework.beans.factory.FactoryBean
	 * @see org.springframework.beans.factory.DisposableBean
	 * @see org.springframework.beans.factory.InitializingBean
	 * @see okhttp3.mockwebserver.MockWebServer
	 */
	static class MockWebServerFactoryBean implements FactoryBean<MockWebServer>, InitializingBean, DisposableBean {

		private final LogAccessor logger = new LogAccessor(getClass().getName());

		private final Queue<MockResponse> queuedResponses = new ConcurrentLinkedDeque<>();

		private Dispatcher dispatcher;

		private MockWebServer mockWebServer;

		protected Optional<Dispatcher> getDispatcher() {
			return Optional.ofNullable(this.dispatcher);
		}

		public void setDispatcher(@Nullable Dispatcher dispatcher) {
			this.dispatcher = dispatcher;
		}

		protected LogAccessor getLogger() {
			return logger;
		}

		@Override
		public MockWebServer getObject() {
			return start(this.mockWebServer);
		}

		@Override
		public Class<?> getObjectType() {
			return MockWebServer.class;
		}

		@Override
		public void afterPropertiesSet() {
			this.mockWebServer = new MockWebServer();
			this.queuedResponses.forEach(this.mockWebServer::enqueue);
			getDispatcher().ifPresent(this.mockWebServer::setDispatcher);
		}

		public MockWebServerFactoryBean enqueue(MockResponse response) {
			Assert.notNull(response, "MockResponse must not be null");
			this.queuedResponses.add(response);
			return this;
		}

		@Override
		public void destroy() {

			try {
				this.mockWebServer.shutdown();
			}
			catch (IOException e) {
				getLogger().warn("MockWebServer was not shutdown correctly: " + e.getMessage());
				getLogger().trace(e, "MockWebServer shutdown failure");
			}
		}

		private MockWebServer start(MockWebServer webServer) {

			try {
				webServer.start();
				return webServer;
			}
			catch (IOException e) {
				throw new IllegalStateException("Failed to start MockWebServer", e);
			}
		}

	}

}
