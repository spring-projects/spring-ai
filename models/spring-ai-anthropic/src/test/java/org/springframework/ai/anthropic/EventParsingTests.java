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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.springframework.ai.anthropic.api.AnthropicApi.StreamEvent;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.log.LogAccessor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class EventParsingTests {

	private static final LogAccessor logger = new LogAccessor(EventParsingTests.class);

	@Test
	public void readEvents() throws IOException {
		String json = new DefaultResourceLoader().getResource("classpath:/sample_events.json")
			.getContentAsString(Charset.defaultCharset());

		List<StreamEvent> events = new ObjectMapper().readerFor(new TypeReference<List<StreamEvent>>() {

		}).readValue(json);

		logger.info(events.toString());

		assertThat(events).hasSize(31);

	}

}
