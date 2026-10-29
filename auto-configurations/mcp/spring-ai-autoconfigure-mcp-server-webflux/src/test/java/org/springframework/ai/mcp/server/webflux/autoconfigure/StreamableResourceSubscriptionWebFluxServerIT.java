/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.mcp.server.webflux.autoconfigure;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.junit.jupiter.api.Test;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.McpToolCallbackAutoConfiguration;
import org.springframework.ai.mcp.client.webflux.autoconfigure.StreamableHttpWebFluxTransportAutoConfiguration;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerJsonMapperAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.ToolCallbackConverterAutoConfiguration;
import org.springframework.ai.mcp.server.webflux.transport.WebFluxStreamableServerTransportProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ResolvableType;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.web.reactive.function.server.RouterFunctions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resource subscriptions over Streamable HTTP when
 * {@code spring.ai.mcp.server.capabilities.resource-subscribe} is enabled.
 *
 * @author Samuel Komane
 */
public class StreamableResourceSubscriptionWebFluxServerIT {

	private static final String RESOURCE_URI = "test://resource";

	private final ApplicationContextRunner serverContextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mcp.server.protocol=STREAMABLE")
		.withConfiguration(AutoConfigurations.of(McpServerAutoConfiguration.class,
				McpServerJsonMapperAutoConfiguration.class, ToolCallbackConverterAutoConfiguration.class,
				McpServerStreamableHttpWebFluxAutoConfiguration.class));

	private final ApplicationContextRunner clientContextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(McpToolCallbackAutoConfiguration.class,
				McpClientAutoConfiguration.class, StreamableHttpWebFluxTransportAutoConfiguration.class));

	@Test
	void clientReceivesUpdatesForSubscribedResource() {
		int serverPort = TestSocketUtils.findAvailableTcpPort();

		this.serverContextRunner.withUserConfiguration(TestResourceConfiguration.class)
			.withPropertyValues("spring.ai.mcp.server.capabilities.resource-subscribe=true")
			.run(serverContext -> {
				DisposableServer httpServer = startHttpServer(serverContext, serverPort);
				try {
					this.clientContextRunner.withUserConfiguration(TestClientConfiguration.class)
						.withPropertyValues(
								"spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:"
										+ serverPort,
								"spring.ai.mcp.client.initialized=false")
						.run(clientContext -> {
							McpSyncClient client = getMcpSyncClient(clientContext);
							McpSchema.InitializeResult initResult = client.initialize();
							assertThat(initResult.capabilities().resources().subscribe()).isTrue();

							client.subscribeResource(new McpSchema.SubscribeRequest(RESOURCE_URI, null));

							McpSyncServer server = serverContext.getBean(McpSyncServer.class);
							server.notifyResourcesUpdated(new McpSchema.ResourcesUpdatedNotification(RESOURCE_URI));

							UpdateRecorder recorder = clientContext.getBean(UpdateRecorder.class);
							assertThat(recorder.latch.await(5, TimeUnit.SECONDS))
								.as("client should be notified of the resource update")
								.isTrue();
							assertThat(recorder.updates.get(0)).singleElement()
								.isInstanceOfSatisfying(TextResourceContents.class,
										contents -> assertThat(contents.text()).isEqualTo("content"));
						});
				}
				finally {
					httpServer.disposeNow();
				}
			});
	}

	private static DisposableServer startHttpServer(ApplicationContext serverContext, int port) {
		WebFluxStreamableServerTransportProvider transport = serverContext
			.getBean(WebFluxStreamableServerTransportProvider.class);
		HttpHandler httpHandler = RouterFunctions.toHttpHandler(transport.getRouterFunction());
		return HttpServer.create().port(port).handle(new ReactorHttpHandlerAdapter(httpHandler)).bindNow();
	}

	private static McpSyncClient getMcpSyncClient(ApplicationContext clientContext) {
		ObjectProvider<List<McpSyncClient>> clients = clientContext
			.getBeanProvider(ResolvableType.forClassWithGenerics(List.class, McpSyncClient.class));
		return clients.getObject().get(0);
	}

	static class UpdateRecorder {

		final CountDownLatch latch = new CountDownLatch(1);

		final List<List<McpSchema.ResourceContents>> updates = new CopyOnWriteArrayList<>();

	}

	static class TestResourceConfiguration {

		@Bean
		List<SyncResourceSpecification> testResources() {
			Resource resource = Resource.builder(RESOURCE_URI, "Test Resource").mimeType("text/plain").build();
			return List.of(new SyncResourceSpecification(resource,
					(exchange, request) -> ReadResourceResult
						.builder(List.of(TextResourceContents.builder(request.uri(), "content").build()))
						.build()));
		}

	}

	static class TestClientConfiguration {

		@Bean
		UpdateRecorder updateRecorder() {
			return new UpdateRecorder();
		}

		@Bean
		McpClientCustomizer<McpClient.SyncSpec> resourceUpdateCustomizer(UpdateRecorder recorder) {
			return (name, spec) -> spec.resourcesUpdateConsumer(contents -> {
				recorder.updates.add(contents);
				recorder.latch.countDown();
			});
		}

	}

}
