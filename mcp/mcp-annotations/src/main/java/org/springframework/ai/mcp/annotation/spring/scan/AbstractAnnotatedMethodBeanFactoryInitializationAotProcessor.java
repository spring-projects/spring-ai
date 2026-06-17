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

package org.springframework.ai.mcp.annotation.spring.scan;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.log.LogAccessor;
import org.springframework.util.ReflectionUtils;

/**
 * @author Josh Long
 * @author Sebastien Deleuze
 */
public class AbstractAnnotatedMethodBeanFactoryInitializationAotProcessor extends AnnotatedMethodDiscovery
		implements BeanFactoryInitializationAotProcessor {

	private static final LogAccessor logger = new LogAccessor(AbstractAnnotatedMethodBeanPostProcessor.class);

	/**
	 * Name of the annotation attribute referencing a {@code MetaProvider} implementation
	 * that is instantiated reflectively at runtime.
	 */
	private static final String META_PROVIDER_ATTRIBUTE = "metaProvider";

	public AbstractAnnotatedMethodBeanFactoryInitializationAotProcessor(
			Set<Class<? extends Annotation>> targetAnnotations) {
		super(targetAnnotations);
	}

	@Override
	public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
		List<Class<?>> types = new ArrayList<>();
		Set<Class<?>> metaProviderTypes = new LinkedHashSet<>();
		for (String beanName : beanFactory.getBeanDefinitionNames()) {
			Class<?> beanClass = beanFactory.getType(beanName);
			if (beanClass == null) {
				continue;
			}
			Set<Class<? extends Annotation>> classes = this.scan(beanClass);
			if (!classes.isEmpty()) {
				types.add(beanClass);
				collectMetaProviderTypes(beanClass, metaProviderTypes);
			}
		}
		return (generationContext, beanFactoryInitializationCode) -> {
			RuntimeHints runtimeHints = generationContext.getRuntimeHints();
			for (Class<?> typeReference : types) {
				runtimeHints.reflection().registerType(typeReference, MemberCategory.values());
				if (logger.isInfoEnabled()) {
					logger.info("registering " + typeReference.getName() + " for reflection");
				}
			}
			for (Class<?> metaProviderType : metaProviderTypes) {
				runtimeHints.reflection().registerType(metaProviderType, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
				if (logger.isDebugEnabled()) {
					logger.debug("Registering MetaProvider " + metaProviderType.getName() + " for reflection");
				}
			}
		};
	}

	/**
	 * Collect the {@code MetaProvider} implementations referenced by the
	 * {@value #META_PROVIDER_ATTRIBUTE} attribute of the target annotations present on
	 * the methods of the given bean class. Those types are instantiated reflectively
	 * through their no-arg constructor at runtime, so they must be registered for
	 * reflection in a native image.
	 */
	private void collectMetaProviderTypes(Class<?> beanClass, Set<Class<?>> metaProviderTypes) {
		ReflectionUtils.doWithMethods(beanClass, method -> {
			for (Class<? extends Annotation> annotationType : this.targetAnnotations) {
				Annotation annotation = AnnotationUtils.findAnnotation(method, annotationType);
				if (annotation == null) {
					continue;
				}
				Object value = AnnotationUtils.getValue(annotation, META_PROVIDER_ATTRIBUTE);
				if (value instanceof Class<?> metaProviderType) {
					metaProviderTypes.add(metaProviderType);
				}
			}
		});
	}

}
