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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.modelcontextprotocol.spec.McpSchema;

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
@ConditionalOnMissingBean(name = "mcpServerObjectMapper")
public class McpServerObjectMapperAutoConfiguration {

	/**
	 * Creates a configured ObjectMapper for MCP server JSON serialization.
	 * <p>
	 * This ObjectMapper is specifically configured for MCP protocol compliance with:
	 * <ul>
	 * <li>Lenient deserialization that doesn't fail on unknown properties</li>
	 * <li>Proper handling of empty beans during serialization</li>
	 * <li>Exclusion of null values from JSON output</li>
	 * <li>Standard Jackson modules for Java 8, JSR-310, and Kotlin support</li>
	 * </ul>
	 * <p>
	 * This bean can be overridden by providing a custom ObjectMapper bean with the name
	 * "mcpServerObjectMapper".
	 * @return configured ObjectMapper instance for MCP server operations
	 */
	// NOTE: defaultCandidate=false prevents this MCP specific mapper from being injected
	// in code that doesn't explicitly qualify injection point by name.
	@Bean(name = "mcpServerObjectMapper", defaultCandidate = false)
	public ObjectMapper mcpServerObjectMapper() {
		return JsonMapper.builder()
			// Deserialization configuration
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
			// Serialization configuration
			.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			.serializationInclusion(JsonInclude.Include.NON_NULL)
			// Register standard Jackson modules (Jdk8, JavaTime, ParameterNames, Kotlin)
			.addModules(JacksonUtils.instantiateAvailableModules())
			.build();
	}

}
