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

package org.springframework.ai.model.mistralai.autoconfigure;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.ai.mistralai.ocr.MistralOcrApi;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link MistralAiOcrProperties} interacting with
 * {@link MistralAiCommonProperties}.
 *
 * @author Alexandros Pappas
 * @author Issam El-atif
 * @since 1.1.0
 */
class MistralAiOcrPropertiesTests {

	// Define common configurations to load in tests
	private final AutoConfigurations autoConfigurations = SpringAiTestAutoConfigurations
		.of(MistralAiOcrAutoConfiguration.class);

	@Test
	void commonPropertiesAppliedToOcr() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.mistralai.base-url=COMMON_BASE_URL",
					"spring.ai.mistralai.api-key=COMMON_API_KEY",
					"spring.ai.mistralai.ocr.options.model=mistral-ocr-specific-model")
			.withConfiguration(this.autoConfigurations)
			.run(context -> {
				assertThat(context).hasSingleBean(MistralAiCommonProperties.class);
				assertThat(context).hasSingleBean(MistralAiOcrProperties.class);

				var commonProps = context.getBean(MistralAiCommonProperties.class);
				var ocrProps = context.getBean(MistralAiOcrProperties.class);

				assertThat(commonProps.getBaseUrl()).isEqualTo("COMMON_BASE_URL");
				assertThat(commonProps.getApiKey()).isEqualTo("COMMON_API_KEY");

				assertThat(ocrProps.getBaseUrl()).isEqualTo(MistralAiCommonProperties.DEFAULT_BASE_URL);
				assertThat(ocrProps.getApiKey()).isNull();

				assertThat(ocrProps.getOptions()).isNotNull();
				assertThat(ocrProps.getOptions().getModel()).isEqualTo("mistral-ocr-specific-model");

				assertThat(context).hasSingleBean(MistralOcrApi.class);
			});
	}

	@Test
	void ocrSpecificPropertiesOverrideCommon() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.mistralai.base-url=COMMON_BASE_URL",
					"spring.ai.mistralai.api-key=COMMON_API_KEY", "spring.ai.mistralai.ocr.base-url=OCR_BASE_URL",
					"spring.ai.mistralai.ocr.api-key=OCR_API_KEY",
					"spring.ai.mistralai.ocr.options.model=mistral-ocr-default")
			.withConfiguration(this.autoConfigurations)
			.run(context -> {
				assertThat(context).hasSingleBean(MistralAiCommonProperties.class);
				assertThat(context).hasSingleBean(MistralAiOcrProperties.class);

				var commonProps = context.getBean(MistralAiCommonProperties.class);
				var ocrProps = context.getBean(MistralAiOcrProperties.class);

				assertThat(commonProps.getBaseUrl()).isEqualTo("COMMON_BASE_URL");
				assertThat(commonProps.getApiKey()).isEqualTo("COMMON_API_KEY");

				assertThat(ocrProps.getBaseUrl()).isEqualTo("OCR_BASE_URL");
				assertThat(ocrProps.getApiKey()).isEqualTo("OCR_API_KEY");

				assertThat(ocrProps.getOptions()).isNotNull();
				assertThat(ocrProps.getOptions().getModel()).isEqualTo("mistral-ocr-default");

				assertThat(context).hasSingleBean(MistralOcrApi.class);
			});
	}

	@Test
	void ocrOptionsBinding() {
		new ApplicationContextRunner().withPropertyValues("spring.ai.mistralai.api-key=API_KEY",
				"spring.ai.mistralai.ocr.options.model=custom-ocr-model",
				"spring.ai.mistralai.ocr.options.id=ocr-request-id-123", "spring.ai.mistralai.ocr.options.pages=0,1,5",
				"spring.ai.mistralai.ocr.options.includeImageBase64=true",
				"spring.ai.mistralai.ocr.options.imageLimit=25", "spring.ai.mistralai.ocr.options.imageMinSize=150")
			.withConfiguration(this.autoConfigurations)
			.run(context -> {
				assertThat(context).hasSingleBean(MistralAiOcrProperties.class);
				var ocrProps = context.getBean(MistralAiOcrProperties.class);
				var options = ocrProps.getOptions();

				assertThat(options).isNotNull();
				assertThat(options.getModel()).isEqualTo("custom-ocr-model");
				assertThat(options.getId()).isEqualTo("ocr-request-id-123");
				assertThat(options.getPages()).containsExactly(0, 1, 5);
				assertThat(options.getIncludeImageBase64()).isTrue();
				assertThat(options.getImageLimit()).isEqualTo(25);
				assertThat(options.getImageMinSize()).isEqualTo(150);
			});
	}

	@Test
	void ocrActivationViaModelProperty() {
		// Scenario 1: OCR explicitly disabled
		new ApplicationContextRunner().withConfiguration(this.autoConfigurations)
			.withPropertyValues("spring.ai.mistralai.api-key=API_KEY", "spring.ai.model.ocr=none")
			.run(context -> {
				assertThat(context.getBeansOfType(MistralAiOcrProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(MistralOcrApi.class)).isEmpty();
				// Should not have common properties either if only OCR config was loaded
				// and then disabled
				assertThat(context.getBeansOfType(MistralAiCommonProperties.class)).isEmpty();
			});

		// Scenario 2: OCR explicitly enabled for 'mistral'
		new ApplicationContextRunner().withConfiguration(this.autoConfigurations)
			.withPropertyValues("spring.ai.mistralai.api-key=API_KEY", "spring.ai.model.ocr=mistral")
			.run(context -> {
				assertThat(context).hasSingleBean(MistralAiCommonProperties.class); // Enabled
																					// by
																					// MistralAiOcrAutoConfiguration
				assertThat(context).hasSingleBean(MistralAiOcrProperties.class);
				assertThat(context).hasSingleBean(MistralOcrApi.class);
			});

		// Scenario 3: OCR implicitly enabled (default behavior when property is absent)
		new ApplicationContextRunner().withConfiguration(this.autoConfigurations)
			.withPropertyValues("spring.ai.mistralai.api-key=API_KEY")
			.run(context -> {
				assertThat(context).hasSingleBean(MistralAiCommonProperties.class); // Enabled
																					// by
																					// MistralAiOcrAutoConfiguration
				assertThat(context).hasSingleBean(MistralAiOcrProperties.class);
				assertThat(context).hasSingleBean(MistralOcrApi.class);
			});

		// Scenario 4: OCR implicitly disabled when another provider is chosen
		new ApplicationContextRunner().withConfiguration(this.autoConfigurations)
			.withPropertyValues("spring.ai.mistralai.api-key=API_KEY", "spring.ai.model.ocr=some-other-provider")
			.run(context -> {
				assertThat(context.getBeansOfType(MistralAiOcrProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(MistralOcrApi.class)).isEmpty();
				// Common properties might still be loaded if another Mistral AI config
				// (like Chat) was active,
				// but in this minimal test setup, they shouldn't be loaded if OCR is
				// disabled.
				assertThat(context.getBeansOfType(MistralAiCommonProperties.class)).isEmpty();
			});
	}

	@Test
	public void ocrCustomTimeouts() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
						"spring.ai.mistralai.api-key=API_KEY",
						"spring.ai.mistralai.base-url=TEST_BASE_URL",
						"spring.ai.mistralai.connect-timeout=5s",
						"spring.ai.mistralai.read-timeout=30s")
				// @formatter:on
			.withConfiguration(this.autoConfigurations)
			.run(context -> {
				var connectionProperties = context.getBean(MistralAiCommonProperties.class);

				assertThat(connectionProperties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
				assertThat(connectionProperties.getReadTimeout()).isEqualTo(Duration.ofSeconds(30));
			});
	}

}
