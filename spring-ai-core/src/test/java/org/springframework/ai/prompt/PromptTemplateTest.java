/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.prompt;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PromptTemplateTest {

	@Test
	public void testRenderWithList() {
		String templateString = "The items are:\n{items:{item | - {item}\n}}";
		List<String> itemList = Arrays.asList("apple", "banana", "cherry");
		PromptTemplate promptTemplate = new PromptTemplate(templateString);
		Message message = promptTemplate.createMessage(Map.of("items", itemList));

		String expected = "The items are:\n- apple\n- banana\n- cherry\n";

		// After upgrading StringTemplate4 to 4.3.4, this test will fail on windows if we
		// don't normalize EOLs.
		// It should be fine on Unix systems. In addition, Git will replace CRLF by LF by
		// default.
		assertEqualsWithNormalizedEOLs(expected, message.getContent());

		PromptTemplate unfilledPromptTemplate = new PromptTemplate(templateString);
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(unfilledPromptTemplate::render)
			.withMessage("Not all template variables were replaced. Missing variable names are [items]");
	}

	@Test
	public void testRender() {
		Map<String, Object> model = createTestMap();
		model.put("key3", 100);

		// Create a simple template with placeholders for keys in the generative
		String template = "This is a {key1}, it is {key2}, and it costs {key3}";
		PromptTemplate promptTemplate = new PromptTemplate(template, model);

		// The expected result after rendering the template with the generative
		String expected = "This is a value1, it is true, and it costs 100";
		String result = promptTemplate.render();

		// Check that the rendered string matches the expected result
		assertEquals(expected, result);

		model.put("key3", 200);
		expected = "This is a value1, it is true, and it costs 200";
		result = promptTemplate.render(model);
		assertEquals(expected, result);
	}

	@Test
	public void testRenderWithHyphen() {
		Map<String, Object> model = Map.of("key-1", "value1");
		String template = "This is a {key-1}";
		PromptTemplate promptTemplate = new PromptTemplate(template, model);

		String expected = "This is a value1";
		String result = promptTemplate.render();

		assertEquals(expected, result);
	}

	@Test
	public void testRenderResource() {
		Map<String, Object> model = createTestMap();
		InputStream inputStream = new ByteArrayInputStream(
				"key1's value is {key1} and key2's value is {key2}".getBytes(Charset.defaultCharset()));
		Resource resource = new InputStreamResource(inputStream);
		PromptTemplate promptTemplate = new PromptTemplate(resource, model);
		String expected = "key1's value is value1 and key2's value is true";
		String result = promptTemplate.render();
		assertEquals(expected, result);
	}

	private static Map<String, Object> createTestMap() {
		Map<String, Object> model = new HashMap<>();
		model.put("key1", "value1");
		model.put("key2", true);
		return model;
	}

	@Disabled("Need to improve PromptTemplate to better handle Resource toString and tracking with 'dynamicModel' for underlying StringTemplate")
	@Test
	public void testRenderResourceAsValue() throws Exception {
		Map<String, Object> model = createTestMap();

		// Create an input stream for the resource
		InputStream inputStream = new ByteArrayInputStream("it costs 100".getBytes(Charset.defaultCharset()));
		Resource resource = new InputStreamResource(inputStream);

		model.put("key3", resource);

		// Create a simple template with placeholders for keys in the generative
		String template = "{key1}, {key2}, {key3}";
		PromptTemplate promptTemplate = new PromptTemplate(template, model);

		// The expected result after rendering the template with the generative
		String expected = "value1, true, it costs 100";
		String result = promptTemplate.render();

		// Check that the rendered string matches the expected result
		assertEquals(expected, result);
	}

	@Test
	public void testRenderFailure() {
		// Create a map with string keys and object values to serve as a generative for
		// testing
		Map<String, Object> model = new HashMap<>();
		model.put("key1", "value1");

		// Create a simple template that includes a key not present in the generative
		String template = "This is a {key2}!";
		PromptTemplate promptTemplate = new PromptTemplate(template, model);

		// Rendering the template with a missing key should throw an exception
		assertThrows(IllegalStateException.class, promptTemplate::render);
	}

	private static void assertEqualsWithNormalizedEOLs(String expected, String actual) {
		assertEquals(expected.replaceAll("\\r\\n|\\r|\\n", System.lineSeparator()),
				actual.replaceAll("\\r\\n|\\r|\\n", System.lineSeparator()));
	}

}