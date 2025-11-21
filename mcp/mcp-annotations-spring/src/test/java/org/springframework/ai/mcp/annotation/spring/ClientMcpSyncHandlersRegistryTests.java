/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.mcp.annotation.spring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springaicommunity.mcp.annotation.McpElicitation;
import org.springaicommunity.mcp.annotation.McpLogging;
import org.springaicommunity.mcp.annotation.McpProgress;
import org.springaicommunity.mcp.annotation.McpPromptListChanged;
import org.springaicommunity.mcp.annotation.McpResourceListChanged;
import org.springaicommunity.mcp.annotation.McpSampling;
import org.springaicommunity.mcp.annotation.McpToolListChanged;

import org.springframework.aop.framework.autoproxy.AutoProxyUtils;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

class ClientMcpSyncHandlersRegistryTests {

	@Test
	void getCapabilitiesPerClient() {
		var registry = new ClientMcpSyncHandlersRegistry();
		var beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerBeanDefinition("myConfig",
				BeanDefinitionBuilder.genericBeanDefinition(ClientCapabilitiesConfiguration.class).getBeanDefinition());
		registry.postProcessBeanFactory(beanFactory);

		assertThat(registry.getCapabilities("client-1").elicitation()).isNotNull();
		assertThat(registry.getCapabilities("client-2").elicitation()).isNotNull();
		assertThat(registry.getCapabilities("client-3").elicitation()).isNotNull();

		assertThat(registry.getCapabilities("client-1").sampling()).isNotNull();
		assertThat(registry.getCapabilities("client-2").sampling()).isNull();
		assertThat(registry.getCapabilities("client-3").sampling()).isNull();

		assertThat(registry.getCapabilities("client-1").roots()).isNull();
		assertThat(registry.getCapabilities("client-2").roots()).isNull();
		assertThat(registry.getCapabilities("client-3").roots()).isNull();

		assertThat(registry.getCapabilities("client-1").experimental()).isNull();
		assertThat(registry.getCapabilities("client-2").experimental()).isNull();
		assertThat(registry.getCapabilities("client-3").experimental()).isNull();

		assertThat(registry.getCapabilities("client-unknown").sampling()).isNull();
		assertThat(registry.getCapabilities("client-unknown").elicitation()).isNull();
		assertThat(registry.getCapabilities("client-unknown").roots()).isNull();
	}

	@Test
	void twoHandlersElicitation() {
		var registry = new ClientMcpSyncHandlersRegistry();
		var beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerBeanDefinition("firstConfig",
				BeanDefinitionBuilder.genericBeanDefinition(DoubleElicitationHandlerConfiguration.First.class)
					.getBeanDefinition());
		beanFactory.registerBeanDefinition("secondConfig",
				BeanDefinitionBuilder.genericBeanDefinition(DoubleElicitationHandlerConfiguration.Second.class)
					.getBeanDefinition());
		assertThatThrownBy(() -> registry.postProcessBeanFactory(beanFactory))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage(
					"Found 2 elicitation handlers for client [client-1], found in bean with names [firstConfig, secondConfig]. Only one @McpElicitation handler is allowed per client");
	}

	@Test
	void twoHandlersSameBeanElicitation() {
		var registry = new ClientMcpSyncHandlersRegistry();
		var beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerBeanDefinition("elicitationConfig",
				BeanDefinitionBuilder.genericBeanDefinition(DoubleElicitationHandlerConfiguration.TwoHandlers.class)
					.getBeanDefinition());
		assertThatThrownBy(() -> registry.postProcessBeanFactory(beanFactory))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage(
					"Found 2 elicitation handlers for client [client-1], found in bean with names [elicitationConfig]. Only one @McpElicitation handler is allowed per client");
	}

	@Test
	void twoHandlersSampling() {
		var registry = new ClientMcpSyncHandlersRegistry();
		var beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerBeanDefinition("firstConfig",
				BeanDefinitionBuilder.genericBeanDefinition(DoubleSamplingHandlerConfiguration.First.class)
					.getBeanDefinition());
		beanFactory.registerBeanDefinition("secondConfig",
				BeanDefinitionBuilder.genericBeanDefinition(DoubleSamplingHandlerConfiguration.Second.class)
					.getBeanDefinition());
		assertThatThrownBy(() -> registry.postProcessBeanFactory(beanFactory))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage(
					"Found 2 sampling handlers for client [client-1], found in bean with names [firstConfig, secondConfig]. Only one @McpSampling handler is allowed per client");
	}

