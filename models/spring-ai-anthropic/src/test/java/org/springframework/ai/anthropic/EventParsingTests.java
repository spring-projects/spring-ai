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

package org.springframework.ai.anthropic;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.anthropic.api.AnthropicApi.StreamEvent;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class EventParsingTests {

	private static final Logger logger = LoggerFactory.getLogger(EventParsingTests.class);

	@Test
	public void readEvents() throws IOException {
		String json = new DefaultResourceLoader().getResource("classpath:/sample_events.json")
			.getContentAsString(Charset.defaultCharset());

		List<StreamEvent> events = JsonMapper.shared().readerFor(new TypeReference<>() {
		}).readValue(json);

		logger.info(events.toString());

		assertThat(events).hasSize(31);

	}

}
