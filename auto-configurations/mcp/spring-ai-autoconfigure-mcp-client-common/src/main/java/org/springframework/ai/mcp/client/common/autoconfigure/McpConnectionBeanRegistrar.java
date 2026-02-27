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

package org.springframework.ai.mcp.client.common.autoconfigure;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.mcp.McpClient;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpSseClientProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;

/**
 * A {@link BeanDefinitionRegistryPostProcessor} that registers individual MCP client
 * beans for each configured MCP connection.
 *
 * <p>
 * This registrar enables selective injection of MCP clients using the
 * {@link McpClient @McpClient} qualifier annotation. It scans both SSE and Streamable
 * HTTP connection configurations and registers the appropriate FactoryBean based on the
 * configured client type ({@code spring.ai.mcp.client.type}):
 * <ul>
 * <li>SYNC (default): Registers {@link NamedMcpSyncClientFactoryBean} for
 * {@link io.modelcontextprotocol.client.McpSyncClient}
 * <li>ASYNC: Registers {@link NamedMcpAsyncClientFactoryBean} for
 * {@link io.modelcontextprotocol.client.McpAsyncClient}
 * </ul>
 *
 * <p>
 * Bean naming convention:
 * <ul>
 * <li>Sync: {@code mcpSyncClient_{connectionName}}
 * <li>Async: {@code mcpAsyncClient_{connectionName}}
 * </ul>
 *
 * <p>
 * Example: For a connection named "filesystem", a bean with name
 * "mcpSyncClient_filesystem" (or "mcpAsyncClient_filesystem" in async mode) will be
 * registered and can be injected using {@code @McpClient("filesystem")}.
 *
 * @author Taewoong Kim
 * @see McpClient
 * @see NamedMcpSyncClientFactoryBean
 * @see NamedMcpAsyncClientFactoryBean
 */
public class McpConnectionBeanRegistrar implements BeanDefinitionRegistryPostProcessor {

	private static final Log logger = LogFactory.getLog(McpConnectionBeanRegistrar.class);

	/**
	 * Pattern for valid connection names: alphanumeric, hyphens, and underscores only.
	 */
	private static final Pattern CONNECTION_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

	/**
	 * Prefix for sync client bean names.
	 */
	private static final String SYNC_BEAN_NAME_PREFIX = "mcpSyncClient_";

	/**
	 * Prefix for async client bean names.
	 */
	private static final String ASYNC_BEAN_NAME_PREFIX = "mcpAsyncClient_";

	/**
	 * Property key for client type configuration.
	 */
	private static final String CLIENT_TYPE_PROPERTY = "spring.ai.mcp.client.type";

	private final Environment environment;

	public McpConnectionBeanRegistrar(Environment environment) {
		this.environment = environment;
	}

	@Override
	public void postProcessBeanDefinitionRegistry(@NonNull BeanDefinitionRegistry registry) throws BeansException {
		Set<String> registeredConnections = new HashSet<>();

		// Determine client type from properties
		boolean isAsync = isAsyncClientType();

		// Process SSE connections
		registerSseConnections(registry, registeredConnections, isAsync);

		// Process Streamable HTTP connections
		registerStreamableHttpConnections(registry, registeredConnections, isAsync);

		if (!registeredConnections.isEmpty()) {
			String clientType = isAsync ? "async" : "sync";
			logger.info("Registered " + registeredConnections.size() + " named MCP " + clientType + " client bean(s): "
					+ registeredConnections);
		}
	}

	private boolean isAsyncClientType() {
		String clientType = this.environment.getProperty(CLIENT_TYPE_PROPERTY, "SYNC");
		return "ASYNC".equalsIgnoreCase(clientType);
	}

	private void registerSseConnections(BeanDefinitionRegistry registry, Set<String> registeredConnections,
			boolean isAsync) {
		Bindable<Map<String, McpSseClientProperties.SseParameters>> bindable = Bindable.mapOf(String.class,
				McpSseClientProperties.SseParameters.class);

		Binder.get(this.environment)
			.bind(McpSseClientProperties.CONFIG_PREFIX + ".connections", bindable)
			.ifBound(connections -> connections.forEach((name, params) -> {
				if (validateAndCanRegister(registry, name, "SSE", registeredConnections, isAsync)) {
					registerNamedClientFactoryBean(registry, name, isAsync);
					registeredConnections.add(name);
				}
			}));
	}

