/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.ollama.api;

import org.junit.jupiter.api.Test;

import org.springframework.ai.model.ModelOptionsUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class OllamaDurationFieldsTests {

	@Test
	public void testDurationFields() {

		var value = ModelOptionsUtils.jsonToObject("""
				{
					"model": "llama3.2",
					"created_at": "2023-08-04T19:22:45.499127Z",
					"response": "",
					"done": true,
					"total_duration": 10706818083,
					"load_duration": 6338219291,
					"prompt_eval_count": 26,
					"prompt_eval_duration": 130079000,
					"eval_count": 259,
					"eval_duration": 4232710000
				}
				""", OllamaApi.ChatResponse.class);

		assertThat(value.getTotalDuration().toNanos()).isEqualTo(10706818083L);
		assertThat(value.getLoadDuration().toNanos()).isEqualTo(6338219291L);
		assertThat(value.getEvalDuration().toNanos()).isEqualTo(4232710000L);
		assertThat(value.getPromptEvalDuration().toNanos()).isEqualTo(130079000L);
	}

}
