/*
 * Copyright 2024-2024 the original author or authors.
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
package org.springframework.ai.autoconfigure.common.function;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.ai.model.AbstractToolFunctionCallback;
import org.springframework.ai.model.ToolFunctionCallback;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Manages the chat functions that are annotated with {@link SpringAiFunction}.
 *
 * @author Christopher Smith
 * @author Christian Tzolov
 */
public class SpringAiFunctionAnnotationManager implements ApplicationContextAware {

	private GenericApplicationContext applicationContext;

	@Override
	public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = (GenericApplicationContext) applicationContext;
	}

	/**
	 * @return a list of all the {@link java.util.Function}s annotated with
	 * {@link SpringAiFunction}.
	 */
	public List<ToolFunctionCallback> getAnnotatedToolFunctionCallbacks() {
		Map<String, Object> beans = this.applicationContext.getBeansWithAnnotation(SpringAiFunction.class);

		List<ToolFunctionCallback> toolFunctionCallbacks = new ArrayList<>();

		if (!CollectionUtils.isEmpty(beans)) {

			beans.forEach((k, v) -> {
				if (v instanceof Function<?, ?> function) {
					SpringAiFunction functionAnnotation = applicationContext.findAnnotationOnBean(k,
							SpringAiFunction.class);

					toolFunctionCallbacks.add(new SpringAiFunctionToolFunctionCallback(functionAnnotation.name(),
							functionAnnotation.description(), functionAnnotation.classType(), function));
				}
				else {
					ReflectionUtils.handleReflectionException(new IllegalArgumentException(
							"Bean annotated with @SpringAiFunction must be of type Function"));
				}
			});

		}

		return toolFunctionCallbacks;
	}

	/**
	 * Note that the underlying function is responsible for converting the output into
	 * format that can be consumed by the Model. The default implementation converts the
	 * output into String before sending it to the Model. Provide a custom Function<O,
	 * String> responseConverter implementation to override this.
	 *
	 */
	public static class SpringAiFunctionToolFunctionCallback<I, O> extends AbstractToolFunctionCallback<I, O> {

		private Function<I, O> function;

		protected SpringAiFunctionToolFunctionCallback(String name, String description, Class<I> inputType,
				Function<I, O> function) {
			super(name, description, inputType);
			Assert.notNull(function, "Function must not be null");
			this.function = function;
		}

		protected SpringAiFunctionToolFunctionCallback(String name, String description, Class<I> inputType,
				Function<O, String> responseConverter, Function<I, O> function) {
			super(name, description, inputType, responseConverter);
			Assert.notNull(function, "Function must not be null");
			this.function = function;
		}

		@Override
		public O apply(I input) {
			return this.function.apply(input);
		}

	}

}
