/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.core.prompts;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTests {

	@Test
	void testSingleInputVariable() {
		String template = "This is a {foo} test";
		PromptTemplate promptTemplate = new PromptTemplate(template);
		Set<String> inputVariables = promptTemplate.getInputVariables();
		assertThat(inputVariables).isNotEmpty();
		assertThat(inputVariables).hasSize(1);
		assertThat(inputVariables).contains("foo");
	}

	@Test
	void testMultipleInputVariables() {
		String template = "This {bar} is a {foo} test";
		PromptTemplate promptTemplate = new PromptTemplate(template);
		Set<String> inputVariables = promptTemplate.getInputVariables();
		assertThat(inputVariables).isNotEmpty();
		assertThat(inputVariables).hasSize(2);
		assertThat(inputVariables).contains("foo", "bar");
	}

	@Test
	void testMultipleInputVariablesWithRepeats() {
		String template = "This {bar} is a {foo} test {foo}.";
		PromptTemplate promptTemplate = new PromptTemplate(template);
		Set<String> inputVariables = promptTemplate.getInputVariables();
		assertThat(inputVariables).isNotEmpty();
		assertThat(inputVariables).hasSize(2);
		assertThat(inputVariables).contains("foo", "bar");
	}

	@Test
	void testBadTemplateString() {
		String template = "This is a {foo test";
		Assertions.assertThatThrownBy(() -> {
			PromptTemplate promptTemplate = new PromptTemplate(template);
			promptTemplate.validate();
		}).isInstanceOf(IllegalArgumentException.class).hasMessage("The template string is not valid.");
	}

}
