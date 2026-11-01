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

package org.springframework.ai.vectorstore.qdrant;

import java.util.List;

import io.qdrant.client.grpc.Common.Condition;
import io.qdrant.client.grpc.Common.FieldCondition;
import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.filter.Filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link QdrantFilterExpressionConverter}.
 */
class QdrantFilterExpressionConverterTests {

	private final QdrantFilterExpressionConverter converter = new QdrantFilterExpressionConverter();

	@Test
	void eqWithFractionalNumberBuildsInclusiveRange() {
		Condition condition = this.converter.buildEqCondition(new Filter.Key("price"), new Filter.Value(10.5));

		assertThat(condition.getConditionOneOfCase()).isEqualTo(Condition.ConditionOneOfCase.FIELD);
		FieldCondition field = condition.getField();
		assertThat(field.getKey()).isEqualTo("price");
		assertThat(field.hasRange()).isTrue();
		assertThat(field.getRange().getGte()).isEqualTo(10.5);
		assertThat(field.getRange().getLte()).isEqualTo(10.5);
	}

	@Test
	void eqWithIntegralNumberBuildsIntegerMatch() {
		Condition condition = this.converter.buildEqCondition(new Filter.Key("year"), new Filter.Value(2024));

		assertThat(condition.getConditionOneOfCase()).isEqualTo(Condition.ConditionOneOfCase.FIELD);
		FieldCondition field = condition.getField();
		assertThat(field.hasMatch()).isTrue();
		assertThat(field.getMatch().hasInteger()).isTrue();
		assertThat(field.getMatch().getInteger()).isEqualTo(2024L);
	}

	@Test
	void eqWithWholeDoubleDoesNotUseStringParsing() {
		Condition condition = this.converter.buildEqCondition(new Filter.Key("rating"), new Filter.Value(5.0));

		assertThat(condition.getConditionOneOfCase()).isEqualTo(Condition.ConditionOneOfCase.FIELD);
		FieldCondition field = condition.getField();
		assertThat(field.hasRange()).isTrue();
		assertThat(field.getRange().getGte()).isEqualTo(5.0);
		assertThat(field.getRange().getLte()).isEqualTo(5.0);
	}

	@Test
	void eqWithBooleanBuildsBooleanMatch() {
		Condition condition = this.converter.buildEqCondition(new Filter.Key("available"), new Filter.Value(true));

		assertThat(condition.getConditionOneOfCase()).isEqualTo(Condition.ConditionOneOfCase.FIELD);
		FieldCondition field = condition.getField();
		assertThat(field.hasMatch()).isTrue();
		assertThat(field.getMatch().getBoolean()).isTrue();
	}

	@Test
	void neWithFractionalNumberBuildsNegatedInclusiveRange() {
		Condition condition = this.converter.buildNeCondition(new Filter.Key("price"), new Filter.Value(10.5));

		assertThat(condition.getConditionOneOfCase()).isEqualTo(Condition.ConditionOneOfCase.FILTER);
		assertThat(condition.getFilter().getMustNotCount()).isEqualTo(1);
		FieldCondition negated = condition.getFilter().getMustNot(0).getField();
		assertThat(negated.getKey()).isEqualTo("price");
		assertThat(negated.hasRange()).isTrue();
		assertThat(negated.getRange().getGte()).isEqualTo(10.5);
		assertThat(negated.getRange().getLte()).isEqualTo(10.5);
	}

	@Test
	void inWithIntegralNumbersBuildsIntegersMatch() {
		Condition condition = this.converter.buildInCondition(new Filter.Key("year"),
				new Filter.Value(List.of(2023, 2024)));

		FieldCondition field = condition.getField();
		assertThat(field.hasMatch()).isTrue();
		assertThat(field.getMatch().hasIntegers()).isTrue();
		assertThat(field.getMatch().getIntegers().getIntegersList()).containsExactly(2023L, 2024L);
	}

	@Test
	void inWithFractionalNumberIsRejectedWithClearError() {
		assertThatThrownBy(
				() -> this.converter.buildInCondition(new Filter.Key("price"), new Filter.Value(List.of(10.0, 10.5))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("integral Number values");
	}

	@Test
	void ninWithIntegralNumbersBuildsIntegersExcept() {
		Condition condition = this.converter.buildNInCondition(new Filter.Key("year"),
				new Filter.Value(List.of(2023, 2024)));

		FieldCondition field = condition.getField();
		assertThat(field.hasMatch()).isTrue();
		assertThat(field.getMatch().hasExceptIntegers()).isTrue();
		assertThat(field.getMatch().getExceptIntegers().getIntegersList()).containsExactly(2023L, 2024L);
	}

}
