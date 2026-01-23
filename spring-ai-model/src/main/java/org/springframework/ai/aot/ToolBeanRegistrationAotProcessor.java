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

import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.aot.BeanRegistrationCode;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.util.ReflectionUtils;

/**
 * AOT {@code BeanRegistrationAotProcessor} that detects the presence of the {@link Tool}
 * annotation on methods and creates the required reflection hints.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
class ToolBeanRegistrationAotProcessor implements BeanRegistrationAotProcessor {

	@Override
	public @Nullable BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
		Class<?> beanClass = registeredBean.getBeanClass();
		MergedAnnotations.Search search = MergedAnnotations
			.search(org.springframework.core.annotation.MergedAnnotations.SearchStrategy.TYPE_HIERARCHY);

		boolean hasAnyToolAnnotatedMethods = Stream.of(ReflectionUtils.getDeclaredMethods(beanClass))
			.anyMatch(method -> search.from(method).isPresent(Tool.class));

		if (hasAnyToolAnnotatedMethods) {
			return new AotContribution(beanClass);
		}

		return null;
	}

	private static class AotContribution implements BeanRegistrationAotContribution {

		private final MemberCategory[] memberCategories = new MemberCategory[] { MemberCategory.INVOKE_DECLARED_METHODS,
				MemberCategory.INVOKE_PUBLIC_METHODS };

		private final Class<?> toolClass;

		AotContribution(Class<?> toolClass) {
			this.toolClass = toolClass;
		}

		@Override
		public void applyTo(GenerationContext generationContext, BeanRegistrationCode beanRegistrationCode) {
			ReflectionHints reflectionHints = generationContext.getRuntimeHints().reflection();
			reflectionHints.registerType(this.toolClass, this.memberCategories);
		}

	}

}
