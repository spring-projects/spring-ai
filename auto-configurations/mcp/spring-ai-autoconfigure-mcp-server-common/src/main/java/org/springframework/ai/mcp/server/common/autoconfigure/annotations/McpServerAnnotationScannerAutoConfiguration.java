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

package org.springframework.ai.mcp.server.common.autoconfigure.annotations;

import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.mcp.annotation.McpComplete;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.spring.scan.AbstractAnnotatedMethodBeanFactoryInitializationAotProcessor;
import org.springframework.aop.framework.autoproxy.AutoProxyUtils;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

/**
 * @author Christian Tzolov
 * @author Josh Long
 * @author Daniel Garnier-Moiroux
 */
@AutoConfiguration
@ConditionalOnClass(McpTool.class)
@ConditionalOnProperty(prefix = McpServerAnnotationScannerProperties.CONFIG_PREFIX, name = "enabled",
		havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(McpServerAnnotationScannerProperties.class)
@ImportRuntimeHints(McpServerAnnotationScannerAutoConfiguration.AnnotationHints.class)
public class McpServerAnnotationScannerAutoConfiguration {

	private static final Set<Class<? extends Annotation>> SERVER_MCP_ANNOTATIONS = Set.of(McpTool.class,
			McpResource.class, McpPrompt.class, McpComplete.class);

	@Bean
	@ConditionalOnMissingBean
	public ServerMcpAnnotatedBeans serverAnnotatedBeanRegistry() {
		return new ServerMcpAnnotatedBeans(SERVER_MCP_ANNOTATIONS);
	}

	@Bean
	public static ServerAnnotatedBeanFactoryInitializationAotProcessor serverAnnotatedBeanFactoryInitializationAotProcessor() {
		return new ServerAnnotatedBeanFactoryInitializationAotProcessor(SERVER_MCP_ANNOTATIONS);
	}

	/**
	 * Finds the beans with MCP server annotated methods (tools, resources, prompts). All
	 * beans in the application context are scanned to find annotated methods.
	 * <p>
	 * Annotations are scanned once all bean definitions are available and before the
	 * beans are created. This avoids lifecycle issues: beans containing MCP handlers are
	 * known before MCP-specific beans (e.g. {@code McpSyncToolRepository}) are created.
	 * We can therefore create them ahead of time.
	 * <p>
	 * This is inspired from {@code ClientMcpSyncHandlersRegistry} on the client-side. It
	 * is simpler as it does not deal with cyclic dependencies. If an {@link McpTool}
	 * needs to interact with an {@code McpSyncToolRepository}, use
	 * {@code ObjectProvider<McpSyncToolRepository>} in your tool definition to inject the
	 * repository.
	 */
	public static class ServerMcpAnnotatedBeans implements BeanFactoryPostProcessor {

		private final Set<Class<? extends Annotation>> targetAnnotations;

		private final Map<Class<? extends Annotation>, Set<String>> beanNamesByAnnotation = new LinkedHashMap<>();

		private @Nullable ConfigurableListableBeanFactory beanFactory;

		/**
		 * Create an instance looking for methods annotated with any of the given
		 * annotations.
		 * @param targetAnnotations the method annotations to look for
		 * @since 2.1.0
		 */
		public ServerMcpAnnotatedBeans(Set<Class<? extends Annotation>> targetAnnotations) {
			Assert.notEmpty(targetAnnotations, "Target annotations must not be empty");
			this.targetAnnotations = targetAnnotations;
		}

		@Override
		public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
			this.beanFactory = beanFactory;
			for (String beanName : beanFactory.getBeanDefinitionNames()) {
				BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
				if (!beanDefinition.isSingleton() || beanDefinition.isAbstract()) {
					continue;
				}
				Class<?> beanClass = AutoProxyUtils.determineTargetClass(beanFactory, beanName);
				if (beanClass == null) {
					// If we cannot determine the bean class, we cannot scan it before
					// it is really resolved. This is very likely an infrastructure-level
					// bean, not a "service" type, skip it entirely.
					continue;
				}
				for (Class<? extends Annotation> annotationType : scan(beanClass)) {
					this.beanNamesByAnnotation.computeIfAbsent(annotationType, k -> new LinkedHashSet<>())
						.add(beanName);
				}
			}
		}

		/**
		 * Return the beans that have at least one method annotated with the given
		 * annotation, creating them if they do not exist yet.
		 * @param annotationType the method annotation
		 * @return the annotated beans, in bean definition order
		 */
		public List<Object> getBeansByAnnotation(Class<? extends Annotation> annotationType) {
			Set<String> beanNames = this.beanNamesByAnnotation.get(annotationType);
			if (beanNames == null) {
				return List.of();
			}
			ConfigurableListableBeanFactory beanFactory = this.beanFactory;
			Assert.state(beanFactory != null, "The bean factory has not been post-processed yet");
			return beanNames.stream().map(beanFactory::getBean).toList();
		}

		private Set<Class<? extends Annotation>> scan(Class<?> beanType) {
			Set<Class<? extends Annotation>> foundAnnotations = new LinkedHashSet<>();
			ReflectionUtils.doWithMethods(beanType, method -> {
				for (Class<? extends Annotation> annotationType : this.targetAnnotations) {
					if (AnnotationUtils.findAnnotation(method, annotationType) != null) {
						foundAnnotations.add(annotationType);
					}
				}
			});
			return foundAnnotations;
		}

	}

	public static class ServerAnnotatedBeanFactoryInitializationAotProcessor
			extends AbstractAnnotatedMethodBeanFactoryInitializationAotProcessor {

		public ServerAnnotatedBeanFactoryInitializationAotProcessor(
				Set<Class<? extends Annotation>> targetAnnotations) {
			super(targetAnnotations);
		}

	}

	static class AnnotationHints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
			SERVER_MCP_ANNOTATIONS.forEach(an -> hints.reflection().registerType(an, MemberCategory.values()));
		}

	}

}