	@Test
	void twoHandlersSameBeanSampling() {
		var registry = new ClientMcpSyncHandlersRegistry();
		var beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerBeanDefinition("samplingConfig",
				BeanDefinitionBuilder.genericBeanDefinition(DoubleSamplingHandlerConfiguration.TwoHandlers.class)
					.getBeanDefinition());
		assertThatThrownBy(() -> registry.postProcessBeanFactory(beanFactory))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage(
					"Found 2 sampling handlers for client [client-1], found in bean with names [samplingConfig]. Only one @McpSampling handler is allowed per client");
	}

	@Test
	void elicitation() {
		var registry = new ClientMcpSyncHandlersRegistry();
		var beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerBeanDefinition("myConfig",
				BeanDefinitionBuilder.genericBeanDefinition(HandlersConfiguration.class).getBeanDefinition());
		registry.postProcessBeanFactory(beanFactory);
		registry.afterSingletonsInstantiated();

		var request = McpSchema.ElicitRequest.builder().message("Elicit request").progressToken("token-12345").build();
		var response = registry.handleElicitation("client-1", request);

		assertThat(response.content()).hasSize(1).containsEntry("message", "Elicit request");
		assertThat(response.action()).isEqualTo(McpSchema.ElicitResult.Action.ACCEPT);
	}

	@Test
	void missingElicitationHandler() {
		var registry = new ClientMcpSyncHandlersRegistry();
		var beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerBeanDefinition("myConfig",
				BeanDefinitionBuilder.genericBeanDefinition(HandlersConfiguration.class).getBeanDefinition());
		registry.postProcessBeanFactory(beanFactory);
		registry.afterSingletonsInstantiated();

		var request = McpSchema.ElicitRequest.builder().message("Elicit request").progressToken("token-12345").build();
		assertThatThrownBy(() -> registry.handleElicitation("client-unknown", request))
			.hasMessage("Elicitation not supported")
			.asInstanceOf(type(McpError.class))
			.extracting(McpError::getJsonRpcError)
			.satisfies(error -> assertThat(error.data())
				.isEqualTo(Map.of("reason", "Client does not have elicitation capability")))
			.satisfies(error -> assertThat(error.code()).isEqualTo(McpSchema.ErrorCodes.METHOD_NOT_FOUND));
	}

	@Test
	void sampling() {
		var registry = new ClientMcpSyncHandlersRegistry();
		var beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerBeanDefinition("myConfig",
				BeanDefinitionBuilder.genericBeanDefinition(HandlersConfiguration.class).getBeanDefinition());
		registry.postProcessBeanFactory(beanFactory);
		registry.afterSingletonsInstantiated();

		var request = McpSchema.CreateMessageRequest.builder()
			.messages(List
				.of(new McpSchema.SamplingMessage(McpSchema.Role.USER, new McpSchema.TextContent("Tell a joke"))))
			.build();
		var response = registry.handleSampling("client-1", request);

		assertThat(response.content()).isInstanceOf(McpSchema.TextContent.class);
		assertThat(response.model()).isEqualTo("testgpt-42.5");
		McpSchema.TextContent content = (McpSchema.TextContent) response.content();
		assertThat(content.text()).isEqualTo("Tell a joke");
	}

