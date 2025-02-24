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

package org.springframework.ai.autoconfigure.mcp.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringJUnitConfig
@Import(SseHttpClientTransportAutoConfiguration.class)
public class SseHttpClientTransportAutoConfigurationTest {
	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void testConditionOnMissingClass_whenClassNotPresent() {
		assertFalse(applicationContext.containsBean("sseHttpClientTransportAutoConfiguration"));
	}

	@Test
	void testConditionOnMissingClass_whenClassPresent() throws ClassNotFoundException {
		Class.forName("io.modelcontextprotocol.client.transport.WebFluxSseClientTransport");
		assertFalse(applicationContext.containsBean("sseHttpClientTransportAutoConfiguration"));
	}
}
