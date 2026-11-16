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

package org.springframework.ai.systemone.api.response;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The discriminator values an answer can carry. {@link #UNKNOWN} is not a wire value; it
 * marks an answer kind this client version does not model yet.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public enum AnswerType {

	NOUL("noul"), CHOICE("choice"), SCORE("score"), UNKNOWN("unknown");

	private final String value;

	AnswerType(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return this.value;
	}

}
