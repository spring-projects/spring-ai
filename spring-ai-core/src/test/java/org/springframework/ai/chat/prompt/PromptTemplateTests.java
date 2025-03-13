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

package org.springframework.ai.chat.prompt;

import org.junit.jupiter.api.Test;

/**
 * Unit Tests for {@link PromptTemplateTests}.
 *
 * @author Sun Yuhan
 */
public class PromptTemplateTests {
	@Test
	void buildPromptTemplateWithBuiltInFunctions() {
		String chatMemoryPrompt = """
                {if(strlen(memory))}

                Hello World!

                ---------------------
                {memory}
                ---------------------
                {endif}
                """;

		PromptTemplate promptTemplate = new PromptTemplate(chatMemoryPrompt);
		promptTemplate.add("memory", "you are a helpful assistant");
		System.out.println(promptTemplate.render());
	}

	@Test
	void buildPromptTemplateSkipRenderValidate() {
		String chatMemoryPrompt = """
                {if(strlen(memory))}

                Hello World!

                ---------------------
                {memory}
                ---------------------
                {endif}
                """;

		PromptTemplate promptTemplate = new PromptTemplate(chatMemoryPrompt);
		promptTemplate.add("memory", "you are a helpful assistant");
		promptTemplate.skipValidate();
		System.out.println(promptTemplate.render());
	}
}
