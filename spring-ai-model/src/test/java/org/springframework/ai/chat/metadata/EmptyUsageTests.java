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

package org.springframework.ai.chat.metadata;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EmptyUsage}.
 *
 * @author Christian Tzolov
 */
class EmptyUsageTests {

	@Test
	void reportsZeroTokens() {
		var usage = new EmptyUsage();

		assertThat(usage.getPromptTokens()).isZero();
		assertThat(usage.getCompletionTokens()).isZero();
		assertThat(usage.getTotalTokens()).isZero();
	}

	@Test
	void allInstancesAreEqual() {
		var usage = new EmptyUsage();

		assertThat(usage).isEqualTo(new EmptyUsage()).hasSameHashCodeAs(new EmptyUsage());
		assertThat(usage).isNotEqualTo(new DefaultUsage(0, 0));
		assertThat(usage).isNotEqualTo(new EmptyUsage() {
		});
		assertThat(usage).isNotEqualTo(null);
	}

}
