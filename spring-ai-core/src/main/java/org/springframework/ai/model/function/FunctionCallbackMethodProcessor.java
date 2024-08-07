/*
 * Copyright 2024 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.model.function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.framework.autoproxy.AutoProxyUtils;
import org.springframework.aop.scope.ScopedObject;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link BeanFactoryPostProcessor} that processes {@link FunctionCalling} annotations.
 * <p>
 * <p>Any such annotated method is registered as a {@link MethodFunctionCallback} bean in the
 * application context.
 * <p>
 * <p>Processing of {@code @FunctionCalling} annotations can be customized through the
 * {@link FunctionCalling} annotation.
 * <p>
 *
 * @see FunctionCalling
 * @see MethodFunctionCallback
 * @author kamosama
 */
public class FunctionCallbackMethodProcessor
		implements SmartInitializingSingleton, BeanFactoryPostProcessor {

	protected final Log logger = LogFactory.getLog(getClass());

	private final Set<Class<?>> nonAnnotatedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>(64));

	@Nullable
	private ConfigurableListableBeanFactory beanFactory;


	@Override
	public void afterSingletonsInstantiated() {
		Assert.state(this.beanFactory != null, "No ConfigurableListableBeanFactory set");

		String[] beanNames = beanFactory.getBeanNamesForType(Object.class);

		for (String beanName : beanNames) {
			if (ScopedProxyUtils.isScopedTarget(beanName)) {
				continue;
			}
			Class<?> type = null;
			try {
				type = AutoProxyUtils.determineTargetClass(beanFactory, beanName);
			} catch (Throwable ex) {
				// An unresolvable bean type, probably from a lazy bean - let's ignore it.
				if (logger.isDebugEnabled()) {
					logger.debug("Could not resolve target class for bean with name '" + beanName + "'", ex);
				}
			}
			if (type == null) {
				continue;
			}
			if (ScopedObject.class.isAssignableFrom(type)) {
				try {
					Class<?> targetClass = AutoProxyUtils.determineTargetClass(
							beanFactory, ScopedProxyUtils.getTargetBeanName(beanName));
					if (targetClass != null) {
						type = targetClass;
					}
				} catch (Throwable ex) {
					// An invalid scoped proxy arrangement - let's ignore it.
					if (logger.isDebugEnabled()) {
						logger.debug("Could not resolve target bean for scoped proxy '" + beanName + "'", ex);
					}
				}
			}
			try {
				processBean(beanName, type);
			} catch (Throwable ex) {
				throw new BeanInitializationException("Failed to process @FunctionCalling " +
						"annotation on bean with name '" + beanName + "'", ex);
			}
		}
	}

	private void processBean(final String beanName, final Class<?> targetType) {
		Assert.state(this.beanFactory != null, "No ConfigurableListableBeanFactory set");

		if (!this.nonAnnotatedClasses.contains(targetType)
				&& AnnotationUtils.isCandidateClass(targetType, FunctionCalling.class)
				&& !isSpringContainerClass(targetType)
		) {

			Map<Method, FunctionCalling> annotatedMethods = null;
			try {
				annotatedMethods = MethodIntrospector.selectMethods(targetType,
						(MethodIntrospector.MetadataLookup<FunctionCalling>) method ->
								AnnotatedElementUtils.findMergedAnnotation(method, FunctionCalling.class));
			} catch (Throwable ex) {
				// An unresolvable type in a method signature, probably from a lazy bean - let's ignore it.
				if (logger.isDebugEnabled()) {
					logger.debug("Could not resolve methods for bean with name '" + beanName + "'", ex);
				}
			}

			if (CollectionUtils.isEmpty(annotatedMethods)) {
				this.nonAnnotatedClasses.add(targetType);
				if (logger.isTraceEnabled()) {
					logger.trace("No @FunctionCalling annotations found on bean class: " + targetType.getName());
				}
			} else {
				// Non-empty set of methods
				annotatedMethods.forEach((method, annotation) -> {
					String name = annotation.name().isEmpty() ? method.getName() : annotation.name();
					ReflectionUtils.makeAccessible(method);
					var functionObject = Modifier.isStatic(method.getModifiers()) ? null : beanFactory.getBean(beanName);
					MethodFunctionCallback callback = MethodFunctionCallback.builder()
							.withFunctionObject(functionObject)
							.withMethod(method)
							.withDescription(annotation.description())
							.build();
					beanFactory.registerSingleton(name, callback);
				});

				if (logger.isDebugEnabled()) {
					logger.debug(annotatedMethods.size() + " @FunctionCalling methods processed on bean '" +
							beanName + "': " + annotatedMethods);
				}
			}
		}
	}

	/**
	 * Determine whether the given class is an {@code org.springframework}
	 * bean class that is not annotated as a user or test {@link Component}...
	 * which indicates that there is no {@link FunctionCalling} to be found there.
	 */
	private static boolean isSpringContainerClass(Class<?> clazz) {
		return (clazz.getName().startsWith("org.springframework.") &&
				!AnnotatedElementUtils.isAnnotated(ClassUtils.getUserClass(clazz), Component.class));
	}

	@Override
	public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

}
