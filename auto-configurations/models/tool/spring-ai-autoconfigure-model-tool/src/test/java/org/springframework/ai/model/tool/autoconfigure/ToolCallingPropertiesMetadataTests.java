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

package org.springframework.ai.model.tool.autoconfigure;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The defaults the configuration metadata of {@link ToolCallingProperties} advertises
 * match the defaults the properties have.
 *
 * @author Christian Tzolov
 */
class ToolCallingPropertiesMetadataTests {

	@Test
	void metadataDefaultsMatchThePropertyDefaults() throws Exception {
		Map<String, String> defaults = metadataDefaults();
		ToolCallingProperties properties = new ToolCallingProperties();

		assertThat(defaults).containsEntry("spring.ai.tools.confirmation.enabled",
				String.valueOf(properties.getConfirmation().isEnabled()));
		assertThat(defaults).containsEntry("spring.ai.tools.limits.max-calls-per-tool-default",
				String.valueOf(properties.getLimits().getMaxCallsPerToolDefault()));
		assertThat(defaults).containsEntry("spring.ai.tools.limits.max-total-tool-calls",
				String.valueOf(properties.getLimits().getMaxTotalToolCalls()));
		assertThat(defaults).containsEntry("spring.ai.tools.limits.on-limit-exceeded",
				properties.getLimits().getOnLimitExceeded().name().toLowerCase());
		assertThat(defaults).containsEntry("spring.ai.tools.observations.include-content",
				String.valueOf(properties.getObservations().isIncludeContent()));
		assertThat(defaults).containsEntry("spring.ai.tools.resolution.fallback.enabled",
				String.valueOf(properties.getResolution().getFallback().isEnabled()));
		assertThat(defaults).containsEntry("spring.ai.tools.throw-exception-on-error",
				String.valueOf(properties.isThrowExceptionOnError()));
	}

	private static Map<String, String> metadataDefaults() throws Exception {
		Map<String, String> defaults = new HashMap<>();
		try (InputStream in = new ClassPathResource("META-INF/additional-spring-configuration-metadata.json")
			.getInputStream()) {
			for (JsonNode property : JsonMapper.builder().build().readTree(in).get("properties")) {
				defaults.put(property.get("name").asString(), property.get("defaultValue").asString());
			}
		}
		return defaults;
	}

}
