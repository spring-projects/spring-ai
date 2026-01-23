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

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.log.LogAccessor;

/**
 * @author Josh Long
 */
public class AbstractAnnotatedMethodBeanFactoryInitializationAotProcessor extends AnnotatedMethodDiscovery
		implements BeanFactoryInitializationAotProcessor {

	private static final LogAccessor logger = new LogAccessor(AbstractAnnotatedMethodBeanPostProcessor.class);

	public AbstractAnnotatedMethodBeanFactoryInitializationAotProcessor(
			Set<Class<? extends Annotation>> targetAnnotations) {
		super(targetAnnotations);
	}

	@Override
	public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
		List<Class<?>> types = new ArrayList<>();
		for (String beanName : beanFactory.getBeanDefinitionNames()) {
			Class<?> beanClass = beanFactory.getType(beanName);
			if (beanClass == null) {
				continue;
			}
			Set<Class<? extends Annotation>> classes = this.scan(beanClass);
			if (!classes.isEmpty()) {
				types.add(beanClass);
			}
		}
		return (generationContext, beanFactoryInitializationCode) -> {
			RuntimeHints runtimeHints = generationContext.getRuntimeHints();
			for (Class<?> typeReference : types) {
				runtimeHints.reflection().registerType(typeReference, MemberCategory.values());
				logger.info("registering " + typeReference.getName() + " for reflection");
			}
		};
	}

}
