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

package org.springframework.ai.systemone.api.exception;

import org.springframework.ai.systemone.api.response.AnswerType;

/**
 * Raised when an answer is read as the wrong primitive, for example calling
 * {@code score(..)} on a question that was asked as a noul.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public class SystemOneAnswerTypeException extends SystemOneException {

	private final String name;

	private final AnswerType expected;

	private final AnswerType actual;

	public SystemOneAnswerTypeException(String name, AnswerType expected, AnswerType actual) {
		super("Answer '%s' is a %s answer, not a %s answer".formatted(name, actual.value(), expected.value()));
		this.name = name;
		this.expected = expected;
		this.actual = actual;
	}

	public String name() {
		return this.name;
	}

	public AnswerType expected() {
		return this.expected;
	}

	public AnswerType actual() {
		return this.actual;
	}

}
