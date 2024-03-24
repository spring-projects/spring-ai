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
package org.springframework.ai.ollama.api;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class OllamaModelOptionsTests {

	@Test
	public void testOptions() {
		var options = OllamaOptions.create().withTemperature(3.14f).withTopK(30).withStop(List.of("a", "b", "c"));

		var optionsMap = options.toMap();
		System.out.println(optionsMap);
		assertThat(optionsMap).containsEntry("temperature", 3.14);
		assertThat(optionsMap).containsEntry("top_k", 30);
		assertThat(optionsMap).containsEntry("stop", List.of("a", "b", "c"));
	}

}
