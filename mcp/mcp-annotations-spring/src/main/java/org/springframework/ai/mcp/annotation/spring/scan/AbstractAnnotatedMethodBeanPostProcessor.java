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

package org.springframework.ai.mcp.annotation.spring.scan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.log.LogAccessor;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Christian Tzolov
 * @author Josh Long
 */
public abstract class AbstractAnnotatedMethodBeanPostProcessor
		implements BeanFactoryInitializationAotProcessor, BeanPostProcessor {

	private static final LogAccessor logger = new LogAccessor(AbstractAnnotatedMethodBeanPostProcessor.class);

	@Override
	public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {

		List<org.springframework.aot.hint.TypeReference> types = new ArrayList<>();

		for (String beanName : beanFactory.getBeanDefinitionNames()) {
			Class<?> beanClass = beanFactory.getType(beanName);
			Set<Class<? extends Annotation>> classes = scan(beanClass);
			if (!classes.isEmpty()) {
				types.add(TypeReference.of(beanClass.getName()));
			}
		}
		return (generationContext, beanFactoryInitializationCode) -> {
			RuntimeHints runtimeHints = generationContext.getRuntimeHints();
			for (TypeReference typeReference : types) {
				runtimeHints.reflection().registerType(typeReference, MemberCategory.values());
				logger.info("registering " + typeReference.getName() + " for reflection");
			}
		};
	}

	private final AbstractMcpAnnotatedBeans registry;

	// Define the annotations to scan for
	private final Set<Class<? extends Annotation>> targetAnnotations;

	public AbstractAnnotatedMethodBeanPostProcessor(AbstractMcpAnnotatedBeans registry,
			Set<Class<? extends Annotation>> targetAnnotations) {
		Assert.notNull(registry, "AnnotatedBeanRegistry must not be null");
		Assert.notEmpty(targetAnnotations, "Target annotations must not be empty");

		this.registry = registry;
		this.targetAnnotations = targetAnnotations;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		Class<?> beanClass = AopUtils.getTargetClass(bean); // Handle proxied beans

		Set<Class<? extends Annotation>> foundAnnotations = scan(beanClass);

		// Register the bean if it has any of our target annotations
		if (!foundAnnotations.isEmpty()) {
			this.registry.addMcpAnnotatedBean(bean, foundAnnotations);
		}

		return bean;
	}

	private Set<Class<? extends Annotation>> scan(Class<?> beanClass) {
		Set<Class<? extends Annotation>> foundAnnotations = new HashSet<>();

		// Scan all methods in the bean class
		ReflectionUtils.doWithMethods(beanClass, method -> {
			this.targetAnnotations.forEach(annotationType -> {
				if (AnnotationUtils.findAnnotation(method, annotationType) != null) {
					foundAnnotations.add(annotationType);
				}
			});
		});
		return foundAnnotations;
	}

}
