/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.vectorstore;

/**
 * @author Christian Tzolov
 */
public class CriteriaBuilder {

	String key;

	String op;

	Object value;

	public static class CompExpr {

		final CriteriaBuilder criteria;

		public CompExpr(CriteriaBuilder criteria) {
			this.criteria = criteria;
		}

		public JoinOp eq(Object value) {
			criteria.op = "eq";
			criteria.value = value;
			return new JoinOp();
		}

		public JoinOp ne(Object value) {
			criteria.op = "ne";
			criteria.value = value;

			return new JoinOp();
		}

		public JoinOp gt(Object value) {

			criteria.op = "gt";
			criteria.value = value;

			return new JoinOp();
		}

		public JoinOp gte(Object value) {
			criteria.op = "gte";
			criteria.value = value;

			return new JoinOp();
		}

		public JoinOp lt(Object value) {
			criteria.op = "lt";
			criteria.value = value;

			return new JoinOp();
		}

		public JoinOp lte(Object value) {
			criteria.op = "lte";
			criteria.value = value;

			return new JoinOp();
		}

		public JoinOp in(Object... values) {
			criteria.op = "in";
			criteria.value = values;

			return new JoinOp();
		}

		public JoinOp nin(Object... values) {
			criteria.op = "nin";
			criteria.value = values;

			return new JoinOp();
		}

	}

	public static class Builder {

		public CompExpr key(String key) {
			var criteria = new CriteriaBuilder();
			criteria.key = key;
			return new CompExpr(criteria);
		}

	}

	public static class JoinOp {

		public Builder and() {
			return new Builder();
		}

		public Builder and(CriteriaBuilder criteriaBuilder) {
			// Grouping
			return new Builder();
		}

		public Builder or() {
			return new Builder();
		}

		public CriteriaBuilder build() {
			return null;
		}

	}

	public static CompExpr key(String key) {
		return new Builder().key(key);
	};

	public static void main(String[] args) {
		CriteriaBuilder.key("country")
			.eq("NL")
			.and()
			.key("year")
			.gt(2019)
			.or()
			.key("boza")
			.in(1, 2, 3)
			.and(CriteriaBuilder.key("null").eq("args").build());
		// .build();
	}

}
