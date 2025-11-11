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
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeHint;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit Tests for {@link AbstractAnnotatedMethodBeanFactoryInitializationAotProcessor}.
 *
 * @author lance
 */
class AbstractAnnotatedMethodBeanFactoryInitializationAotProcessorTests {

	@Test
	void testProcessAheadOfTime() {
		// register bean(AnnotatedBean,PlainBean)
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerBeanDefinition(AnnotatedBean.class.getName(), new RootBeanDefinition(AnnotatedBean.class));
		beanFactory.registerBeanDefinition(PlainBean.class.getName(), new RootBeanDefinition(PlainBean.class));

		PlainBean plainBean = beanFactory.getBean(PlainBean.class);
		assertThat(plainBean).isNotNull();

		// create AbstractAnnotatedMethodBeanFactoryInitializationAotProcessor
		Set<Class<? extends Annotation>> annotations = Set.of(MyAnnotation.class);
		AbstractAnnotatedMethodBeanFactoryInitializationAotProcessor processor = new AbstractAnnotatedMethodBeanFactoryInitializationAotProcessor(
				annotations);

		// execute processAheadOfTime
		BeanFactoryInitializationAotContribution aotContribution = processor.processAheadOfTime(beanFactory);
		assertThat(aotContribution).isNotNull();

		// execute Contribution
		GenerationContext generationContext = mock(GenerationContext.class);
		when(generationContext.getRuntimeHints()).thenReturn(new RuntimeHints());

		BeanFactoryInitializationCode initializationCode = mock(BeanFactoryInitializationCode.class);
		aotContribution.applyTo(generationContext, initializationCode);

		// valid hints bean exist?
		List<TypeHint> typeHints = generationContext.getRuntimeHints().reflection().typeHints().toList();
		assertThat(typeHints).isNotNull().hasSize(1);

		TypeReference type = typeHints.get(0).getType();
		assertThat(type).matches(t -> t.getName().equals(AnnotatedBean.class.getName()))
			.doesNotMatch(t -> t.getName().equals(PlainBean.class.getName()));
	}

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	@interface MyAnnotation {

	}

	/**
	 * test bean
	 */
	static class AnnotatedBean {

		@MyAnnotation
		public void doSomething() {
		}

	}

	static class PlainBean {

		public void nothing() {
		}

	}

}
