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

package org.springframework.ai.model.function;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.function.FunctionCallback.SchemaType;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Description;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.KotlinDetector;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;

/**
 * A Spring {@link ApplicationContextAware} implementation that provides a way to retrieve
 * a {@link Function} from the Spring context and wrap it into a {@link FunctionCallback}.
 * <p>
 * The name of the function is determined by the bean name.
 * <p>
 * The description of the function is determined by the following rules:
 * <ul>
 * <li>Provided as a default description</li>
 * <li>Provided as a {@code @Description} annotation on the bean</li>
 * <li>Provided as a {@code @JsonClassDescription} annotation on the input class</li>
 * </ul>
 *
 * @author Christian Tzolov
 * @author Christopher Smith
 * @author Sebastien Deleuze
 */
public class DefaultFunctionCallbackResolver implements ApplicationContextAware, FunctionCallbackResolver {

	private GenericApplicationContext applicationContext;

	private SchemaType schemaType = SchemaType.JSON_SCHEMA;

	public void setSchemaType(SchemaType schemaType) {
		this.schemaType = schemaType;
	}

	@Override
	public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = (GenericApplicationContext) applicationContext;
	}

	@Override
	public FunctionCallback resolve(@NonNull String beanName) {
		ResolvableType functionType = TypeResolverHelper.resolveBeanType(this.applicationContext, beanName);
		ResolvableType functionInputType = (ResolvableType.forType(Supplier.class).isAssignableFrom(functionType))
				? ResolvableType.forType(Void.class) : TypeResolverHelper.getFunctionArgumentType(functionType, 0);

		String functionDescription = resolveFunctionDescription(beanName, functionInputType.toClass());
		Object bean = this.applicationContext.getBean(beanName);

		return buildFunctionCallback(beanName, functionType, functionInputType, functionDescription, bean);
	}

	private String resolveFunctionDescription(String beanName, Class<?> functionInputClass) {
		String functionDescription = "";

		if (!StringUtils.hasText(functionDescription)) {
			Description descriptionAnnotation = this.applicationContext.findAnnotationOnBean(beanName,
					Description.class);
			if (descriptionAnnotation != null) {
				functionDescription = descriptionAnnotation.value();
			}

			if (!StringUtils.hasText(functionDescription)) {
				JsonClassDescription jsonClassDescriptionAnnotation = functionInputClass
					.getAnnotation(JsonClassDescription.class);
				if (jsonClassDescriptionAnnotation != null) {
					functionDescription = jsonClassDescriptionAnnotation.value();
				}
			}

			if (!StringUtils.hasText(functionDescription)) {
				throw new IllegalStateException("Could not determine function description. "
						+ "Please provide a description either as a default parameter, via @Description annotation on the bean "
						+ "or @JsonClassDescription annotation on the input class.");
			}
		}

		return functionDescription;
	}

	private FunctionCallback buildFunctionCallback(String beanName, ResolvableType functionType,
			ResolvableType functionInputType, String functionDescription, Object bean) {

		if (KotlinDetector.isKotlinPresent()) {
			if (KotlinDelegate.isKotlinFunction(functionType.toClass())) {
				return FunctionCallback.builder()
					.schemaType(this.schemaType)
					.description(functionDescription)
					.function(beanName, KotlinDelegate.wrapKotlinFunction(bean))
					.inputType(ParameterizedTypeReference.forType(functionInputType.getType()))
					.build();
			}
			if (KotlinDelegate.isKotlinBiFunction(functionType.toClass())) {
				return FunctionCallback.builder()
					.description(functionDescription)
					.schemaType(this.schemaType)
					.function(beanName, KotlinDelegate.wrapKotlinBiFunction(bean))
					.inputType(ParameterizedTypeReference.forType(functionInputType.getType()))
					.build();
			}
			if (KotlinDelegate.isKotlinSupplier(functionType.toClass())) {
				return FunctionCallback.builder()
					.description(functionDescription)
					.schemaType(this.schemaType)
					.function(beanName, KotlinDelegate.wrapKotlinSupplier(bean))
					.inputType(ParameterizedTypeReference.forType(functionInputType.getType()))
					.build();
			}
		}

		if (bean instanceof Function<?, ?> function) {
			return FunctionCallback.builder()
				.schemaType(this.schemaType)
				.description(functionDescription)
				.function(beanName, function)
				.inputType(ParameterizedTypeReference.forType(functionInputType.getType()))
				.build();
		}
		if (bean instanceof BiFunction<?, ?, ?>) {
			return FunctionCallback.builder()
				.description(functionDescription)
				.schemaType(this.schemaType)
				.function(beanName, (BiFunction<?, ToolContext, ?>) bean)
				.inputType(ParameterizedTypeReference.forType(functionInputType.getType()))
				.build();
		}
		if (bean instanceof Supplier<?> supplier) {
			return FunctionCallback.builder()
				.description(functionDescription)
				.schemaType(this.schemaType)
				.function(beanName, supplier)
				.inputType(ParameterizedTypeReference.forType(functionInputType.getType()))
				.build();
		}
		if (bean instanceof Consumer<?> consumer) {
			return FunctionCallback.builder()
				.description(functionDescription)
				.schemaType(this.schemaType)
				.function(beanName, consumer)
				.inputType(ParameterizedTypeReference.forType(functionInputType.getType()))
				.build();
		}

		throw new IllegalStateException("Unsupported function type");
	}

	private static final class KotlinDelegate {

		public static boolean isKotlinSupplier(Class<?> clazz) {
			return Function0.class.isAssignableFrom(clazz);
		}

		@SuppressWarnings("unchecked")
		public static Supplier<?> wrapKotlinSupplier(Object function) {
			return () -> ((Function0<Object>) function).invoke();
		}

		public static boolean isKotlinFunction(Class<?> clazz) {
			return Function1.class.isAssignableFrom(clazz);
		}

		@SuppressWarnings("unchecked")
		public static Function<?, ?> wrapKotlinFunction(Object function) {
			return t -> ((Function1<Object, Object>) function).invoke(t);
		}

		public static boolean isKotlinBiFunction(Class<?> clazz) {
			return Function2.class.isAssignableFrom(clazz);
		}

		@SuppressWarnings("unchecked")
		public static BiFunction<?, ToolContext, ?> wrapKotlinBiFunction(Object function) {
			return (t, u) -> ((Function2<Object, ToolContext, Object>) function).invoke(t, u);
		}

	}

}
