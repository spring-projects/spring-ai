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

import org.springframework.ai.mcp.annotation.context.MetaProvider;
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
 * @author Nikita Kibitkin
 */
public class AbstractAnnotatedMethodBeanFactoryInitializationAotProcessor extends AnnotatedMethodDiscovery
		implements BeanFactoryInitializationAotProcessor {

	private static final String META_PROVIDER_ATTRIBUTE_NAME = "metaProvider";

	private static final LogAccessor logger = new LogAccessor(AbstractAnnotatedMethodBeanPostProcessor.class);

	public AbstractAnnotatedMethodBeanFactoryInitializationAotProcessor(
			Set<Class<? extends Annotation>> targetAnnotations) {
		super(targetAnnotations);
	}

	@Override
	public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
		List<Class<?>> types = new ArrayList<>();
		Set<Class<? extends MetaProvider>> metaProviderTypes = new LinkedHashSet<>();
		for (String beanName : beanFactory.getBeanDefinitionNames()) {
			Class<?> beanClass = beanFactory.getType(beanName);
			if (beanClass == null) {
				continue;
			}
			Set<Class<? extends Annotation>> classes = this.scan(beanClass);
			if (!classes.isEmpty()) {
				types.add(beanClass);
				metaProviderTypes.addAll(this.scanMetaProviderTypes(beanClass));
			}
		}
		return (generationContext, beanFactoryInitializationCode) -> {
			RuntimeHints runtimeHints = generationContext.getRuntimeHints();
			for (Class<?> typeReference : types) {
				runtimeHints.reflection().registerType(typeReference, MemberCategory.values());
				logger.info("registering " + typeReference.getName() + " for reflection");
			}
			for (Class<? extends MetaProvider> metaProviderType : metaProviderTypes) {
				runtimeHints.reflection().registerType(metaProviderType, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
				logger.info("registering " + metaProviderType.getName() + " for reflective constructor invocation");
			}
		};
	}

	private Set<Class<? extends MetaProvider>> scanMetaProviderTypes(Class<?> beanClass) {
		Set<Class<? extends MetaProvider>> metaProviderTypes = new LinkedHashSet<>();
		ReflectionUtils.doWithMethods(beanClass, method -> this.targetAnnotations.forEach(annotationType -> {
			Annotation annotation = AnnotationUtils.findAnnotation(method, annotationType);
			if (annotation != null) {
				this.addMetaProviderType(annotation, metaProviderTypes);
			}
		}));
		return metaProviderTypes;
	}

	private void addMetaProviderType(Annotation annotation, Set<Class<? extends MetaProvider>> metaProviderTypes) {
		Object metaProviderType = AnnotationUtils.getValue(annotation, META_PROVIDER_ATTRIBUTE_NAME);
		if (metaProviderType instanceof Class<?> type && MetaProvider.class.isAssignableFrom(type)) {
			metaProviderTypes.add(type.asSubclass(MetaProvider.class));
		}
	}

}
