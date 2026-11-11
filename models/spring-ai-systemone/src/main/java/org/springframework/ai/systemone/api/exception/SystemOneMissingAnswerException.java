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

import java.util.Collection;
import java.util.List;

/**
 * Raised when a response is asked for an answer whose question name it does not carry.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public class SystemOneMissingAnswerException extends SystemOneException {

	private final String name;

	private final List<String> availableNames;

	public SystemOneMissingAnswerException(String name, Collection<String> availableNames) {
		super("No answer named '%s' in the response. Available answers: %s".formatted(name, availableNames));
		this.name = name;
		this.availableNames = List.copyOf(availableNames);
	}

	/**
	 * @return the requested question name
	 */
	public String name() {
		return this.name;
	}

	/**
	 * @return the names the response does carry
	 */
	public List<String> availableNames() {
		return this.availableNames;
	}

}
