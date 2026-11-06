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

package org.springframework.ai.bedrock.converse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Matt Meckes
 */
class UserAgentProviderTest {

	@AfterEach
	void resetSystemProperty() {
		System.clearProperty("sdk.ua.appId");
	}

	@Test
	void configureSetsSystemProperty() {
		UserAgentProvider.configure();
		assertThat(System.getProperty("sdk.ua.appId")).startsWith("spring-ai/");
	}

	@Test
	void configureAppendsToExistingValue() {
		System.setProperty("sdk.ua.appId", "my-app");
		UserAgentProvider.configure();
		String value = System.getProperty("sdk.ua.appId");
		assertThat(value).startsWith("my-app/");
		assertThat(value).contains("spring-ai/");
	}

	@Test
	void configureIsIdempotent() {
		System.clearProperty("sdk.ua.appId");
		UserAgentProvider.configure();
		String first = System.getProperty("sdk.ua.appId");
		UserAgentProvider.configure();
		assertThat(System.getProperty("sdk.ua.appId")).isEqualTo(first);
	}

	@Test
	void appIdStartsWithExpectedPrefix() {
		assertThat(UserAgentProvider.appId()).startsWith("spring-ai/");
	}

}
