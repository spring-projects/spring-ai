/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * Unit tests for {@link Query}.
 *
 * @author Thomas Vitale
 */
class QueryTests {

	@Test
	void whenTextIsNullThenThrow() {
		assertThatThrownBy(() -> new Query(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("text cannot be null or empty");
	}

	@Test
	void whenTextIsEmptyThenThrow() {
		assertThatThrownBy(() -> new Query("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("text cannot be null or empty");
	}

	@Test
	void whenTextIsBlankThenThrow() {
		assertThatThrownBy(() -> new Query("   ")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("text cannot be null or empty");
	}

	@Test
	void whenTextIsTabsAndSpacesThenThrow() {
		assertThatThrownBy(() -> new Query("\t\n  \r")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("text cannot be null or empty");
	}

	@Test
	void whenMultipleQueriesWithSameTextThenEqual() {
		String text = "Same query text";
		Query query1 = new Query(text);
		Query query2 = new Query(text);

		assertThat(query1).isEqualTo(query2);
		assertThat(query1.hashCode()).isEqualTo(query2.hashCode());
	}

	@Test
	void whenQueriesWithDifferentTextThenNotEqual() {
		Query query1 = new Query("First query");
		Query query2 = new Query("Second query");

		assertThat(query1).isNotEqualTo(query2);
		assertThat(query1.hashCode()).isNotEqualTo(query2.hashCode());
	}

	@Test
	void whenCompareQueryToNullThenNotEqual() {
		Query query = new Query("Test query");

		assertThat(query).isNotEqualTo(null);
	}

}
