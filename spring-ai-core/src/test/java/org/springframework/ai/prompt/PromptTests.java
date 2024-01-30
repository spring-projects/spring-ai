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

package org.springframework.ai.prompt;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
class PromptTests {

	@Test
	void newApiPlaygroundTests() {
		// Create a String, a PromptValue or Messages
		String templateText = "Hello '{firstName}' '{lastName}' from Unix";
		PromptTemplate pt = new PromptTemplate(templateText);

		final Map<String, Object> model = new HashMap<>();
		model.put("firstName", "Nick");

		// Try to render with missing value for template variable, expect exception
		Assertions.assertThatThrownBy(() -> {
			String promptString = pt.render(model);
		})
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("All template variables were not replaced. Missing variable names are [lastName]");

		pt.add("lastName", "Park"); // TODO investigate partial
		String promptString = pt.render(model);
		assertThat(promptString).isEqualTo("Hello 'Nick' 'Park' from Unix");

		promptString = pt.render(model); // render again
		assertThat(promptString).isEqualTo("Hello 'Nick' 'Park' from Unix");

		// to have access to Messages
		Prompt prompt = pt.create(model);
		assertThat(prompt.getContents()).isNotNull();
		assertThat(prompt.getInstructions()).isNotEmpty().hasSize(1);
		System.out.println(prompt.getContents());

		String systemTemplate = "You are a helpful assistant that translates {input_language} to {output_language}.";
		// system_message_prompt = SystemMessagePromptTemplate.from_template(template)

		Map<String, Object> systemModel = new HashMap();
		systemModel.put("input_language", "English");
		systemModel.put("output_language", "French");

		String humanTemplate = "{text}";
		Map<String, Object> humanModel = new HashMap();
		humanModel.put("text", "I love programming");
		// human_message_prompt = HumanMessagePromptTemplate.from_template(human_template)

		/*
		 * chat_prompt = ChatPromptTemplate.from_messages([system_message_prompt,
		 * human_message_prompt])
		 *
		 * # get a chat completion from the formatted messages
		 * chat_prompt.format_prompt(input_language="English", output_language="French",
		 * text="I love programming.").to_messages()
		 */
		PromptTemplate promptTemplate = new SystemPromptTemplate(systemTemplate);
		Prompt systemPrompt = promptTemplate.create(systemModel);

		promptTemplate = new PromptTemplate(humanTemplate); // creates a Prompt with
															// HumanMessage
		Prompt humanPrompt = promptTemplate.create(humanModel);

		// ChatPromptTemplate chatPromptTemplate = new ChatPromptTemplate(systemPrompt,
		// humanPrompt);
		// Prompt chatPrompt chatPromptTemplate.create(generative);

	}

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
	void testBadFormatOfTemplateString() {
		String template = "This is a {foo test";
		Assertions.assertThatThrownBy(() -> {
			new PromptTemplate(template);
		}).isInstanceOf(IllegalArgumentException.class).hasMessage("The template string is not valid.");
	}

}