	private void registerStreamableHttpConnections(BeanDefinitionRegistry registry, Set<String> registeredConnections,
			boolean isAsync) {
		Bindable<Map<String, McpStreamableHttpClientProperties.ConnectionParameters>> bindable = Bindable
			.mapOf(String.class, McpStreamableHttpClientProperties.ConnectionParameters.class);

		Binder.get(this.environment)
			.bind(McpStreamableHttpClientProperties.CONFIG_PREFIX + ".connections", bindable)
			.ifBound(connections -> connections.forEach((name, params) -> {
				if (validateAndCanRegister(registry, name, "Streamable HTTP", registeredConnections, isAsync)) {
					registerNamedClientFactoryBean(registry, name, isAsync);
					registeredConnections.add(name);
				}
			}));
	}

	private boolean validateAndCanRegister(BeanDefinitionRegistry registry, String connectionName, String transportType,
			Set<String> registeredConnections, boolean isAsync) {
		// Validate connection name format
		if (!StringUtils.hasText(connectionName)) {
			return false;
		}

		if (!CONNECTION_NAME_PATTERN.matcher(connectionName).matches()) {
			throw new IllegalArgumentException("Invalid MCP connection name '" + connectionName
					+ "'. Connection names must contain only alphanumeric characters, hyphens, or underscores.");
		}

		String beanName = getBeanName(connectionName, isAsync);

		// Check if already registered (e.g., same name used for both SSE and HTTP)
		if (registeredConnections.contains(connectionName)) {
			logger.warn("MCP connection '" + connectionName + "' is configured for multiple transports. "
					+ "Using the first registered transport. Skipping " + transportType + " registration.");
			return false;
		}

		// Check for existing bean definition conflicts
		if (registry.containsBeanDefinition(beanName)) {
			logger.warn("Bean '" + beanName + "' is already defined. Skipping auto-registration for connection '"
					+ connectionName + "'.");
			return false;
		}

		return true;
	}

	private String getBeanName(String connectionName, boolean isAsync) {
		return isAsync ? ASYNC_BEAN_NAME_PREFIX + connectionName : SYNC_BEAN_NAME_PREFIX + connectionName;
	}

	private void registerNamedClientFactoryBean(BeanDefinitionRegistry registry, String connectionName,
			boolean isAsync) {
		String beanName = getBeanName(connectionName, isAsync);

		// Create a FactoryBean definition that will produce the MCP client
		GenericBeanDefinition definition = new GenericBeanDefinition();
		definition.setBeanClass(isAsync ? NamedMcpAsyncClientFactoryBean.class : NamedMcpSyncClientFactoryBean.class);

		// Pass the connection name as constructor argument
		definition.getConstructorArgumentValues().addIndexedArgumentValue(0, connectionName);

		// Set scope and lifecycle
		definition.setScope(BeanDefinition.SCOPE_SINGLETON);

		definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);

		// Register both custom and standard qualifiers to support multiple injection
		// styles:
		// 1. @McpClient("connectionName") - Recommended, provides better readability
		// and searchability
		// 2. @Qualifier("connectionName") - Fallback for users preferring standard
		// Spring annotations
		// This dual registration ensures maximum flexibility without breaking
		// compatibility.
		// The qualifiers are attached to the FactoryBean definition, but Spring
		// automatically applies them to the produced bean as well.
		definition.addQualifier(new AutowireCandidateQualifier(McpClient.class, connectionName));
		definition.addQualifier(new AutowireCandidateQualifier(Qualifier.class, connectionName));

		// Mark as synthetic (auto-generated)
		definition.setSynthetic(true);

		// Log registration (only connection name, never URLs or sensitive data)
		String clientType = isAsync ? "async" : "sync";
		logger.info("Registering named MCP " + clientType + " client bean '" + beanName + "' for connection '"
				+ connectionName + "'");

		registry.registerBeanDefinition(beanName, definition);
	}

	@Override
	public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory beanFactory) throws BeansException {
		// No-op: All work is done in postProcessBeanDefinitionRegistry
	}

}