	@Test
	void missingSamplingHandler() {
		var registry = new ClientMcpSyncHandlersRegistry();
		var beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerBeanDefinition("myConfig",
				BeanDefinitionBuilder.genericBeanDefinition(HandlersConfiguration.class).getBeanDefinition());
		registry.postProcessBeanFactory(beanFactory);
		registry.afterSingletonsInstantiated();

		var request = McpSchema.CreateMessageRequest.builder()
			.messages(List
				.of(new McpSchema.SamplingMessage(McpSchema.Role.USER, new McpSchema.TextContent("Tell a joke"))))
			.build();
		assertThatThrownBy(() -> registry.handleSampling("client-unknown", request))
			.hasMessage("Sampling not supported")
			.asInstanceOf(type(McpError.class))
			.extracting(McpError::getJsonRpcError)
			.satisfies(error -> assertThat(error.data())
				.isEqualTo(Map.of("reason", "Client does not have sampling capability")))
			.satisfies(error -> assertThat(error.code()).isEqualTo(McpSchema.ErrorCodes.METHOD_NOT_FOUND));
	}

	@Test
	void logging() {
		var registry = new ClientMcpSyncHandlersRegistry();
		var beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerBeanDefinition("myConfig",
				BeanDefinitionBuilder.genericBeanDefinition(HandlersConfiguration.class).getBeanDefinition());
		registry.postProcessBeanFactory(beanFactory);
		registry.afterSingletonsInstantiated();
		var handlers = beanFactory.getBean(HandlersConfiguration.class);

		var logRequest = McpSchema.LoggingMessageNotification.builder()
			.data("Hello world")
			.logger("log-me")
			.level(McpSchema.LoggingLevel.INFO)
			.build();

		registry.handleLogging("client-1", logRequest);
		assertThat(handlers.getCalls()).hasSize(2)
			.containsExactlyInAnyOrder(new HandlersConfiguration.Call("handleLoggingMessage", logRequest),
					new HandlersConfiguration.Call("handleLoggingMessageAgain", logRequest));
	}

	@Test
	void progress() {
		var registry = new ClientMcpSyncHandlersRegistry();
		var beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerBeanDefinition("myConfig",
				BeanDefinitionBuilder.genericBeanDefinition(HandlersConfiguration.class).getBeanDefinition());
		registry.postProcessBeanFactory(beanFactory);
		registry.afterSingletonsInstantiated();
		var handlers = beanFactory.getBean(HandlersConfiguration.class);

		var progressRequest = new McpSchema.ProgressNotification("progress-12345", 13.37, 100., "progressing ...");

		registry.handleProgress("client-1", progressRequest);
		assertThat(handlers.getCalls()).hasSize(2)
			.containsExactlyInAnyOrder(new HandlersConfiguration.Call("handleProgress", progressRequest),
					new HandlersConfiguration.Call("handleProgressAgain", progressRequest));
	}

	@Test
	void toolListChanged() {
		var registry = new ClientMcpSyncHandlersRegistry();
		var beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerBeanDefinition("myConfig",
				BeanDefinitionBuilder.genericBeanDefinition(HandlersConfiguration.class).getBeanDefinition());
		registry.postProcessBeanFactory(beanFactory);
		registry.afterSingletonsInstantiated();
		var handlers = beanFactory.getBean(HandlersConfiguration.class);

		List<McpSchema.Tool> updatedTools = List.of(McpSchema.Tool.builder().name("tool-1").build(),
				McpSchema.Tool.builder().name("tool-2").build());

		registry.handleToolListChanged("client-1", updatedTools);
		assertThat(handlers.getCalls()).hasSize(2)
			.containsExactlyInAnyOrder(new HandlersConfiguration.Call("handleToolListChanged", updatedTools),
					new HandlersConfiguration.Call("handleToolListChangedAgain", updatedTools));
	}

	@Test
	void promptListChanged() {
		var registry = new ClientMcpSyncHandlersRegistry();
		var beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerBeanDefinition("myConfig",
				BeanDefinitionBuilder.genericBeanDefinition(HandlersConfiguration.class).getBeanDefinition());
		registry.postProcessBeanFactory(beanFactory);
		registry.afterSingletonsInstantiated();
		var handlers = beanFactory.getBean(HandlersConfiguration.class);

		List<McpSchema.Prompt> updatedPrompts = List.of(
				new McpSchema.Prompt("prompt-1", "a test prompt", Collections.emptyList()),
				new McpSchema.Prompt("prompt-2", "another test prompt", Collections.emptyList()));

		registry.handlePromptListChanged("client-1", updatedPrompts);
		assertThat(handlers.getCalls()).hasSize(2)
			.containsExactlyInAnyOrder(new HandlersConfiguration.Call("handlePromptListChanged", updatedPrompts),
					new HandlersConfiguration.Call("handlePromptListChangedAgain", updatedPrompts));
	}

