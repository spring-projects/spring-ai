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

package org.springframework.ai.mcp.modern.client;

import java.util.Map;

import io.modelcontextprotocol.modern.McpSchema.MetaKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.ToolContextToMcpMetaConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ModernToolCalls}.
 */
@ExtendWith(MockitoExtension.class)
class ModernToolCallsTests {

	@Mock
	private ToolContextToMcpMetaConverter converter;

	@Test
	void requestMetaPassesTheToolNameToTheConverter() {
		ToolContext toolContext = new ToolContext(Map.of("key", "value"));
		when(this.converter.convert(toolContext, "getWeather")).thenReturn(Map.of(MetaKeys.PROGRESS_TOKEN, "token",
				"io.modelcontextprotocol/custom", "reserved", "custom", "value"));

		Map<String, Object> meta = ModernToolCalls.requestMeta(this.converter, toolContext, "getWeather");

		assertThat(meta).hasSize(1).containsEntry("custom", "value");
	}

	@Test
	void requestMetaIsEmptyWithoutAToolContext() {
		assertThat(ModernToolCalls.requestMeta(this.converter, null, "getWeather")).isEmpty();
	}

}
