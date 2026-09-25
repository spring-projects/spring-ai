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

package org.springframework.ai.model.openai.autoconfigure;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OpenAiAutoConfigurationUtil#resolveCommonProperties}.
 *
 * Specifically verifies that an explicitly-set empty {@code apiKey} (the no-auth /
 * {@code NoopApiKey} signal) is preserved and not silently replaced by the common-level
 * key.
 *
 * @author Ilayaperumal Gopinathan
 */
public class OpenAiAutoConfigurationUtilTests {

	@Test
	void modelTimeoutTakesPrecedenceOverCommonTimeout() {
		var common = properties(null);
		common.setTimeout(Duration.ofMinutes(10));
		var model = properties(null);
		model.setTimeout(Duration.ofMinutes(30));

		assertThat(OpenAiAutoConfigurationUtil.resolveCommonProperties(common, model).getTimeout())
			.isEqualTo(Duration.ofMinutes(30));
	}

	@Test
	void commonTimeoutUsedWhenModelTimeoutIsNotSet() {
		var common = properties(null);
		common.setTimeout(Duration.ofMinutes(30));

		assertThat(OpenAiAutoConfigurationUtil.resolveCommonProperties(common, properties(null)).getTimeout())
			.isEqualTo(Duration.ofMinutes(30));
	}

	@Test
	void explicitModelTimeoutMatchingTheDefaultIsNotTreatedAsUnset() {
		var common = properties(null);
		common.setTimeout(Duration.ofMinutes(30));
		var model = properties(null);
		model.setTimeout(AbstractOpenAiProperties.DEFAULT_TIMEOUT);

		assertThat(OpenAiAutoConfigurationUtil.resolveCommonProperties(common, model).getTimeout())
			.isEqualTo(AbstractOpenAiProperties.DEFAULT_TIMEOUT);
	}

	@Test
	void defaultTimeoutUsedWhenNeitherIsSet() {
		assertThat(OpenAiAutoConfigurationUtil.resolveCommonProperties(properties(null), properties(null)).getTimeout())
			.isEqualTo(AbstractOpenAiProperties.DEFAULT_TIMEOUT);
	}

	@Test
	void modelApiKeyTakesPrecedenceOverCommonApiKey() {
		var common = properties("common-key");
		var model = properties("model-key");

		var resolved = OpenAiAutoConfigurationUtil.resolveCommonProperties(common, model);

		assertThat(resolved.getApiKey()).isEqualTo("model-key");
	}

	@Test
	void commonApiKeyUsedWhenModelApiKeyIsNull() {
		var common = properties("common-key");
		var model = properties(null);

		var resolved = OpenAiAutoConfigurationUtil.resolveCommonProperties(common, model);

		assertThat(resolved.getApiKey()).isEqualTo("common-key");
	}

	@Test
	void emptyModelApiKeyIsPreservedAndNotReplacedByCommonKey() {
		// An empty string is the no-auth (NoopApiKey) signal. It must be kept as-is
		// and must NOT fall back to the common-level key.
		var common = properties("common-key");
		var model = properties("");

		var resolved = OpenAiAutoConfigurationUtil.resolveCommonProperties(common, model);

		assertThat(resolved.getApiKey()).as("empty model apiKey must be preserved as the no-auth signal").isEmpty();
	}

	@Test
	void emptyCommonApiKeyIsPreservedWhenModelApiKeyIsNull() {
		// If the common key is explicitly empty and the model key is absent, the
		// empty string should flow through (no-auth at the common level).
		var common = properties("");
		var model = properties(null);

		var resolved = OpenAiAutoConfigurationUtil.resolveCommonProperties(common, model);

		assertThat(resolved.getApiKey()).as("empty common apiKey must be preserved when model apiKey is absent")
			.isEmpty();
	}

	@Test
	void bothApiKeysNullResolvesToNull() {
		var common = properties(null);
		var model = properties(null);

		var resolved = OpenAiAutoConfigurationUtil.resolveCommonProperties(common, model);

		assertThat(resolved.getApiKey()).isNull();
	}

	private static OpenAiCommonProperties properties(String apiKey) {
		var props = new OpenAiCommonProperties();
		props.setApiKey(apiKey);
		return props;
	}

}