	@Test
	void resourceListChanged() {
		var registry = new ClientMcpSyncHandlersRegistry();
		var beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerBeanDefinition("myConfig",
				BeanDefinitionBuilder.genericBeanDefinition(HandlersConfiguration.class).getBeanDefinition());
		registry.postProcessBeanFactory(beanFactory);
		registry.afterSingletonsInstantiated();
		var handlers = beanFactory.getBean(HandlersConfiguration.class);

		List<McpSchema.Resource> updatedResources = List.of(
				McpSchema.Resource.builder().name("resource-1").uri("file:///resource/1").build(),
				McpSchema.Resource.builder().name("resource-2").uri("file:///resource/2").build());

		registry.handleResourceListChanged("client-1", updatedResources);
		assertThat(handlers.getCalls()).hasSize(2)
			.containsExactlyInAnyOrder(new HandlersConfiguration.Call("handleResourceListChanged", updatedResources),
					new HandlersConfiguration.Call("handleResourceListChangedAgain", updatedResources));
	}

	@Test
	void supportsNonResolvableTypes() {
		var registry = new ClientMcpSyncHandlersRegistry();
		var beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerBeanDefinition("myConfig",
				BeanDefinitionBuilder.genericBeanDefinition(ClientCapabilitiesConfiguration.class.getName())
					.getBeanDefinition());
		registry.postProcessBeanFactory(beanFactory);

		assertThat(registry.getCapabilities("client-1").elicitation()).isNotNull();
	}

	@Test
	void supportsProxiedClass() {
		var registry = new ClientMcpSyncHandlersRegistry();
		var beanFactory = new DefaultListableBeanFactory();
		var beanDefinition = BeanDefinitionBuilder.genericBeanDefinition(Object.class).getBeanDefinition();
		beanDefinition.setAttribute(AutoProxyUtils.ORIGINAL_TARGET_CLASS_ATTRIBUTE,
				ClientCapabilitiesConfiguration.class);
		beanFactory.registerBeanDefinition("myConfig", beanDefinition);

		registry.postProcessBeanFactory(beanFactory);

		assertThat(registry.getCapabilities("client-1").elicitation()).isNotNull();
	}

	@Test
	void skipsUnknownBeanClass() {
		var registry = new ClientMcpAsyncHandlersRegistry();
		var beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerBeanDefinition("myConfig",
				BeanDefinitionBuilder.genericBeanDefinition().getBeanDefinition());

		assertThatNoException().isThrownBy(() -> registry.postProcessBeanFactory(beanFactory));
	}

	static class ClientCapabilitiesConfiguration {

		@McpElicitation(clients = { "client-1", "client-2" })
		public McpSchema.ElicitResult elicitationHandler1(McpSchema.ElicitRequest request) {
			return null;
		}

		@McpElicitation(clients = { "client-3" })
		public McpSchema.ElicitResult elicitationHandler2(McpSchema.ElicitRequest request) {
			return null;
		}

		@McpSampling(clients = { "client-1" })
		public McpSchema.CreateMessageResult samplingHandler(McpSchema.CreateMessageRequest request) {
			return null;
		}

	}

	static class DoubleElicitationHandlerConfiguration {

		static class First {

			@McpElicitation(clients = { "client-1" })
			public McpSchema.ElicitResult elicitationHandler1(McpSchema.ElicitRequest request) {
				return null;
			}

		}

		static class Second {

			@McpElicitation(clients = { "client-1" })
			public McpSchema.ElicitResult elicitationHandler2(McpSchema.ElicitRequest request) {
				return null;
			}

		}

		static class TwoHandlers {

			@McpElicitation(clients = { "client-1" })
			public McpSchema.ElicitResult elicitationHandler1(McpSchema.ElicitRequest request) {
				return null;
			}

