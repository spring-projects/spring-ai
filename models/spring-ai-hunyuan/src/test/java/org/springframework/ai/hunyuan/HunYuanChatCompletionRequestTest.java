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

package org.springframework.ai.hunyuan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.hunyuan.api.HunYuanApi;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Guo Junyu
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "HUNYUAN_SECRET_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "HUNYUAN_SECRET_KEY", matches = ".+")
public class HunYuanChatCompletionRequestTest {

	HunYuanChatModel chatModel = new HunYuanChatModel(
			new HunYuanApi(System.getenv("HUNYUAN_SECRET_ID"), System.getenv("HUNYUAN_SECRET_KEY")));

	@Test
	void chatCompletionDefaultRequestTest() {
		var request = this.chatModel.createRequest(new Prompt("test content"), false);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isFalse();
	}

	@Test
	void chatCompletionRequestWithOptionsTest() {
		var options = HunYuanChatOptions.builder().temperature(0.5).topP(0.8).build();
		var request = this.chatModel.createRequest(new Prompt("test content", options), true);

		assertThat(request.messages().size()).isEqualTo(1);
		assertThat(request.topP()).isEqualTo(0.8);
		assertThat(request.temperature()).isEqualTo(0.5);
		assertThat(request.stream()).isTrue();
	}

}
