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

package org.springframework.ai.decision;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DecisionOptions} and {@link DefaultDecisionOptions}.
 *
 * @author Thomas Vitale
 */
class DecisionOptionsTests {

	@Test
	void buildsImmutableOptions() {
		var options = DecisionOptions.builder().model("jev-1").build();

		assertThat(options).isInstanceOf(DefaultDecisionOptions.class);
		assertThat(options.getModel()).isEqualTo("jev-1");
		assertThat(options).isEqualTo(DecisionOptions.builder().model("jev-1").build());
	}

	@Test
	void mutateStartsFromTheOptions() {
		var options = DecisionOptions.builder().model("jev-1").build();

		var mutated = options.mutate().model("jev-2").build();

		assertThat(mutated.getModel()).isEqualTo("jev-2");
		assertThat(options.getModel()).isEqualTo("jev-1");
	}

	@Test
	void combineWithOverridesOnlyTheValuesSet() {
		var defaults = DecisionOptions.builder().model("jev-1");

		assertThat(defaults.clone().combineWith(DecisionOptions.builder()).build().getModel()).isEqualTo("jev-1");
		assertThat(defaults.clone().combineWith(DecisionOptions.builder().model("jev-2")).build().getModel())
			.isEqualTo("jev-2");
		assertThat(defaults.build().getModel()).isEqualTo("jev-1");
	}

	@Test
	void cloneIsIndependent() {
		var builder = DecisionOptions.builder().model("jev-1");
		var copy = builder.clone().model("jev-2");

		assertThat(builder.build().getModel()).isEqualTo("jev-1");
		assertThat(copy.build().getModel()).isEqualTo("jev-2");
	}

}
