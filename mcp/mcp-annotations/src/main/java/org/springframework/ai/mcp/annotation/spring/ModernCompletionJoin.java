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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.common.McpPredicates;

/**
 * Attaches the handlers of {@code @McpComplete} methods to the prompts and resource
 * templates they complete, because a modern server serves a completion only through its
 * prompt or template.
 * <p>
 * A handler whose prompt or template method exists but was skipped by the provider, for
 * example because of a reactive return type on a sync server, is dropped with a warning,
 * as its target was. A handler that names nothing fails at startup.
 *
 * @author Christian Tzolov
 */
final class ModernCompletionJoin {

	private static final Log logger = LogFactory.getLog(ModernCompletionJoin.class);

	static final String SYNC_PROMPTS = "McpSyncPrompt";

	static final String SYNC_TEMPLATES = "SyncResourceTemplateRegistration";

	private ModernCompletionJoin() {
	}

	/**
	 * Each prompt with the handler registered under its name.
	 * @param promptObjects the beans the prompts were built from, searched for the
	 * {@code @McpPrompt} methods that the provider skipped
	 * @param type the prompt type, named in the error message
	 * @throws IllegalStateException if a handler names a prompt that no
	 * {@code @McpPrompt} method declares
	 */
	static <P, H> List<P> prompts(List<P> prompts, Map<String, H> handlers, Function<P, String> name,
			BiFunction<P, H, P> withHandler, List<Object> promptObjects, String type) {
		Set<String> declared = annotatedMethods(promptObjects, McpPrompt.class).map(method -> {
			String promptName = method.getAnnotation(McpPrompt.class).name();
			return promptName.isEmpty() ? method.getName() : promptName;
		}).collect(Collectors.toSet());
		return attach(prompts, handlers, name, withHandler, declared, "prompt",
				"@McpComplete methods name prompts that no @McpPrompt method defines: %s. A prompt defined in code "
						+ "takes its completion handler through " + type + ".withCompletionHandler.");
	}

	/**
	 * Each template registration with the handler registered under its URI template.
	 * @param resourceObjects the beans the templates were built from, searched for the
	 * {@code @McpResource} template methods that the provider skipped
	 * @param type the registration type, named in the error message
	 * @throws IllegalStateException if a handler names a URI template that no
	 * {@code @McpResource} method declares, which includes the URI of a static resource
	 */
	static <T, H> List<T> templates(List<T> templates, Map<String, H> handlers, Function<T, String> uriTemplate,
			BiFunction<T, H, T> withHandler, List<Object> resourceObjects, String type) {
		Set<String> declared = annotatedMethods(resourceObjects, McpResource.class)
			.map(method -> method.getAnnotation(McpResource.class).uri())
			.filter(McpPredicates::isUriTemplate)
			.collect(Collectors.toSet());
		return attach(templates, handlers, uriTemplate, withHandler, declared, "resource template",
				"@McpComplete methods name URI templates that no @McpResource method defines: %s. A static resource "
						+ "has no variables to complete, and a template defined in code takes its completion handler "
						+ "through " + type + ".withCompletionHandler.");
	}

	private static Stream<Method> annotatedMethods(List<Object> beans, Class<? extends Annotation> annotation) {
		return beans.stream()
			.flatMap(bean -> Stream.of(AnnotationProviderUtil.beanMethods(bean)))
			.filter(method -> method.isAnnotationPresent(annotation));
	}

	private static <T, H> List<T> attach(List<T> targets, Map<String, H> handlers, Function<T, String> key,
			BiFunction<T, H, T> withHandler, Set<String> declared, String kind, String unattachedMessage) {
		Map<String, H> unattached = new LinkedHashMap<>(handlers);
		List<T> attached = targets.stream().map(target -> {
			H handler = unattached.remove(key.apply(target));
			return handler != null ? withHandler.apply(target, handler) : target;
		}).toList();
		List<String> missing = new ArrayList<>();
		for (String name : unattached.keySet()) {
			if (declared.contains(name)) {
				logger.warn("The completion of " + kind + " '" + name + "' is skipped together with its method; "
						+ "see the warning about that method above");
			}
			else {
				missing.add(name);
			}
		}
		if (!missing.isEmpty()) {
			throw new IllegalStateException(unattachedMessage.formatted(missing));
		}
		return attached;
	}

}
