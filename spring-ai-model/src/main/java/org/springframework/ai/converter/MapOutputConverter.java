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

package org.springframework.ai.converter;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.messaging.Message;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.support.MessageBuilder;

/**
 * {@link StructuredOutputConverter} implementation that uses a pre-configured
 * {@link JacksonJsonMessageConverter} to convert the LLM output into a
 * java.util.Map&lt;String, Object&gt; instance.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 */
public class MapOutputConverter extends AbstractMessageOutputConverter<Map<String, Object>> {

	public MapOutputConverter() {
		super(new JacksonJsonMessageConverter(
				JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)));
	}

	@Override
	public Map<String, Object> convert(String text) {
		if (text.startsWith("```json") && text.endsWith("```")) {
			text = text.substring(7, text.length() - 3);
		}

		Message<?> message = MessageBuilder.withPayload(text.getBytes(StandardCharsets.UTF_8)).build();
		Map result = (Map) this.getMessageConverter().fromMessage(message, HashMap.class);
		return result == null ? new HashMap<>() : result;
	}

	@Override
	public String getFormat() {
		String raw = """
				Your response should be in JSON format.
				The data structure for the JSON should match this Java class: %s
				Do not include any explanations, only provide a RFC8259 compliant JSON response following this format without deviation.
				Remove the ```json markdown surrounding the output including the trailing "```".
				""";
		return String.format(raw, HashMap.class.getName());
	}

}
