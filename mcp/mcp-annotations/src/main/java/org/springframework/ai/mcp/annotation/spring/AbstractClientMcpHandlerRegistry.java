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

package org.springframework.ai.mcp.annotation.spring;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.modelcontextprotocol.spec.McpSchema;

import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.ai.mcp.annotation.McpLogging;
import org.springframework.ai.mcp.annotation.McpProgress;
import org.springframework.ai.mcp.annotation.McpPromptListChanged;
import org.springframework.ai.mcp.annotation.McpResourceListChanged;
import org.springframework.ai.mcp.annotation.McpSampling;
import org.springframework.ai.mcp.annotation.McpToolListChanged;
import org.springframework.ai.mcp.annotation.common.ModernClientHandlerSupport;
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

	/**
	 * The handler annotations that name each client. Known once the bean factory is
	 * post-processed, which is before the clients are created.
	 */
	protected final Map<String, Set<Class<? extends Annotation>>> handlerAnnotationsPerClient = new HashMap<>();

	/**
	 * The clients with a handler for the form elicitation requests of the 2026-07-28
	 * ("modern") MCP revision.
	 */
	protected final Set<String> modernFormElicitationClients = new HashSet<>();

	/**
	 * The clients with a handler for the URL elicitation requests of the modern revision.
	 */
	protected final Set<String> modernUrlElicitationClients = new HashSet<>();

	/**
	 * The clients with a synchronous progress handler, which a client of the modern
	 * revision can call.
	 */
	protected final Set<String> modernProgressClients = new HashSet<>();

	/**
	 * The clients with a list-changed handler that a client of the modern revision can
	 * call, by the annotation of the handler.
	 */
	protected final Map<Class<? extends Annotation>, Set<String>> modernListChangedClients = new HashMap<>();

	static final Class<? extends Annotation>[] CLIENT_MCP_ANNOTATIONS = new Class[] { McpSampling.class,
			McpElicitation.class, McpLogging.class, McpProgress.class, McpToolListChanged.class,
			McpPromptListChanged.class, McpResourceListChanged.class };

	static final McpSchema.ClientCapabilities EMPTY_CAPABILITIES = McpSchema.ClientCapabilities.builder().build();

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
		Map<String, List<String>> elicitationClientToAnnotatedBeans = new HashMap<>();
		Map<String, List<ElicitationHandlerMethod>> elicitationHandlerMethods = new HashMap<>();
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
				this.scanHandlerMethods(beanClass, elicitationHandlerMethods);
			}
			for (var foundAnnotation : foundAnnotations) {
				Map<String, List<String>> clientToAnnotatedBeans = null;
				if (foundAnnotation instanceof McpSampling) {
					clientToAnnotatedBeans = samplingClientToAnnotatedBeans;
				}
				else if (foundAnnotation instanceof McpElicitation) {
					clientToAnnotatedBeans = elicitationClientToAnnotatedBeans;
				}
				if (clientToAnnotatedBeans != null) {
					for (var client : clients(foundAnnotation)) {
						clientToAnnotatedBeans.computeIfAbsent(client, c -> new ArrayList<>()).add(beanName);
					}
				}
			}
		}

		for (var elicitationEntry : elicitationClientToAnnotatedBeans.entrySet()) {
			if (elicitationEntry.getValue().size() > 1
					&& this.elicitationHandlersConflict(elicitationEntry.getValue().size(),
							elicitationHandlerMethods.getOrDefault(elicitationEntry.getKey(), List.of()))) {
				throw new IllegalArgumentException(
						"Found 2 elicitation handlers for client [%s], found in bean with names %s. Only one @McpElicitation handler is allowed per client%s"
							.formatted(elicitationEntry.getKey(), new LinkedHashSet<>(elicitationEntry.getValue()),
									this.allowsFormAndUrlElicitationHandlers()
											? ", or one for form requests and one for URL requests" : ""));
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
			// A method declares form elicitation, as it always did, unless it takes only
			// URL requests. A client with such a method declares URL elicitation.
			List<ElicitationHandlerMethod> handlerMethods = elicitationHandlerMethods.getOrDefault(elicitationClient,
					List.of());
			boolean form = handlerMethods.isEmpty() || handlerMethods.stream().anyMatch(ElicitationHandlerMethod::form);
			boolean urlOnly = handlerMethods.stream().anyMatch(method -> method.url() && !method.form());
			var capabilities = capsPerClient.computeIfAbsent(elicitationClient,
					ignored -> McpSchema.ClientCapabilities.builder());
			if (urlOnly) {
				capabilities.elicitation(form, true);
			}
			else {
				capabilities.elicitation();
			}
		}

		this.capabilitiesPerClient = capsPerClient.entrySet()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().build()));
	}

	/**
	 * Whether a handler method with the given annotation names the client. The answer is
	 * available before the handlers themselves are resolved, so it can be used while the
	 * clients are created.
	 * @param clientName the name of the client, which is the name of its connection
	 * @param annotationType the handler annotation, such as {@code McpProgress}
	 * @return {@code true} if such a handler method exists
	 * @since 2.1.0
	 */
	public boolean hasHandlers(String clientName, Class<? extends Annotation> annotationType) {
		return this.handlerAnnotationsPerClient.getOrDefault(clientName, Set.of()).contains(annotationType);
	}

	/**
	 * Whether a client may have one {@code @McpElicitation} method for form requests and
	 * another one for URL requests. By default a client has a single elicitation method.
	 * @return {@code true} if the registry tells the two kinds of handler apart
	 * @since 2.1.0
	 */
	protected boolean allowsFormAndUrlElicitationHandlers() {
		return false;
	}

	/**
	 * Whether a client of the modern revision can call the {@code @McpElicitation} method
	 * for form requests. By default it must be a synchronous method that takes a form
	 * request.
	 * @param method the annotated method
	 * @return {@code true} if the method answers modern form requests
	 * @since 2.1.0
	 */
	protected boolean isModernFormElicitationMethod(Method method) {
		return ModernClientHandlerSupport.handlesModernFormElicitation(method);
	}

	/**
	 * Whether a client of the modern revision can call the {@code @McpElicitation} method
	 * for URL requests. By default it must be a synchronous method that takes the modern
	 * URL request.
	 * @param method the annotated method
	 * @return {@code true} if the method answers modern URL requests
	 * @since 2.1.0
	 */
	protected boolean isModernUrlElicitationMethod(Method method) {
		return ModernClientHandlerSupport.handlesModernUrlElicitation(method);
	}

	/**
	 * Whether a client of the modern revision can call the {@code @McpProgress} method.
	 * By default it must be a synchronous method.
	 * @param method the annotated method
	 * @return {@code true} if the method receives modern progress
	 * @since 2.1.0
	 */
	protected boolean isModernProgressMethod(Method method) {
		return method.getReturnType() == void.class;
	}

	/**
	 * Whether a client of the modern revision can call the list-changed method. By
	 * default it must be a synchronous method.
	 * @param method the method with {@code @McpToolListChanged},
	 * {@code @McpPromptListChanged} or {@code @McpResourceListChanged}
	 * @return {@code true} if the method hears about modern list changes
	 * @since 2.1.0
	 */
	protected boolean isModernListChangedMethod(Method method) {
		return method.getReturnType() == void.class;
	}

	/**
	 * Whether the elicitation methods that name one client stand for the same kind of
	 * request. More than one method is always a conflict unless the registry
	 * {@link #allowsFormAndUrlElicitationHandlers() tells form and URL handlers apart}.
	 */
	private boolean elicitationHandlersConflict(int annotatedMethods, List<ElicitationHandlerMethod> handlerMethods) {
		// Methods that only inherit the annotation are not known by their kind.
		if (!this.allowsFormAndUrlElicitationHandlers() || handlerMethods.size() < annotatedMethods) {
			return true;
		}
		return handlerMethods.stream().filter(ElicitationHandlerMethod::form).count() > 1
				|| handlerMethods.stream().filter(ElicitationHandlerMethod::url).count() > 1;
	}

	/**
	 * Records which clients the handler methods of a bean class name, which of them get a
	 * handler for the elicitation requests of the modern revision, and which kinds of
	 * elicitation request each {@code @McpElicitation} method stands for.
	 */
	private void scanHandlerMethods(Class<?> beanClass,
			Map<String, List<ElicitationHandlerMethod>> elicitationHandlerMethods) {
		// The same methods and the same annotation lookup as the providers that create
		// the handlers, so that what is recorded here is what gets a handler later. An
		// annotation that a method only inherits is not seen by them.
		for (Method method : ReflectionUtils.getUniqueDeclaredMethods(beanClass,
				ReflectionUtils.USER_DECLARED_METHODS)) {
			for (var annotationType : CLIENT_MCP_ANNOTATIONS) {
				Annotation annotation = method.getAnnotation(annotationType);
				if (annotation == null) {
					continue;
				}
				for (String client : clients(annotation)) {
					this.handlerAnnotationsPerClient.computeIfAbsent(client, c -> new HashSet<>()).add(annotationType);
					if (annotation instanceof McpElicitation) {
						elicitationHandlerMethods.computeIfAbsent(client, c -> new ArrayList<>())
							.add(new ElicitationHandlerMethod(ModernClientHandlerSupport.claimsFormElicitation(method),
									ModernClientHandlerSupport.claimsUrlElicitation(method)));
						if (this.isModernFormElicitationMethod(method)) {
							this.modernFormElicitationClients.add(client);
						}
						if (this.isModernUrlElicitationMethod(method)) {
							this.modernUrlElicitationClients.add(client);
						}
					}
					else if (annotation instanceof McpProgress && this.isModernProgressMethod(method)) {
						this.modernProgressClients.add(client);
					}
					else if ((annotation instanceof McpToolListChanged || annotation instanceof McpPromptListChanged
							|| annotation instanceof McpResourceListChanged)
							&& this.isModernListChangedMethod(method)) {
						this.modernListChangedClients.computeIfAbsent(annotationType, type -> new HashSet<>())
							.add(client);
					}
				}
			}
		}
	}

	private static String[] clients(Annotation annotation) {
		if (annotation instanceof McpSampling sampling) {
			return sampling.clients();
		}
		if (annotation instanceof McpElicitation elicitation) {
			return elicitation.clients();
		}
		if (annotation instanceof McpLogging logging) {
			return logging.clients();
		}
		if (annotation instanceof McpProgress progress) {
			return progress.clients();
		}
		if (annotation instanceof McpToolListChanged toolListChanged) {
			return toolListChanged.clients();
		}
		if (annotation instanceof McpPromptListChanged promptListChanged) {
			return promptListChanged.clients();
		}
		if (annotation instanceof McpResourceListChanged resourceListChanged) {
			return resourceListChanged.clients();
		}
		return new String[0];
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

	/**
	 * The kinds of elicitation request that an {@code @McpElicitation} method stands for.
	 */
	private record ElicitationHandlerMethod(boolean form, boolean url) {
	}

}
