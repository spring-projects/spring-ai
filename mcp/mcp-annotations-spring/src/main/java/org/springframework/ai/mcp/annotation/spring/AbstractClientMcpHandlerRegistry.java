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

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.mcp.annotation.McpElicitation;
import org.springaicommunity.mcp.annotation.McpLogging;
import org.springaicommunity.mcp.annotation.McpProgress;
import org.springaicommunity.mcp.annotation.McpPromptListChanged;
import org.springaicommunity.mcp.annotation.McpResourceListChanged;
import org.springaicommunity.mcp.annotation.McpSampling;
import org.springaicommunity.mcp.annotation.McpToolListChanged;

import org.springframework.aop.framework.autoproxy.AutoProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Base class for sync and async ClientMcpHandlerRegistries. Not intended for public use.
 *
 * @author Daniel Garnier-Moiroux
 * @see ClientMcpAsyncHandlersRegistry
 * @see ClientMcpSyncHandlersRegistry
 */
abstract class AbstractClientMcpHandlerRegistry implements BeanFactoryPostProcessor {

	protected Map<String, McpSchema.ClientCapabilities> capabilitiesPerClient = new HashMap<>();

	@SuppressWarnings("NullAway") // Late-init field
	protected ConfigurableListableBeanFactory beanFactory;

	protected final Set<String> allAnnotatedBeans = new HashSet<>();

	static final Class<? extends Annotation>[] CLIENT_MCP_ANNOTATIONS = new Class[] { McpSampling.class,
			McpElicitation.class, McpLogging.class, McpProgress.class, McpToolListChanged.class,
			McpPromptListChanged.class, McpResourceListChanged.class };

	static final McpSchema.ClientCapabilities EMPTY_CAPABILITIES = new McpSchema.ClientCapabilities(null, null, null,
			null);

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
		Map<String, List<String>> elicitationClientToAnnotatedBeans = new HashMap<>();
		Map<String, List<String>> samplingClientToAnnotatedBeans = new HashMap<>();
		for (var beanName : beanFactory.getBeanDefinitionNames()) {
			if (!beanFactory.getBeanDefinition(beanName).isSingleton()) {
				// Only process singleton beans, not scoped beans
				continue;
			}
			Class<?> beanClass = AutoProxyUtils.determineTargetClass(beanFactory, beanName);
			if (beanClass == null) {
				// If we cannot determine the bean class, we cannot scan it before
				// it is really resolved. This is very likely an infrastructure-level
				// bean, not a "service" type, skip it entirely.
				continue;
			}
			var foundAnnotations = this.scan(beanClass);
			if (!foundAnnotations.isEmpty()) {
				this.allAnnotatedBeans.add(beanName);
			}
			for (var foundAnnotation : foundAnnotations) {
				if (foundAnnotation instanceof McpSampling sampling) {
					for (var client : sampling.clients()) {
						samplingClientToAnnotatedBeans.computeIfAbsent(client, c -> new ArrayList<>()).add(beanName);
					}
				}
				else if (foundAnnotation instanceof McpElicitation elicitation) {
					for (var client : elicitation.clients()) {
						elicitationClientToAnnotatedBeans.computeIfAbsent(client, c -> new ArrayList<>()).add(beanName);
					}
				}
			}
		}

		for (var elicitationEntry : elicitationClientToAnnotatedBeans.entrySet()) {
			if (elicitationEntry.getValue().size() > 1) {
				throw new IllegalArgumentException(
						"Found 2 elicitation handlers for client [%s], found in bean with names %s. Only one @McpElicitation handler is allowed per client"
							.formatted(elicitationEntry.getKey(), new LinkedHashSet<>(elicitationEntry.getValue())));
			}
		}
		for (var samplingEntry : samplingClientToAnnotatedBeans.entrySet()) {
			if (samplingEntry.getValue().size() > 1) {
				throw new IllegalArgumentException(
						"Found 2 sampling handlers for client [%s], found in bean with names %s. Only one @McpSampling handler is allowed per client"
							.formatted(samplingEntry.getKey(), new LinkedHashSet<>(samplingEntry.getValue())));
			}
		}

		Map<String, McpSchema.ClientCapabilities.Builder> capsPerClient = new HashMap<>();
		for (var samplingClient : samplingClientToAnnotatedBeans.keySet()) {
			capsPerClient.computeIfAbsent(samplingClient, ignored -> McpSchema.ClientCapabilities.builder()).sampling();
		}
		for (var elicitationClient : elicitationClientToAnnotatedBeans.keySet()) {
			capsPerClient.computeIfAbsent(elicitationClient, ignored -> McpSchema.ClientCapabilities.builder())
				.elicitation();
		}

		this.capabilitiesPerClient = capsPerClient.entrySet()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().build()));
	}

	protected List<Annotation> scan(Class<?> beanClass) {
		List<Annotation> foundAnnotations = new ArrayList<>();

		// Scan all methods in the bean class
		ReflectionUtils.doWithMethods(beanClass, method -> {
			for (var annotationType : CLIENT_MCP_ANNOTATIONS) {
				Annotation annotation = AnnotationUtils.findAnnotation(method, annotationType);
				if (annotation != null) {
					foundAnnotations.add(annotation);
				}
			}
		});
		return foundAnnotations;
	}

	protected Map<Class<? extends Annotation>, Set<Object>> getBeansByAnnotationType() {
		// Use a set in case multiple handlers are registered in the same bean
		Map<Class<? extends Annotation>, Set<Object>> beansByAnnotation = new HashMap<>();
		for (var annotation : CLIENT_MCP_ANNOTATIONS) {
			beansByAnnotation.put(annotation, new HashSet<>());
		}

		for (var beanName : this.allAnnotatedBeans) {
			var bean = this.beanFactory.getBean(beanName);
			var annotations = this.scan(bean.getClass());
			for (var annotation : annotations) {
				beansByAnnotation.computeIfAbsent(annotation.annotationType(), k -> new HashSet<>()).add(bean);
			}
		}
		return beansByAnnotation;
	}

}
