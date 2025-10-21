/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.mcp;

import java.util.HashMap;
import java.util.Map;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.model.ToolContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ToolContextToMcpMetaConverter}.
 *
 * @author Christian Tzolov
 * @author YunKui Lu
 */
@ExtendWith(MockitoExtension.class)
class ToolContextToMcpMetaConverterTest {

	@Mock
	private McpSyncServerExchange mockExchange;

	@Test
	void defaultConverterShouldReturnEmptyMapForNullContext() {
		ToolContextToMcpMetaConverter converter = ToolContextToMcpMetaConverter.defaultConverter();

		Map<String, Object> result = converter.convert(null);

		assertThat(result).isEmpty();
	}

	@Test
	void defaultConverterShouldReturnEmptyMapForEmptyContext() {
		ToolContextToMcpMetaConverter converter = ToolContextToMcpMetaConverter.defaultConverter();
		ToolContext toolContext = new ToolContext(new HashMap<>());

		Map<String, Object> result = converter.convert(toolContext);

		assertThat(result).isEmpty();
	}

	@Test
	void defaultConverterShouldReturnEmptyMapForNullContextMap() {
		ToolContextToMcpMetaConverter converter = ToolContextToMcpMetaConverter.defaultConverter();
		// ToolContext doesn't accept null, so we test with an empty map instead
		ToolContext toolContext = new ToolContext(new HashMap<>());

		Map<String, Object> result = converter.convert(toolContext);

		assertThat(result).isEmpty();
	}

	@Test
	void defaultConverterShouldFilterOutMcpExchangeKey() {
		ToolContextToMcpMetaConverter converter = ToolContextToMcpMetaConverter.defaultConverter();
		Map<String, Object> contextMap = new HashMap<>();
		contextMap.put(McpToolUtils.TOOL_CONTEXT_MCP_EXCHANGE_KEY, this.mockExchange);
		contextMap.put("key1", "value1");
		contextMap.put("key2", "value2");
		ToolContext toolContext = new ToolContext(contextMap);

		Map<String, Object> result = converter.convert(toolContext);

		assertThat(result).hasSize(2);
		assertThat(result).containsEntry("key1", "value1");
		assertThat(result).containsEntry("key2", "value2");
		assertThat(result).doesNotContainKey(McpToolUtils.TOOL_CONTEXT_MCP_EXCHANGE_KEY);
	}

	@Test
	void defaultConverterShouldFilterOutNullValues() {
		ToolContextToMcpMetaConverter converter = ToolContextToMcpMetaConverter.defaultConverter();
		Map<String, Object> contextMap = new HashMap<>();
		contextMap.put("key1", "value1");
		contextMap.put("key2", null);
		contextMap.put("key3", "value3");
		contextMap.put("key4", null);
		ToolContext toolContext = new ToolContext(contextMap);

		Map<String, Object> result = converter.convert(toolContext);

		assertThat(result).hasSize(2);
		assertThat(result).containsEntry("key1", "value1");
		assertThat(result).containsEntry("key3", "value3");
		assertThat(result).doesNotContainKeys("key2", "key4");
	}

	@Test
	void defaultConverterShouldHandleComplexObjects() {
		ToolContextToMcpMetaConverter converter = ToolContextToMcpMetaConverter.defaultConverter();
		Map<String, Object> nestedMap = new HashMap<>();
		nestedMap.put("nested1", "nestedValue1");
		nestedMap.put("nested2", 42);

		Map<String, Object> contextMap = new HashMap<>();
		contextMap.put("string", "stringValue");
		contextMap.put("number", 123);
		contextMap.put("boolean", true);
		contextMap.put("map", nestedMap);
		ToolContext toolContext = new ToolContext(contextMap);

		Map<String, Object> result = converter.convert(toolContext);

		assertThat(result).hasSize(4);
		assertThat(result).containsEntry("string", "stringValue");
		assertThat(result).containsEntry("number", 123);
		assertThat(result).containsEntry("boolean", true);
		assertThat(result).containsEntry("map", nestedMap);
	}

	@Test
	void defaultConverterShouldFilterBothExchangeKeyAndNullValues() {
		ToolContextToMcpMetaConverter converter = ToolContextToMcpMetaConverter.defaultConverter();
		Map<String, Object> contextMap = new HashMap<>();
		contextMap.put(McpToolUtils.TOOL_CONTEXT_MCP_EXCHANGE_KEY, this.mockExchange);
		contextMap.put("key1", "value1");
		contextMap.put("key2", null);
		contextMap.put("key3", "value3");
		ToolContext toolContext = new ToolContext(contextMap);

		Map<String, Object> result = converter.convert(toolContext);

		assertThat(result).hasSize(2);
		assertThat(result).containsEntry("key1", "value1");
		assertThat(result).containsEntry("key3", "value3");
		assertThat(result).doesNotContainKey(McpToolUtils.TOOL_CONTEXT_MCP_EXCHANGE_KEY);
		assertThat(result).doesNotContainKey("key2");
	}

