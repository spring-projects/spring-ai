/*
 * Copyright 2026-2026 the original author or authors.
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

package org.springframework.ai.test.options;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.ChatOptions.Builder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for testing {@link ChatOptions} subclasses.
 *
 * @param <O> the concrete type of ChatOptions
 * @param <B> the type of the builder for O
 */
public abstract class AbstractChatOptionsTests<O extends ChatOptions, B extends ChatOptions.Builder<B>> {

	@Test
	public void builderShouldReturnNewInstances() {
		ChatOptions.Builder<?> builder = readyToBuildBuilder();
		Object o1 = builder.build();
		assertThat(o1.getClass()).isEqualTo(getConcreteOptionsClass());
		Object o2 = builder.build();
		assertThat(o2.getClass()).isEqualTo(getConcreteOptionsClass());

		assertThat(o1).isEqualTo(o2);
		assertThat(o1).isNotSameAs(o2);
	}

	@Test
	public void testMutateBehavior() {
		ChatOptions.Builder<?> builder = readyToBuildBuilder();
		ChatOptions options1 = builder.build();
		ChatOptions.Builder<?> builder2 = options1.mutate();
		ChatOptions options2 = builder2.build();
		ChatOptions.Builder<?> builder3 = options1.mutate();

		// mutate returns the correct type of builder
		assertThat(builder).hasSameClassAs(builder2);
		assertThat(builder2).isNotSameAs(builder);

		assertThat(options1).isNotSameAs(options2);
		assertThat(options1).isEqualTo(options2);
		assertThat(options1).hasSameClassAs(options2);

		// mutate returns a new builder each time
		assertThat(builder2).isNotSameAs(builder3);
	}

	/**
	 * Return the concrete options class being tested.
	 */
	protected abstract Class<O> getConcreteOptionsClass();

	/**
	 * Return an instance of a builder that should not error when calling
	 * {@link Builder#build()}. This may mean setting some required fields, depending on
	 * the semantics of the particular options class.
	 *
	 * This convenience method helps reduce repetitive boilerplate code used in each and
	 * every test.
	 */
	protected abstract B readyToBuildBuilder();

}
