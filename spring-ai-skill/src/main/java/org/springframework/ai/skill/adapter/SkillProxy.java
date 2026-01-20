/*
 * Copyright 2025-2026 the original author or authors.
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

package org.springframework.ai.skill.adapter;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.skill.core.Skill;
import org.springframework.ai.skill.core.SkillMetadata;
import org.springframework.ai.skill.core.SkillRegistrar;
import org.springframework.ai.skill.exception.SkillInvocationException;
import org.springframework.ai.tool.ToolCallback;

/**
 * Proxy-based Skill implementation wrapping user POJOs annotated with {@code @Skill}.
 *
 * <p>
 * This class acts as a dynamic proxy that delegates method calls to the underlying
 * annotated POJO instance using reflection. It supports capability extension through JDK
 * dynamic proxies.
 *
 * <p>
 * INTERNAL USE ONLY: Framework internal class subject to change.
 *
 * @author LinPeng Zhang
 * @see Skill
 * @see SkillRegistrar
 * @since 1.1.3
 */
public final class SkillProxy implements Skill {

	private final SkillMetadata metadata;

	private final Object delegate;

	private final Map<String, Method> extensionMethods;

	private final Map<Class<?>, Object> capabilityProxies = new ConcurrentHashMap<>();

	/**
	 * Constructor.
	 * @param metadata skill metadata
	 * @param delegate user POJO instance
	 * @param extensionMethods extension method mappings
	 */
	public SkillProxy(SkillMetadata metadata, Object delegate, Map<String, Method> extensionMethods) {
		this.metadata = Objects.requireNonNull(metadata, "metadata cannot be null");
		this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
		this.extensionMethods = Objects.requireNonNull(extensionMethods, "extensionMethods cannot be null");
	}

	@Override
	public SkillMetadata getMetadata() {
		return this.metadata;
	}

	/**
	 * Gets skill content.
	 * @return skill content
	 * @throws UnsupportedOperationException if no @SkillContent method found
	 * @throws SkillInvocationException if invocation fails
	 */
	@Override
	public String getContent() {
		Method contentMethod = this.extensionMethods.get("content");
		if (contentMethod == null) {
			throw new UnsupportedOperationException(
					"Skill '" + this.getName() + "' does not have @SkillContent annotated method");
		}

		try {
			contentMethod.setAccessible(true);
			Object result = contentMethod.invoke(this.delegate);

			// Validate return type
			if (result == null) {
				throw new SkillInvocationException(this.getName(), contentMethod.getName(),
						"@SkillContent method '" + contentMethod.getName() + "' returned null. "
								+ "The method must return a non-null String value.",
						null);
			}

			if (!(result instanceof String)) {
				throw new SkillInvocationException(this.getName(), contentMethod.getName(),
						"@SkillContent method '" + contentMethod.getName() + "' must return String, " + "but returned "
								+ result.getClass().getName() + ". " + "Ensure the method signature is: public String "
								+ contentMethod.getName() + "()",
						null);
			}

			return (String) result;
		}
		catch (SkillInvocationException e) {
			throw e; // Re-throw our custom exception
		}
		catch (Exception e) {
			throw new SkillInvocationException(this.getName(), contentMethod.getName(),
					"Failed to invoke @SkillContent method '" + contentMethod.getName() + "' on skill '"
							+ this.getName() + "'. " + "Ensure the method is accessible and does not throw exceptions.",
					e);
		}
	}

