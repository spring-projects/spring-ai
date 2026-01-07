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
 * A {@link BeanDefinitionRegistryPostProcessor} that registers individual
 * {@link io.modelcontextprotocol.client.McpSyncClient} beans for each configured MCP
 * connection.
 *
 * <p>
 * This registrar enables selective injection of MCP clients using the
 * {@link McpClient @McpClient} qualifier annotation. It scans both SSE and Streamable
 * HTTP connection configurations and registers a {@link NamedMcpSyncClientFactoryBean}
 * for each connection. This defers the actual client creation to runtime when all
 * required dependencies are available.
 *
 * <p>
 * Bean naming convention: {@code mcpSyncClient_{connectionName}}
 *
 * <p>
 * Example: For a connection named "filesystem", a bean with name
 * "mcpSyncClient_filesystem" will be registered and can be injected using
 * {@code @McpClient("filesystem")}.
 *
 * @author Taewoong Kim
 * @see McpClient
 * @see NamedMcpSyncClientFactoryBean
 */
public class McpConnectionBeanRegistrar implements BeanDefinitionRegistryPostProcessor {

	private static final Log logger = LogFactory.getLog(McpConnectionBeanRegistrar.class);

	/**
	 * Pattern for valid connection names: alphanumeric, hyphens, and underscores only.
	 */
	private static final Pattern CONNECTION_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

	/**
	 * Prefix for bean names.
	 */
	private static final String BEAN_NAME_PREFIX = "mcpSyncClient_";

	private final Environment environment;

	public McpConnectionBeanRegistrar(Environment environment) {
		this.environment = environment;
	}

	@Override
	public void postProcessBeanDefinitionRegistry(@NonNull BeanDefinitionRegistry registry) throws BeansException {
		Set<String> registeredConnections = new HashSet<>();

		// Process SSE connections
		registerSseConnections(registry, registeredConnections);

		// Process Streamable HTTP connections
		registerStreamableHttpConnections(registry, registeredConnections);

		if (!registeredConnections.isEmpty()) {
			logger.info("Registered " + registeredConnections.size() + " named MCP client bean(s): "
					+ registeredConnections);
		}
	}

	private void registerSseConnections(BeanDefinitionRegistry registry, Set<String> registeredConnections) {
		Bindable<Map<String, McpSseClientProperties.SseParameters>> bindable = Bindable.mapOf(String.class,
				McpSseClientProperties.SseParameters.class);

		Binder.get(this.environment)
			.bind(McpSseClientProperties.CONFIG_PREFIX + ".connections", bindable)
			.ifBound(connections -> connections.forEach((name, params) -> {
				if (validateAndCanRegister(registry, name, "SSE", registeredConnections)) {
					registerNamedClientFactoryBean(registry, name);
					registeredConnections.add(name);
				}
			}));
	}

	private void registerStreamableHttpConnections(BeanDefinitionRegistry registry, Set<String> registeredConnections) {
		Bindable<Map<String, McpStreamableHttpClientProperties.ConnectionParameters>> bindable = Bindable
			.mapOf(String.class, McpStreamableHttpClientProperties.ConnectionParameters.class);

		Binder.get(this.environment)
			.bind(McpStreamableHttpClientProperties.CONFIG_PREFIX + ".connections", bindable)
			.ifBound(connections -> connections.forEach((name, params) -> {
				if (validateAndCanRegister(registry, name, "Streamable HTTP", registeredConnections)) {
					registerNamedClientFactoryBean(registry, name);
					registeredConnections.add(name);
				}
			}));
	}

	private boolean validateAndCanRegister(BeanDefinitionRegistry registry, String connectionName, String transportType,
			Set<String> registeredConnections) {
		// Validate connection name format
		if (!StringUtils.hasText(connectionName)) {
			return false;
		}

		if (!CONNECTION_NAME_PATTERN.matcher(connectionName).matches()) {
			throw new IllegalArgumentException("Invalid MCP connection name '" + connectionName
					+ "'. Connection names must contain only alphanumeric characters, hyphens, or underscores.");
		}

		String beanName = BEAN_NAME_PREFIX + connectionName;

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

	private void registerNamedClientFactoryBean(BeanDefinitionRegistry registry, String connectionName) {
		String beanName = BEAN_NAME_PREFIX + connectionName;

		// Create a FactoryBean definition that will produce the McpSyncClient
		GenericBeanDefinition definition = new GenericBeanDefinition();
		definition.setBeanClass(NamedMcpSyncClientFactoryBean.class);

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
		logger
			.info("Registering named MCP Client bean '" + beanName + "' with connection name '" + connectionName + "'");

		registry.registerBeanDefinition(beanName, definition);
	}

	@Override
	public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory beanFactory) throws BeansException {
		// No-op: All work is done in postProcessBeanDefinitionRegistry
	}

}
