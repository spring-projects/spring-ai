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
import java.util.function.Function;
import java.util.stream.Collectors;

import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.mcp.annotation.McpElicitation;
import org.springaicommunity.mcp.annotation.McpLogging;
import org.springaicommunity.mcp.annotation.McpProgress;
import org.springaicommunity.mcp.annotation.McpPromptListChanged;
import org.springaicommunity.mcp.annotation.McpResourceListChanged;
import org.springaicommunity.mcp.annotation.McpSampling;
import org.springaicommunity.mcp.annotation.McpToolListChanged;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Registry of methods annotated with MCP Client annotations (sampling, logging, etc.).
 * All beans in the application context are scanned to find these methods automatically.
 * They are then exposed by the registry by client name.
 * <p>
 * The scanning happens in two phases:
 * <p>
 * First, once bean definitions are available, all bean types are scanned for the presence
 * of MCP annotations. In particular, this is used to prepare the result
 * {@link #getCapabilities(String)}, which is then used by MCP client auto-configurations
 * to configure the client capabilities without needing to instantiate the beans.
 * <p>
 * Second, after all singleton beans have been instantiated, all annotated beans are
 * scanned again, MCP handlers are created to match the annotations, and stored by client.
 *
 * @see McpSampling
 * @see McpElicitation
 * @see McpLogging
 * @see McpProgress
 * @see McpToolListChanged
 * @see McpPromptListChanged
 * @see McpResourceListChanged
 * @author Daniel Garnier-Moiroux
 * @since 1.1.0
 */
public class ClientMcpAsyncHandlersRegistry implements BeanFactoryPostProcessor, SmartInitializingSingleton {

	private static final Class<? extends Annotation>[] CLIENT_MCP_ANNOTATIONS = new Class[] { McpSampling.class,
			McpElicitation.class, McpLogging.class, McpProgress.class, McpToolListChanged.class,
			McpPromptListChanged.class, McpResourceListChanged.class };

	private final McpSchema.ClientCapabilities EMPTY_CAPABILITIES = new McpSchema.ClientCapabilities(null, null, null,
			null);

	private Map<String, McpSchema.ClientCapabilities> capabilitiesPerClient = new HashMap<>();

	private ConfigurableListableBeanFactory beanFactory;

	private final Set<String> allAnnotatedBeans = new HashSet<>();

	private final Map<String, Function<McpSchema.CreateMessageRequest, Mono<McpSchema.CreateMessageResult>>> samplingHandlers = new HashMap<>();

	private final Map<String, Function<McpSchema.ElicitRequest, Mono<McpSchema.ElicitResult>>> elicitationHandlers = new HashMap<>();

	private final Map<String, List<Function<McpSchema.LoggingMessageNotification, Mono<Void>>>> loggingHandlers = new HashMap<>();

	private final Map<String, List<Function<McpSchema.ProgressNotification, Mono<Void>>>> progressHandlers = new HashMap<>();

	private final Map<String, List<Function<List<McpSchema.Tool>, Mono<Void>>>> toolListChangedHandlers = new HashMap<>();

	private final Map<String, List<Function<List<McpSchema.Prompt>, Mono<Void>>>> promptListChangedHandlers = new HashMap<>();

	private final Map<String, List<Function<List<McpSchema.Resource>, Mono<Void>>>> resourceListChangedHandlers = new HashMap<>();

	/**
	 * Obtain the MCP capabilities declared for a given MCP client. Capabilities are
	 * registered with the {@link McpSampling} and {@link McpElicitation} annotations.
	 */
	public McpSchema.ClientCapabilities getCapabilities(String clientName) {
		return this.capabilitiesPerClient.getOrDefault(clientName, this.EMPTY_CAPABILITIES);
	}

	/**
	 * Invoke the sampling handler for a given MCP client.
	 *
	 * @see McpSampling
	 */
	public Mono<McpSchema.CreateMessageResult> handleSampling(String name,
			McpSchema.CreateMessageRequest samplingRequest) {
		var handler = this.samplingHandlers.get(name);
		if (handler != null) {
			return handler.apply(samplingRequest);
		}
		// TODO: handle null
		return Mono.empty();
	}

	/**
	 * Invoke the elicitation handler for a given MCP client.
	 *
	 * @see McpElicitation
	 */
	public Mono<McpSchema.ElicitResult> handleElicitation(String name, McpSchema.ElicitRequest elicitationRequest) {
		var handler = this.elicitationHandlers.get(name);
		if (handler != null) {
			return handler.apply(elicitationRequest);
		}
		// TODO: handle null
		return Mono.empty();
	}

	/**
	 * Invoke all elicitation handlers for a given MCP client, sequentially.
	 *
	 * @see McpLogging
	 */
	public Mono<Void> handleLogging(String name, McpSchema.LoggingMessageNotification loggingMessageNotification) {
		var consumers = this.loggingHandlers.get(name);
		if (consumers == null) {
			// TODO handle
			return Mono.empty();
		}
		return Flux.fromIterable(consumers).flatMap(c -> c.apply(loggingMessageNotification)).then();
	}

	/**
	 * Invoke all progress handlers for a given MCP client, sequentially.
	 *
	 * @see McpProgress
	 */
	public Mono<Void> handleProgress(String name, McpSchema.ProgressNotification progressNotification) {
		var consumers = this.progressHandlers.get(name);
		if (consumers == null) {
			// TODO handle
			return Mono.empty();
		}
		return Flux.fromIterable(consumers).flatMap(c -> c.apply(progressNotification)).then();
	}

	/**
	 * Invoke all tool list changed handlers for a given MCP client, sequentially.
	 *
	 * @see McpToolListChanged
	 */
	public Mono<Void> handleToolListChanged(String name, List<McpSchema.Tool> updatedTools) {
		var consumers = this.toolListChangedHandlers.get(name);
		if (consumers == null) {
			// TODO handle
			return Mono.empty();
		}
		return Flux.fromIterable(consumers).flatMap(c -> c.apply(updatedTools)).then();
	}

	/**
	 * Invoke all prompt list changed handlers for a given MCP client, sequentially.
	 *
	 * @see McpPromptListChanged
	 */
	public Mono<Void> handlePromptListChanged(String name, List<McpSchema.Prompt> updatedPrompts) {
		var consumers = this.promptListChangedHandlers.get(name);
		if (consumers == null) {
			// TODO handle
			return Mono.empty();
		}
		return Flux.fromIterable(consumers).flatMap(c -> c.apply(updatedPrompts)).then();
	}

	/**
	 * Invoke all resource list changed handlers for a given MCP client, sequentially.
	 *
	 * @see McpResourceListChanged
	 */
	public Mono<Void> handleResourceListChanged(String name, List<McpSchema.Resource> updatedResources) {
		var consumers = this.resourceListChangedHandlers.get(name);
		if (consumers == null) {
			// TODO handle
			return Mono.empty();
		}
		return Flux.fromIterable(consumers).flatMap(c -> c.apply(updatedResources)).then();
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
		Map<String, List<String>> elicitationClientToAnnotatedBeans = new HashMap<>();
		Map<String, List<String>> samplingClientToAnnotatedBeans = new HashMap<>();
		for (var beanName : beanFactory.getBeanDefinitionNames()) {
			var definition = beanFactory.getBeanDefinition(beanName);
			var foundAnnotations = scan(definition.getResolvableType().toClass());
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

	private List<Annotation> scan(Class<?> beanClass) {
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

	@Override
	public void afterSingletonsInstantiated() {
		// Use a set in case multiple handlers are registered in the same bean
		Map<Class<? extends Annotation>, Set<Object>> beansByAnnotation = new HashMap<>();
		for (var annotation : CLIENT_MCP_ANNOTATIONS) {
			beansByAnnotation.put(annotation, new HashSet<>());
		}

		for (var beanName : this.allAnnotatedBeans) {
			var bean = this.beanFactory.getBean(beanName);
			var annotations = scan(bean.getClass());
			for (var annotation : annotations) {
				beansByAnnotation.computeIfAbsent(annotation.annotationType(), k -> new HashSet<>()).add(bean);
			}
		}

		var samplingSpecs = AsyncMcpAnnotationProviders
			.samplingSpecifications(new ArrayList<>(beansByAnnotation.get(McpSampling.class)));
		for (var samplingSpec : samplingSpecs) {
			for (var client : samplingSpec.clients()) {
				this.samplingHandlers.put(client, samplingSpec.samplingHandler());
			}
		}

		var elicitationSpecs = AsyncMcpAnnotationProviders
			.elicitationSpecifications(new ArrayList<>(beansByAnnotation.get(McpElicitation.class)));
		for (var elicitationSpec : elicitationSpecs) {
			for (var client : elicitationSpec.clients()) {
				this.elicitationHandlers.put(client, elicitationSpec.elicitationHandler());
			}
		}

		var loggingSpecs = AsyncMcpAnnotationProviders
			.loggingSpecifications(new ArrayList<>(beansByAnnotation.get(McpLogging.class)));
		for (var loggingSpec : loggingSpecs) {
			for (var client : loggingSpec.clients()) {
				this.loggingHandlers.computeIfAbsent(client, k -> new ArrayList<>()).add(loggingSpec.loggingHandler());
			}
		}

		var progressSpecs = AsyncMcpAnnotationProviders
			.progressSpecifications(new ArrayList<>(beansByAnnotation.get(McpProgress.class)));
		for (var progressSpec : progressSpecs) {
			for (var client : progressSpec.clients()) {
				this.progressHandlers.computeIfAbsent(client, k -> new ArrayList<>())
					.add(progressSpec.progressHandler());
			}
		}

		var toolsListChangedSpecs = AsyncMcpAnnotationProviders
			.toolListChangedSpecifications(new ArrayList<>(beansByAnnotation.get(McpToolListChanged.class)));
		for (var toolsListChangedSpec : toolsListChangedSpecs) {
			for (var client : toolsListChangedSpec.clients()) {
				this.toolListChangedHandlers.computeIfAbsent(client, k -> new ArrayList<>())
					.add(toolsListChangedSpec.toolListChangeHandler());
			}
		}

		var promptListChangedSpecs = AsyncMcpAnnotationProviders
			.promptListChangedSpecifications(new ArrayList<>(beansByAnnotation.get(McpPromptListChanged.class)));
		for (var promptListChangedSpec : promptListChangedSpecs) {
			for (var client : promptListChangedSpec.clients()) {
				this.promptListChangedHandlers.computeIfAbsent(client, k -> new ArrayList<>())
					.add(promptListChangedSpec.promptListChangeHandler());
			}
		}

		var resourceListChangedSpecs = AsyncMcpAnnotationProviders
			.resourceListChangedSpecifications(new ArrayList<>(beansByAnnotation.get(McpResourceListChanged.class)));
		for (var resourceListChangedSpec : resourceListChangedSpecs) {
			for (var client : resourceListChangedSpec.clients()) {
				this.resourceListChangedHandlers.computeIfAbsent(client, k -> new ArrayList<>())
					.add(resourceListChangedSpec.resourceListChangeHandler());
			}
		}

	}

}
