/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.model.anthropic.autoconfigure;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Converter to deserialize JSON string into {@link AnthropicApi.ToolChoice}. This
 * converter is used by Spring Boot's configuration properties binding to convert string
 * values from application properties into ToolChoice objects.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
@Component
@ConfigurationPropertiesBinding
public class StringToToolChoiceConverter implements Converter<String, AnthropicApi.ToolChoice> {

	@Override
	public AnthropicApi.ToolChoice convert(String source) {
		return ModelOptionsUtils.jsonToObject(source, AnthropicApi.ToolChoice.class);
	}

}
