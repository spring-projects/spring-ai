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
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.context.DefaultMetaProvider;
import org.springframework.ai.mcp.annotation.context.MetaProvider;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.MemberCategory;
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
 * @author Nikita Kibitkin
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

	@Test
	void processAheadOfTimeRegistersMetaProviderConstructors() {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerBeanDefinition(McpAnnotatedBean.class.getName(),
				new RootBeanDefinition(McpAnnotatedBean.class));

		Set<Class<? extends Annotation>> annotations = Set.of(McpTool.class, McpPrompt.class, McpResource.class);
		AbstractAnnotatedMethodBeanFactoryInitializationAotProcessor processor = new AbstractAnnotatedMethodBeanFactoryInitializationAotProcessor(
				annotations);

		BeanFactoryInitializationAotContribution aotContribution = processor.processAheadOfTime(beanFactory);
		assertThat(aotContribution).isNotNull();

		GenerationContext generationContext = mock(GenerationContext.class);
		when(generationContext.getRuntimeHints()).thenReturn(new RuntimeHints());

		BeanFactoryInitializationCode initializationCode = mock(BeanFactoryInitializationCode.class);
		aotContribution.applyTo(generationContext, initializationCode);

		List<TypeHint> typeHints = generationContext.getRuntimeHints().reflection().typeHints().toList();
		assertHasDeclaredConstructorHint(typeHints, DefaultMetaProvider.class);
		assertHasDeclaredConstructorHint(typeHints, PromptMetaProvider.class);
		assertHasDeclaredConstructorHint(typeHints, ResourceMetaProvider.class);
	}

	private static void assertHasDeclaredConstructorHint(List<TypeHint> typeHints, Class<?> type) {
		assertThat(typeHints).anySatisfy(typeHint -> {
			assertThat(typeHint.getType()).isEqualTo(TypeReference.of(type));
			assertThat(typeHint.getMemberCategories()).contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
		});
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

	static class McpAnnotatedBean {

		@McpTool(name = "default-tool")
		public String defaultTool() {
			return "default";
		}

		@McpPrompt(name = "custom-prompt", metaProvider = PromptMetaProvider.class)
		public String customPrompt() {
			return "prompt";
		}

		@McpResource(uri = "custom://resource", metaProvider = ResourceMetaProvider.class)
		public String customResource() {
			return "resource";
		}

	}

	static class PromptMetaProvider implements MetaProvider {

		@Override
		public Map<String, Object> getMeta() {
			return Map.of("type", "prompt");
		}

	}

	static class ResourceMetaProvider implements MetaProvider {

		@Override
		public Map<String, Object> getMeta() {
			return Map.of("type", "resource");
		}

	}

	static class PlainBean {

		public void nothing() {
		}

	}

}
