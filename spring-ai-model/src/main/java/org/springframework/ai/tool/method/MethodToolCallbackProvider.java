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

package org.springframework.ai.tool.method;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.execution.ToolCallResultConverter;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.support.ToolDefinitions;
import org.springframework.ai.tool.support.ToolUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * A {@link ToolCallbackProvider} that builds {@link ToolCallback} instances from
 * {@link Tool}-annotated methods.
 *
 * @author Thomas Vitale
 * @author Christian Tzolov
 * @author Jewoo Shin
 * @since 1.0.0
 */
public final class MethodToolCallbackProvider implements ToolCallbackProvider {

	private static final Log logger = LogFactory.getLog(MethodToolCallbackProvider.class);

	private final List<Object> toolObjects;

	private final @Nullable ToolCallResultConverter toolCallResultConverter;

	private MethodToolCallbackProvider(List<Object> toolObjects,
			@Nullable ToolCallResultConverter toolCallResultConverter) {
		Assert.notNull(toolObjects, "toolObjects cannot be null");
		Assert.noNullElements(toolObjects, "toolObjects cannot contain null elements");
		assertToolAnnotatedMethodsPresent(toolObjects);
		this.toolObjects = toolObjects;
		this.toolCallResultConverter = toolCallResultConverter;
		validateToolCallbacks(getToolCallbacks());
	}

	private void assertToolAnnotatedMethodsPresent(List<Object> toolObjects) {

		for (Object toolObject : toolObjects) {
			List<Method> toolMethods = Stream
				.of(ReflectionUtils.getDeclaredMethods(
						AopUtils.isAopProxy(toolObject) ? AopUtils.getTargetClass(toolObject) : toolObject.getClass()))
				.filter(this::isToolAnnotatedMethod)
				.filter(toolMethod -> !isFunctionalType(toolMethod))
				.toList();

			if (toolMethods.isEmpty()) {
				throw new IllegalArgumentException("No @Tool annotated methods found in " + toolObject + ". "
						+ "Did you mean to pass a ToolCallback or ToolCallbackProvider? If so, use"
						+ " .tools(toolCallback) or .toolCallbacks(toolCallback) instead.");
			}
		}
	}

	@Override
	public ToolCallback[] getToolCallbacks() {
		var toolCallbacks = this.toolObjects.stream()
			.map(toolObject -> Stream
				.of(ReflectionUtils.getDeclaredMethods(
						AopUtils.isAopProxy(toolObject) ? AopUtils.getTargetClass(toolObject) : toolObject.getClass()))
				.filter(this::isToolAnnotatedMethod)
				.filter(toolMethod -> !isFunctionalType(toolMethod))
				.filter(ReflectionUtils.USER_DECLARED_METHODS::matches)
				.map(toolMethod -> MethodToolCallback.builder()
					.toolDefinition(ToolDefinitions.from(toolMethod))
					.toolMetadata(ToolMetadata.from(toolMethod))
					.toolMethod(toolMethod)
					.toolObject(toolObject)
					.toolCallResultConverter(
							ToolUtils.getToolCallResultConverter(toolMethod, this.toolCallResultConverter))
					.build())
				.toArray(ToolCallback[]::new))
			.flatMap(Stream::of)
			.toArray(ToolCallback[]::new);

		validateToolCallbacks(toolCallbacks);

		return toolCallbacks;
	}

	private boolean isFunctionalType(Method toolMethod) {
		var isFunction = ClassUtils.isAssignable(Function.class, toolMethod.getReturnType())
				|| ClassUtils.isAssignable(Supplier.class, toolMethod.getReturnType())
				|| ClassUtils.isAssignable(Consumer.class, toolMethod.getReturnType());

		if (isFunction) {
			if (logger.isWarnEnabled()) {
				logger.warn("Method " + toolMethod.getName() + "is annotated with @Tool but returns a functional type. "
						+ "This is not supported and the method will be ignored.");
			}
		}

		return isFunction;
	}

	private boolean isToolAnnotatedMethod(Method method) {
		Tool annotation = AnnotationUtils.findAnnotation(method, Tool.class);
		return Objects.nonNull(annotation);
	}

	private void validateToolCallbacks(ToolCallback[] toolCallbacks) {
		List<String> duplicateToolNames = ToolUtils.getDuplicateToolNames(toolCallbacks);
		if (!duplicateToolNames.isEmpty()) {
			throw new IllegalArgumentException("Multiple tools with the same name (%s) found in sources: %s".formatted(
					String.join(", ", duplicateToolNames),
					this.toolObjects.stream().map(o -> o.getClass().getName()).collect(Collectors.joining(", "))));
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private List<Object> toolObjects = new ArrayList<>();

		private @Nullable ToolCallResultConverter toolCallResultConverter;

		private Builder() {
		}

		public Builder toolObjects(Object... toolObjects) {
			Assert.notNull(toolObjects, "toolObjects cannot be null");
			this.toolObjects = Arrays.asList(toolObjects);
			return this;
		}

		/**
		 * Set the default {@link ToolCallResultConverter} to apply when a tool method
		 * leaves {@link Tool#resultConverter()} at its default value of
		 * {@link org.springframework.ai.tool.execution.DefaultToolCallResultConverter}.
		 * <p>
		 * A {@link Tool} annotation that explicitly declares a {@code resultConverter}
		 * other than {@code DefaultToolCallResultConverter} takes precedence over this
		 * default. Because the annotation's default value is also
		 * {@code DefaultToolCallResultConverter}, a tool that explicitly sets
		 * {@code resultConverter = DefaultToolCallResultConverter.class} is
		 * indistinguishable from the unset case and will therefore receive this default.
		 * @param toolCallResultConverter the converter to use as the default
		 * @return this builder
		 * @since 2.0.0
		 */
		public Builder toolCallResultConverter(ToolCallResultConverter toolCallResultConverter) {
			this.toolCallResultConverter = toolCallResultConverter;
			return this;
		}

		public MethodToolCallbackProvider build() {
			return new MethodToolCallbackProvider(this.toolObjects, this.toolCallResultConverter);
		}

	}

}
