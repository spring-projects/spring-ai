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

package org.springframework.ai.mcp.client.common.autoconfigure;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.mcp.annotation.spring.McpClient;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpSseClientProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStdioClientProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.StringUtils;

/**
 * An {@link ImportBeanDefinitionRegistrar} that registers an individual MCP client bean
 * for each connection name discoverable from MCP client configuration properties.
 *
 * <p>
 * SSE, Streamable HTTP, and stdio connection names are registered with both the
 * {@link McpClient @McpClient} qualifier and the standard {@link Qualifier @Qualifier}.
 * The configured {@code spring.ai.mcp.client.type} determines whether each definition
 * uses {@link NamedMcpSyncClientFactoryBean} or {@link NamedMcpAsyncClientFactoryBean}.
 *
 * <p>
 * Sync beans use the name {@code mcpSyncClient_{connectionName}} and async beans use
 * {@code mcpAsyncClient_{connectionName}}. For example, a connection named
 * {@code filesystem} can be injected with {@code @McpClient("filesystem")}.
 *
 * @author Taewoong Kim
 * @see NamedMcpSyncClientFactoryBean
 * @see NamedMcpAsyncClientFactoryBean
 * @since 2.0.1
 */
public class McpConnectionBeanRegistrar implements ImportBeanDefinitionRegistrar {

	private static final Log logger = LogFactory.getLog(McpConnectionBeanRegistrar.class);

	private static final String SYNC_BEAN_NAME_PREFIX = "mcpSyncClient_";

	private static final String ASYNC_BEAN_NAME_PREFIX = "mcpAsyncClient_";

	private static final String QUALIFIER_TYPE_ATTRIBUTE = McpConnectionBeanRegistrar.class.getName()
			+ ".qualifierType";

	private final Environment environment;

	/**
	 * Creates a registrar that discovers MCP connections from the environment.
	 * @param environment the environment containing MCP client configuration
	 */
	public McpConnectionBeanRegistrar(Environment environment) {
		this.environment = environment;
	}

	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		boolean async = isAsyncClientType();
		Set<String> connectionNames = new LinkedHashSet<>();

		collectSseConnectionNames(connectionNames);
		collectStreamableHttpConnectionNames(connectionNames);
		collectStdioConnectionNames(connectionNames);
		Set<String> registeredConnections = new LinkedHashSet<>();
		for (String connectionName : connectionNames) {
			if (registerNamedClientFactoryBean(registry, connectionName, async)) {
				registeredConnections.add(connectionName);
			}
		}

		if (!registeredConnections.isEmpty() && logger.isDebugEnabled()) {
			String clientType = async ? "async" : "sync";
			logger.debug("Registered " + registeredConnections.size() + " named MCP " + clientType + " client bean(s): "
					+ registeredConnections);
		}
	}

	@SuppressWarnings("removal")
	private void collectSseConnectionNames(Set<String> connectionNames) {
		Bindable<Map<String, McpSseClientProperties.SseParameters>> bindable = Bindable.mapOf(String.class,
				McpSseClientProperties.SseParameters.class);

		Binder.get(this.environment)
			.bind(McpSseClientProperties.CONFIG_PREFIX + ".connections", bindable)
			.ifBound(connections -> connections.keySet().forEach(name -> addConnectionName(connectionNames, name)));
	}

	private void collectStreamableHttpConnectionNames(Set<String> connectionNames) {
		Bindable<Map<String, McpStreamableHttpClientProperties.ConnectionParameters>> bindable = Bindable
			.mapOf(String.class, McpStreamableHttpClientProperties.ConnectionParameters.class);

		Binder.get(this.environment)
			.bind(McpStreamableHttpClientProperties.CONFIG_PREFIX + ".connections", bindable)
			.ifBound(connections -> connections.keySet().forEach(name -> addConnectionName(connectionNames, name)));
	}

	private void collectStdioConnectionNames(Set<String> connectionNames) {
		Bindable<McpStdioClientProperties> bindable = Bindable.of(McpStdioClientProperties.class);

		Binder.get(this.environment)
			.bind(McpStdioClientProperties.CONFIG_PREFIX, bindable)
			.ifBound(properties -> properties.toServerParameters()
				.keySet()
				.forEach(name -> addConnectionName(connectionNames, name)));
	}

	private void addConnectionName(Set<String> connectionNames, String connectionName) {
		if (!StringUtils.hasText(connectionName)) {
			return;
		}
		connectionNames.add(connectionName);
	}

	private boolean isAsyncClientType() {
		String type = this.environment.getProperty(McpClientCommonProperties.CONFIG_PREFIX + ".type", "SYNC");
		return "ASYNC".equalsIgnoreCase(type);
	}

	private boolean registerNamedClientFactoryBean(BeanDefinitionRegistry registry, String connectionName,
			boolean async) {
		String beanName = getBeanName(connectionName, async);
		if (registry.isBeanNameInUse(beanName)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Bean name '" + beanName
						+ "' is already in use. Skipping auto-registration for connection '" + connectionName + "'.");
			}
			return false;
		}

		GenericBeanDefinition definition = new GenericBeanDefinition();
		definition.setBeanClass(async ? NamedMcpAsyncClientFactoryBean.class : NamedMcpSyncClientFactoryBean.class);
		definition.getConstructorArgumentValues().addIndexedArgumentValue(0, connectionName);
		definition.setScope(BeanDefinition.SCOPE_SINGLETON);
		definition.addQualifier(createQualifier(McpClient.class, connectionName));
		definition.addQualifier(createQualifier(Qualifier.class, connectionName));
		definition.setDefaultCandidate(false);
		definition.setLazyInit(true);
		definition.setSynthetic(true);

		registry.registerBeanDefinition(beanName, definition);
		return true;
	}

	private static AutowireCandidateQualifier createQualifier(Class<?> qualifierType, String connectionName) {
		AutowireCandidateQualifier qualifier = new AutowireCandidateQualifier(qualifierType, connectionName);
		// Include the type in equality so equal-valued qualifiers both survive AOT code
		// generation.
		qualifier.setAttribute(QUALIFIER_TYPE_ATTRIBUTE, qualifierType.getName());
		return qualifier;
	}

	private String getBeanName(String connectionName, boolean async) {
		return async ? ASYNC_BEAN_NAME_PREFIX + connectionName : SYNC_BEAN_NAME_PREFIX + connectionName;
	}

}