	@Test
	void noOpConverterShouldAlwaysReturnEmptyMap() {
		ToolContextToMcpMetaConverter converter = ToolContextToMcpMetaConverter.noOp();

		Map<String, Object> result1 = converter.convert(null);

		assertThat(result1).isEmpty();

		ToolContext emptyContext = new ToolContext(new HashMap<>());
		Map<String, Object> result2 = converter.convert(emptyContext);

		assertThat(result2).isEmpty();

		Map<String, Object> contextMap = new HashMap<>();
		contextMap.put("key1", "value1");
		contextMap.put("key2", "value2");
		ToolContext populatedContext = new ToolContext(contextMap);
		Map<String, Object> result3 = converter.convert(populatedContext);

		assertThat(result3).isEmpty();
	}

	@Test
	void customConverterImplementation() {
		ToolContextToMcpMetaConverter customConverter = toolContext -> {
			if (toolContext == null || toolContext.getContext() == null) {
				return Map.of();
			}

			Map<String, Object> result = new HashMap<>();
			for (Map.Entry<String, Object> entry : toolContext.getContext().entrySet()) {
				result.put("mcp_" + entry.getKey(), entry.getValue());
			}
			return result;
		};

		Map<String, Object> contextMap = new HashMap<>();
		contextMap.put("key1", "value1");
		contextMap.put("key2", "value2");
		ToolContext toolContext = new ToolContext(contextMap);

		Map<String, Object> result = customConverter.convert(toolContext);

		assertThat(result).hasSize(2);
		assertThat(result).containsEntry("mcp_key1", "value1");
		assertThat(result).containsEntry("mcp_key2", "value2");
	}

	@Test
	void defaultConverterShouldHandleOnlyExchangeKey() {
		ToolContextToMcpMetaConverter converter = ToolContextToMcpMetaConverter.defaultConverter();
		Map<String, Object> contextMap = new HashMap<>();
		contextMap.put(McpToolUtils.TOOL_CONTEXT_MCP_EXCHANGE_KEY, this.mockExchange);
		ToolContext toolContext = new ToolContext(contextMap);

		Map<String, Object> result = converter.convert(toolContext);

		assertThat(result).isEmpty();
	}

	@Test
	void defaultConverterShouldHandleOnlyNullValues() {
		ToolContextToMcpMetaConverter converter = ToolContextToMcpMetaConverter.defaultConverter();
		Map<String, Object> contextMap = new HashMap<>();
		contextMap.put("key1", null);
		contextMap.put("key2", null);
		ToolContext toolContext = new ToolContext(contextMap);

		Map<String, Object> result = converter.convert(toolContext);

		assertThat(result).isEmpty();
	}

	@Test
	void defaultConverterShouldPreserveOriginalMapImmutability() {
		ToolContextToMcpMetaConverter converter = ToolContextToMcpMetaConverter.defaultConverter();
		Map<String, Object> originalMap = new HashMap<>();
		originalMap.put("key1", "value1");
		originalMap.put("key2", null);
		originalMap.put(McpToolUtils.TOOL_CONTEXT_MCP_EXCHANGE_KEY, this.mockExchange);

		// Create a copy to verify original is not modified
		Map<String, Object> originalMapCopy = new HashMap<>(originalMap);
		ToolContext toolContext = new ToolContext(originalMap);

		Map<String, Object> result = converter.convert(toolContext);

		assertThat(originalMap).isEqualTo(originalMapCopy);
		assertThat(originalMap).hasSize(3);

		assertThat(result).hasSize(1);
		assertThat(result).containsEntry("key1", "value1");
	}

	@Test
	void interfaceMethodShouldBeCallable() {
		ToolContextToMcpMetaConverter converter = new ToolContextToMcpMetaConverter() {
			@Override
			public Map<String, Object> convert(ToolContext toolContext) {
				return Map.of("custom", "implementation");
			}
		};

		Map<String, Object> result = converter.convert(new ToolContext(Map.of()));

		assertThat(result).containsEntry("custom", "implementation");
	}

	@Test
	void defaultConverterShouldHandleSpecialCharactersInKeys() {
		ToolContextToMcpMetaConverter converter = ToolContextToMcpMetaConverter.defaultConverter();
		Map<String, Object> contextMap = new HashMap<>();
		contextMap.put("key-with-dash", "value1");
		contextMap.put("key.with.dots", "value2");
		contextMap.put("key_with_underscore", "value3");
		contextMap.put("key with spaces", "value4");
		contextMap.put("key@with#special$chars", "value5");
		ToolContext toolContext = new ToolContext(contextMap);

		Map<String, Object> result = converter.convert(toolContext);

		assertThat(result).hasSize(5);
		assertThat(result).containsEntry("key-with-dash", "value1");
		assertThat(result).containsEntry("key.with.dots", "value2");
		assertThat(result).containsEntry("key_with_underscore", "value3");
		assertThat(result).containsEntry("key with spaces", "value4");
		assertThat(result).containsEntry("key@with#special$chars", "value5");
	}

	@Test
	void defaultConverterShouldHandleEmptyStringValues() {
		ToolContextToMcpMetaConverter converter = ToolContextToMcpMetaConverter.defaultConverter();
		Map<String, Object> contextMap = new HashMap<>();
		contextMap.put("emptyString", "");
		contextMap.put("nonEmptyString", "value");
		ToolContext toolContext = new ToolContext(contextMap);

		Map<String, Object> result = converter.convert(toolContext);

		assertThat(result).hasSize(2);
		assertThat(result).containsEntry("emptyString", "");
		assertThat(result).containsEntry("nonEmptyString", "value");
	}

}
