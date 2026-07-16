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

package org.springframework.ai.deepseek;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.deepseek.DeepSeekChatOptions.Builder;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.test.options.AbstractChatOptionsTests;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DeepSeekChatOptions}.
 *
 * @author Geng Rong
 */
class DeepSeekChatOptionsTests extends AbstractChatOptionsTests<DeepSeekChatOptions, Builder> {

	@Override
	protected Class<DeepSeekChatOptions> getConcreteOptionsClass() {
		return DeepSeekChatOptions.class;
	}

	@Override
	protected Builder readyToBuildBuilder() {
		return DeepSeekChatOptions.builder().model(DeepSeekApi.DEFAULT_CHAT_MODEL);
	}

	@Test
	void testCombineWithCollections() {
		DeepSeekApi.FunctionTool baseTool = new DeepSeekApi.FunctionTool(DeepSeekApi.FunctionTool.Type.FUNCTION,
				new DeepSeekApi.FunctionTool.Function("base-function", "", "{}"));
		DeepSeekChatOptions base = DeepSeekChatOptions.builder().tools(java.util.List.of(baseTool)).build();

		DeepSeekApi.FunctionTool overrideTool = new DeepSeekApi.FunctionTool(DeepSeekApi.FunctionTool.Type.FUNCTION,
				new DeepSeekApi.FunctionTool.Function("override-function", "", "{}"));
		DeepSeekChatOptions override = DeepSeekChatOptions.builder().tools(java.util.List.of(overrideTool)).build();

		DeepSeekChatOptions merged = base.mutate().combineWith(override.mutate()).build();

		org.assertj.core.api.Assertions.assertThat(merged.getTools()).containsExactlyInAnyOrder(baseTool, overrideTool);
	}

	@Test
	void cloneCreatesIndependentToolsList() {
		DeepSeekApi.FunctionTool tool = new DeepSeekApi.FunctionTool(DeepSeekApi.FunctionTool.Type.FUNCTION,
				new DeepSeekApi.FunctionTool.Function("function", "", "{}"));
		List<DeepSeekApi.FunctionTool> tools = new ArrayList<>();
		tools.add(tool);

		Builder source = DeepSeekChatOptions.builder().tools(tools);
		Builder clone = source.clone();
		tools.add(new DeepSeekApi.FunctionTool(DeepSeekApi.FunctionTool.Type.FUNCTION,
				new DeepSeekApi.FunctionTool.Function("other", "", "{}")));

		assertThat(clone.build().getTools()).containsExactly(tool);
	}

	@Test
	void cloneHandlesNullToolsList() {
		assertThat(DeepSeekChatOptions.builder().clone().build().getTools()).isNull();
	}

}