	/**
	 * Gets tool callbacks.
	 * @return tool callbacks (empty if no @SkillTools method)
	 * @throws SkillInvocationException if invocation fails
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<ToolCallback> getTools() {
		Method toolsMethod = this.extensionMethods.get("tools");
		if (toolsMethod == null) {
			return Collections.emptyList(); // No @SkillTools method, return empty list
		}

		try {
			toolsMethod.setAccessible(true);
			Object result = toolsMethod.invoke(this.delegate);

			// Validate return type
			if (result == null) {
				throw new SkillInvocationException(this.getName(), toolsMethod.getName(),
						"@SkillTools method '" + toolsMethod.getName() + "' returned null. "
								+ "The method must return a non-null List<ToolCallback>.",
						null);
			}

			if (!(result instanceof List<?> list)) {
				throw new SkillInvocationException(this.getName(), toolsMethod.getName(),
						"@SkillTools method '" + toolsMethod.getName() + "' must return List<ToolCallback>, "
								+ "but returned " + result.getClass().getName() + ". "
								+ "Ensure the method signature is: public List<ToolCallback> " + toolsMethod.getName()
								+ "()",
						null);
			}

			// Check list contents ( the best effort - generics are erased at runtime)
			if (!list.isEmpty()) {
				Object firstElement = list.get(0);
				if (firstElement != null && !(firstElement instanceof ToolCallback)) {
					throw new SkillInvocationException(this.getName(), toolsMethod.getName(),
							"@SkillTools method '" + toolsMethod.getName()
									+ "' returned a list containing invalid elements. "
									+ "Expected List<ToolCallback>, but found element of type "
									+ firstElement.getClass().getName() + ". "
									+ "Ensure all list elements are instances of ToolCallback.",
							null);
				}
			}

			return (List<ToolCallback>) result;
		}
		catch (SkillInvocationException e) {
			throw e; // Re-throw our custom exception
		}
		catch (Exception e) {
			throw new SkillInvocationException(this.getName(), toolsMethod.getName(),
					"Failed to invoke @SkillTools method '" + toolsMethod.getName() + "' on skill '" + this.getName()
							+ "'. " + "Ensure the method is accessible and does not throw exceptions.",
					e);
		}
	}

	@Override
	public <T> boolean supports(Class<T> capabilityType) {
		if (!capabilityType.isInterface()) {
			return false;
		}

		String expectedKey = this.getCapabilityKey(capabilityType);
		return expectedKey != null && this.extensionMethods.containsKey(expectedKey);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T as(Class<T> capabilityType) {
		if (!capabilityType.isInterface()) {
			throw new IllegalArgumentException("Capability must be an interface: " + capabilityType.getName());
		}

		if (!this.supports(capabilityType)) {
			throw new UnsupportedOperationException(
					"Skill '" + this.getName() + "' does not support capability: " + capabilityType.getName());
		}

		return (T) this.capabilityProxies.computeIfAbsent(capabilityType, this::createCapabilityProxy);
	}

	/**
	 * Creates dynamic proxy for capability interface.
	 * @param capability capability interface
	 * @return proxy instance
	 */
	private Object createCapabilityProxy(Class<?> capability) {
		return Proxy.newProxyInstance(capability.getClassLoader(), new Class<?>[] { capability },
				(proxy, method, args) -> {
					String key = this.methodToExtensionKey(method);
					Method extensionMethod = this.extensionMethods.get(key);

					if (extensionMethod == null) {
						throw new UnsupportedOperationException("No implementation found for method '"
								+ method.getName() + "' in capability interface '" + capability.getName() + "' "
								+ "for skill '" + this.getName() + "'. " + "Expected an annotated method with key '"
								+ key + "'.");
					}

					try {
						extensionMethod.setAccessible(true);
						return extensionMethod.invoke(this.delegate, args);
					}
					catch (Exception e) {
						throw new SkillInvocationException(this.getName(), extensionMethod.getName(),
								"Failed to invoke capability method '" + method.getName() + "' (mapped to '"
										+ extensionMethod.getName() + "') " + "for capability '" + capability.getName()
										+ "' " + "on skill '" + this.getName() + "'.",
								e);
					}
				});
	}

	private @Nullable String getCapabilityKey(Class<?> capability) {
		Method[] methods = capability.getDeclaredMethods();
		if (methods.length == 0) {
			return null;
		}
		return this.methodToExtensionKey(methods[0]);
	}

	private String methodToExtensionKey(Method method) {
		String methodName = method.getName();
		if (methodName.startsWith("get") && methodName.length() > 3) {
			return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
		}
		return methodName;
	}

}