			@McpElicitation(clients = { "client-1" })
			public McpSchema.ElicitResult elicitationHandler2(McpSchema.ElicitRequest request) {
				return null;
			}

		}

	}

	static class DoubleSamplingHandlerConfiguration {

		static class First {

			@McpSampling(clients = { "client-1" })
			public McpSchema.CreateMessageResult samplingHandler1(McpSchema.CreateMessageRequest request) {
				return null;
			}

		}

		static class Second {

			@McpSampling(clients = { "client-1" })
			public McpSchema.CreateMessageResult samplingHandler2(McpSchema.CreateMessageRequest request) {
				return null;
			}

		}

		static class TwoHandlers {

			@McpSampling(clients = { "client-1" })
			public McpSchema.CreateMessageResult samplingHandler1(McpSchema.CreateMessageRequest request) {
				return null;
			}

			@McpSampling(clients = { "client-1" })
			public McpSchema.CreateMessageResult samplingHandler2(McpSchema.CreateMessageRequest request) {
				return null;
			}

		}

	}

	static class HandlersConfiguration {

		private final List<Call> calls = new ArrayList<>();

		HandlersConfiguration() {
		}

		List<Call> getCalls() {
			return Collections.unmodifiableList(this.calls);
		}

		@McpElicitation(clients = { "client-1" })
		McpSchema.ElicitResult elicitationHandler(McpSchema.ElicitRequest request) {
			return McpSchema.ElicitResult.builder()
				.message(McpSchema.ElicitResult.Action.ACCEPT)
				.content(Map.of("message", request.message()))
				.build();
		}

		@McpSampling(clients = { "client-1" })
		McpSchema.CreateMessageResult samplingHandler(McpSchema.CreateMessageRequest request) {
			return McpSchema.CreateMessageResult.builder()
				.message(((McpSchema.TextContent) request.messages().get(0).content()).text())
				.model("testgpt-42.5")
				.build();
		}

		@McpLogging(clients = { "client-1" })
		void handleLoggingMessage(McpSchema.LoggingMessageNotification notification) {
			this.calls.add(new Call("handleLoggingMessage", notification));
		}

		@McpLogging(clients = { "client-1" })
		void handleLoggingMessageAgain(McpSchema.LoggingMessageNotification notification) {
			this.calls.add(new Call("handleLoggingMessageAgain", notification));
		}

		@McpProgress(clients = { "client-1" })
		void handleProgress(McpSchema.ProgressNotification notification) {
			this.calls.add(new Call("handleProgress", notification));
		}

		@McpProgress(clients = { "client-1" })
		void handleProgressAgain(McpSchema.ProgressNotification notification) {
			this.calls.add(new Call("handleProgressAgain", notification));
		}

		@McpToolListChanged(clients = { "client-1" })
		void handleToolListChanged(List<McpSchema.Tool> updatedTools) {
			this.calls.add(new Call("handleToolListChanged", updatedTools));
		}

		@McpToolListChanged(clients = { "client-1" })
		void handleToolListChangedAgain(List<McpSchema.Tool> updatedTools) {
			this.calls.add(new Call("handleToolListChangedAgain", updatedTools));
		}

		@McpPromptListChanged(clients = { "client-1" })
		void handlePromptListChanged(List<McpSchema.Prompt> updatedPrompts) {
			this.calls.add(new Call("handlePromptListChanged", updatedPrompts));
		}

		@McpPromptListChanged(clients = { "client-1" })
		void handlePromptListChangedAgain(List<McpSchema.Prompt> updatedPrompts) {
			this.calls.add(new Call("handlePromptListChangedAgain", updatedPrompts));
		}

		@McpResourceListChanged(clients = { "client-1" })
		void handleResourceListChanged(List<McpSchema.Resource> updatedResources) {
			this.calls.add(new Call("handleResourceListChanged", updatedResources));
		}

		@McpResourceListChanged(clients = { "client-1" })
		void handleResourceListChangedAgain(List<McpSchema.Resource> updatedResources) {
			this.calls.add(new Call("handleResourceListChangedAgain", updatedResources));
		}

		// Record calls made to this object
		record Call(String name, Object callRequest) {
		}

	}

}
