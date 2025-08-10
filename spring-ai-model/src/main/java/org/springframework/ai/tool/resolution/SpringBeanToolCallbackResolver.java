/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.tool.resolution;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.support.ToolUtils;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.ai.util.json.schema.SchemaType;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Description;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.KotlinDetector;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A Spring {@link ApplicationContext}-based implementation that provides a way to
 * retrieve a bean from the Spring context and wrap it into a {@link ToolCallback}.
 *
 * @author Christian Tzolov
 * @author Christopher Smith
 * @author Sebastien Deleuze
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class SpringBeanToolCallbackResolver implements ToolCallbackResolver {

	private static final Logger logger = LoggerFactory.getLogger(SpringBeanToolCallbackResolver.class);

	private static final Map<String, ToolCallback> toolCallbacksCache = new HashMap<>();

	private static final SchemaType DEFAULT_SCHEMA_TYPE = SchemaType.JSON_SCHEMA;

	private final GenericApplicationContext applicationContext;

	private final SchemaType schemaType;

	public SpringBeanToolCallbackResolver(GenericApplicationContext applicationContext,
			@Nullable SchemaType schemaType) {
		Assert.notNull(applicationContext, "applicationContext cannot be null");

		this.applicationContext = applicationContext;
		this.schemaType = schemaType != null ? schemaType : DEFAULT_SCHEMA_TYPE;
	}

	@Override
	public ToolCallback resolve(String toolName) {
		Assert.hasText(toolName, "toolName cannot be null or empty");

		logger.debug("ToolCallback resolution attempt from Spring application context");

		ToolCallback resolvedToolCallback = toolCallbacksCache.get(toolName);

		if (resolvedToolCallback != null) {
			return resolvedToolCallback;
		}

		try {
			ResolvableType toolType = TypeResolverHelper.resolveBeanType(this.applicationContext, toolName);
			ResolvableType toolInputType = (ResolvableType.forType(Supplier.class).isAssignableFrom(toolType))
					? ResolvableType.forType(Void.class) : TypeResolverHelper.getFunctionArgumentType(toolType, 0);

			String toolDescription = resolveToolDescription(toolName, toolInputType.toClass());
			Object bean = this.applicationContext.getBean(toolName);

			resolvedToolCallback = buildToolCallback(toolName, toolType, toolInputType, toolDescription, bean);

			toolCallbacksCache.put(toolName, resolvedToolCallback);

			return resolvedToolCallback;
		}
		catch (Exception e) {
			logger.debug("ToolCallback resolution failed from Spring application context", e);
			return null;
		}
	}

	public SchemaType getSchemaType() {
		return this.schemaType;
	}

	private String resolveToolDescription(String toolName, Class<?> toolInputType) {
		Description descriptionAnnotation = this.applicationContext.findAnnotationOnBean(toolName, Description.class);
		if (descriptionAnnotation != null && StringUtils.hasText(descriptionAnnotation.value())) {
			return descriptionAnnotation.value();
		}

		JsonClassDescription jsonClassDescriptionAnnotation = toolInputType.getAnnotation(JsonClassDescription.class);
		if (jsonClassDescriptionAnnotation != null && StringUtils.hasText(jsonClassDescriptionAnnotation.value())) {
			return jsonClassDescriptionAnnotation.value();
		}

		return ToolUtils.getToolDescriptionFromName(toolName);
	}

	private ToolCallback buildToolCallback(String toolName, ResolvableType toolType, ResolvableType toolInputType,
			String toolDescription, Object bean) {
		if (KotlinDetector.isKotlinPresent()) {
			if (KotlinDelegate.isKotlinFunction(toolType.toClass())) {
				return FunctionToolCallback.builder(toolName, KotlinDelegate.wrapKotlinFunction(bean))
					.description(toolDescription)
					.inputSchema(generateSchema(toolInputType))
					.inputType(ParameterizedTypeReference.forType(toolInputType.getType()))
					.build();
			}
			if (KotlinDelegate.isKotlinBiFunction(toolType.toClass())) {
				return FunctionToolCallback.builder(toolName, KotlinDelegate.wrapKotlinBiFunction(bean))
					.description(toolDescription)
					.inputSchema(generateSchema(toolInputType))
					.inputType(ParameterizedTypeReference.forType(toolInputType.getType()))
					.build();
			}
			if (KotlinDelegate.isKotlinSupplier(toolType.toClass())) {
				return FunctionToolCallback.builder(toolName, KotlinDelegate.wrapKotlinSupplier(bean))
					.description(toolDescription)
					.inputSchema(generateSchema(toolInputType))
					.inputType(ParameterizedTypeReference.forType(toolInputType.getType()))
					.build();
			}
		}

		if (bean instanceof Function<?, ?> function) {
			return FunctionToolCallback.builder(toolName, function)
				.description(toolDescription)
				.inputSchema(generateSchema(toolInputType))
				.inputType(ParameterizedTypeReference.forType(toolInputType.getType()))
				.build();
		}
		if (bean instanceof BiFunction<?, ?, ?>) {
			return FunctionToolCallback.builder(toolName, (BiFunction<?, ToolContext, ?>) bean)
				.description(toolDescription)
				.inputSchema(generateSchema(toolInputType))
				.inputType(ParameterizedTypeReference.forType(toolInputType.getType()))
				.build();
		}
		if (bean instanceof Supplier<?> supplier) {
			return FunctionToolCallback.builder(toolName, supplier)
				.description(toolDescription)
				.inputSchema(generateSchema(toolInputType))
				.inputType(ParameterizedTypeReference.forType(toolInputType.getType()))
				.build();
		}
		if (bean instanceof Consumer<?> consumer) {
			return FunctionToolCallback.builder(toolName, consumer)
				.description(toolDescription)
				.inputSchema(generateSchema(toolInputType))
				.inputType(ParameterizedTypeReference.forType(toolInputType.getType()))
				.build();
		}

		throw new IllegalStateException(
				"Unsupported bean type. Support types: Function, BiFunction, Supplier, Consumer.");
	}

	private String generateSchema(ResolvableType toolInputType) {
		if (this.schemaType == SchemaType.OPEN_API_SCHEMA) {
			return JsonSchemaGenerator.generateForType(toolInputType.getType(),
					JsonSchemaGenerator.SchemaOption.UPPER_CASE_TYPE_VALUES);
		}
		return JsonSchemaGenerator.generateForType(toolInputType.getType());
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private GenericApplicationContext applicationContext;

		private SchemaType schemaType;

		public Builder applicationContext(GenericApplicationContext applicationContext) {
			this.applicationContext = applicationContext;
			return this;
		}

		public Builder schemaType(SchemaType schemaType) {
			this.schemaType = schemaType;
			return this;
		}

		public SpringBeanToolCallbackResolver build() {
			return new SpringBeanToolCallbackResolver(this.applicationContext, this.schemaType);
		}

	}

	private static final class KotlinDelegate {

		public static boolean isKotlinSupplier(Class<?> clazz) {
			return Function0.class.isAssignableFrom(clazz);
		}

		@SuppressWarnings("unchecked")
		public static Supplier<?> wrapKotlinSupplier(Object bean) {
			return () -> ((Function0<Object>) bean).invoke();
		}

		public static boolean isKotlinFunction(Class<?> clazz) {
			return Function1.class.isAssignableFrom(clazz);
		}

		@SuppressWarnings("unchecked")
		public static Function<?, ?> wrapKotlinFunction(Object bean) {
			return t -> ((Function1<Object, Object>) bean).invoke(t);
		}

		public static boolean isKotlinBiFunction(Class<?> clazz) {
			return Function2.class.isAssignableFrom(clazz);
		}

		@SuppressWarnings("unchecked")
		public static BiFunction<?, ToolContext, ?> wrapKotlinBiFunction(Object bean) {
			return (t, u) -> ((Function2<Object, ToolContext, Object>) bean).invoke(t, u);
		}

	}

}
