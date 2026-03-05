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

package org.springframework.ai.mcp.server.common.autoconfigure;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.modelcontextprotocol.spec.McpSchema;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(McpSchema.class)
@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
@ConditionalOnMissingBean(name = "mcpServerJsonMapper")
public class McpServerJsonMapperAutoConfiguration {

	/**
	 * Creates a configured {@link JsonMapper} for MCP server JSON serialization.
	 * <p>
	 * This JsonMapper is specifically configured for MCP protocol compliance with:
	 * <ul>
	 * <li>Lenient deserialization that doesn't fail on unknown properties</li>
	 * <li>Proper handling of empty beans during serialization</li>
	 * <li>Exclusion of null values from JSON output</li>
	 * <li>Jackson modules via service loader</li>
	 * </ul>
	 * <p>
	 * This bean can be overridden by providing a custom {@link JsonMapper} bean with the
	 * name "mcpServerJsonMapper".
	 * @return configured {@link JsonMapper} instance for MCP server operations
	 */
	// NOTE: defaultCandidate=false prevents this MCP specific mapper from being injected
	// in code that doesn't explicitly qualify injection point by name.
	@Bean(name = "mcpServerJsonMapper", defaultCandidate = false)
	public JsonMapper mcpServerJsonMapper() {
		return JsonMapper.builder()
			// Deserialization configuration
			.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
			// Serialization configuration
			.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
			// Register Jackson modules via server loader
			.addModules(JacksonUtils.instantiateAvailableModules())
			.changeDefaultPropertyInclusion(
					incl -> JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
			.build();
	}

}
