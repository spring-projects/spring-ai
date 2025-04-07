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

package org.springframework.ai.aot;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.lang.Nullable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.reflection;

/**
 * Unit tests for {@link ToolBeanRegistrationAotProcessor}.
 *
 * @author Thomas Vitale
 */
class ToolBeanRegistrationAotProcessorTests {

	private final GenerationContext generationContext = mock();

	private final RuntimeHints runtimeHints = new RuntimeHints();

	@Test
	void shouldSkipNonAnnotatedClass() {
		process(NonTools.class);
		assertThat(this.runtimeHints.reflection().typeHints()).isEmpty();
	}

	@Test
	void shouldProcessAnnotatedClass() {
		process(TestTools.class);
		assertThat(reflection().onType(TestTools.class)).accepts(this.runtimeHints);
	}

	private void process(Class<?> beanClass) {
		when(generationContext.getRuntimeHints()).thenReturn(runtimeHints);
		BeanRegistrationAotContribution contribution = createContribution(beanClass);
		if (contribution != null) {
			contribution.applyTo(this.generationContext, mock());
		}
	}

	private static @Nullable BeanRegistrationAotContribution createContribution(Class<?> beanClass) {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerBeanDefinition(beanClass.getName(), new RootBeanDefinition(beanClass));
		return new ToolBeanRegistrationAotProcessor()
			.processAheadOfTime(RegisteredBean.of(beanFactory, beanClass.getName()));
	}

	static class TestTools {

		@Tool
		String testTool() {
			return "Testing";
		}

	}

	static class NonTools {

		String nonTool() {
			return "More testing";
		}

	}

}
