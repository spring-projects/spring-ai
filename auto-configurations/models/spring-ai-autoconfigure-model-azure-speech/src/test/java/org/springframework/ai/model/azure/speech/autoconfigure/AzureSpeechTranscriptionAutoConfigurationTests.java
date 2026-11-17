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

package org.springframework.ai.model.azure.speech.autoconfigure;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.transcription.observation.TranscriptionModelObservationConvention;
import org.springframework.ai.azure.speech.transcription.AzureSpeechAudioTranscriptionOptions;
import org.springframework.ai.azure.speech.transcription.AzureSpeechConfigFactory;
import org.springframework.ai.azure.speech.transcription.AzureSpeechTranscriptionModel;
import org.springframework.ai.azure.speech.transcription.DefaultAzureSpeechConfigFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AzureSpeechTranscriptionAutoConfiguration}: opt-in provider selection
 * (AC7c), key-based validation of the default factory (AC7a), and the custom factory path
 * that needs no connection properties (AC7b).
 *
 * @author Spring AI Contributors
 */
class AzureSpeechTranscriptionAutoConfigurationTests {

	private static final String API_KEY = "super-secret-speech-key";

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(AzureSpeechTranscriptionAutoConfiguration.class));

	private ApplicationContextRunner selected() {
		return this.contextRunner.withPropertyValues("spring.ai.model.audio.transcription=azure-speech");
	}

	@Test
	void createsModelAndDefaultFactoryWhenProviderIsSelected() {
		selected()
			.withPropertyValues("spring.ai.azure.speech.api-key=" + API_KEY, "spring.ai.azure.speech.region=westeurope")
			.run(context -> {
				assertThat(context).hasSingleBean(AzureSpeechTranscriptionModel.class);
				assertThat(context).hasSingleBean(AzureSpeechConfigFactory.class);

				AzureSpeechConfigFactory factory = context.getBean(AzureSpeechConfigFactory.class);
				assertThat(factory).isInstanceOf(DefaultAzureSpeechConfigFactory.class);

				AzureSpeechTranscriptionModel model = context.getBean(AzureSpeechTranscriptionModel.class);
				assertThat(ReflectionTestUtils.getField(model, "speechConfigFactory")).isSameAs(factory);
			});
	}

	@Test
	void createsModelForAnEndpointConnection() {
		selected()
			.withPropertyValues("spring.ai.azure.speech.api-key=" + API_KEY,
					"spring.ai.azure.speech.endpoint=https://westeurope.api.cognitive.microsoft.com/")
			.run(context -> assertThat(context).hasSingleBean(AzureSpeechTranscriptionModel.class));
	}

	@Test
	void boundTranscriptionPropertiesBecomeTheModelDefaults() {
		selected()
			.withPropertyValues("spring.ai.azure.speech.api-key=" + API_KEY, "spring.ai.azure.speech.region=westeurope",
					"spring.ai.azure.speech.transcription.language=en-US",
					"spring.ai.azure.speech.transcription.output-format=detailed")
			.run(context -> {
				AzureSpeechTranscriptionModel model = context.getBean(AzureSpeechTranscriptionModel.class);
				Object defaults = ReflectionTestUtils.getField(model, "defaultOptions");
				assertThat(defaults).isEqualTo(context.getBean(AzureSpeechTranscriptionProperties.class).toOptions())
					.isEqualTo(AzureSpeechAudioTranscriptionOptions.builder()
						.language("en-US")
						.outputFormat(AzureSpeechAudioTranscriptionOptions.OutputFormat.DETAILED)
						.build());
			});
	}

	@Test
	void backsOffWhenTheSelectionPropertyIsAbsent() {
		this.contextRunner
			.withPropertyValues("spring.ai.azure.speech.api-key=" + API_KEY, "spring.ai.azure.speech.region=westeurope")
			.run(context -> {
				assertThat(context).doesNotHaveBean(AzureSpeechTranscriptionModel.class);
				assertThat(context).doesNotHaveBean(AzureSpeechConfigFactory.class);
			});
	}

	@Test
	void backsOffWhenAnotherProviderIsSelected() {
		this.contextRunner
			.withPropertyValues("spring.ai.model.audio.transcription=openai",
					"spring.ai.azure.speech.api-key=" + API_KEY, "spring.ai.azure.speech.region=westeurope")
			.run(context -> {
				assertThat(context).doesNotHaveBean(AzureSpeechTranscriptionModel.class);
				assertThat(context).doesNotHaveBean(AzureSpeechConfigFactory.class);
			});
	}

	@Test
	void backsOffWhenTheModelClassIsNotOnTheClasspath() {
		selected()
			.withPropertyValues("spring.ai.azure.speech.api-key=" + API_KEY, "spring.ai.azure.speech.region=westeurope")
			.withClassLoader(new FilteredClassLoader(AzureSpeechTranscriptionModel.class))
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).doesNotHaveBean("azureSpeechTranscriptionModel");
				assertThat(context).doesNotHaveBean("azureSpeechConfigFactory");
				assertThat(context).doesNotHaveBean(AzureSpeechTranscriptionAutoConfiguration.class);
			});
	}

	@Test
	void backsOffForAUserBeanDeclaredAsThePortableTranscriptionModel() {
		selected().withUserConfiguration(PortableTranscriptionModelConfiguration.class).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(AzureSpeechTranscriptionModel.class);
			assertThat(context).doesNotHaveBean(AzureSpeechConfigFactory.class);
			assertThat(context.getBean(TranscriptionModel.class))
				.isSameAs(PortableTranscriptionModelConfiguration.CUSTOM_MODEL);
		});
	}

	@Test
	void backsOffForAUserBeanDeclaredAsTheAzureSpeechModel() {
		selected().withUserConfiguration(AzureTranscriptionModelConfiguration.class).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(AzureSpeechConfigFactory.class);
			assertThat(context.getBean(AzureSpeechTranscriptionModel.class))
				.isSameAs(AzureTranscriptionModelConfiguration.CUSTOM_MODEL);
		});
	}

	@Test
	void usesAUserConfigFactoryAndRequiresNoConnectionProperties() {
		selected().withUserConfiguration(UserConfigFactoryConfiguration.class).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(AzureSpeechTranscriptionModel.class);
			assertThat(context.getBean(AzureSpeechConfigFactory.class))
				.isSameAs(UserConfigFactoryConfiguration.CUSTOM_FACTORY);

			AzureSpeechTranscriptionModel model = context.getBean(AzureSpeechTranscriptionModel.class);
			assertThat(ReflectionTestUtils.getField(model, "speechConfigFactory"))
				.isSameAs(UserConfigFactoryConfiguration.CUSTOM_FACTORY);
		});
	}

	@Test
	void aUserConfigFactoryWinsOverTheConnectionProperties() {
		selected()
			.withPropertyValues("spring.ai.azure.speech.api-key=" + API_KEY, "spring.ai.azure.speech.region=westeurope")
			.withUserConfiguration(UserConfigFactoryConfiguration.class)
			.run(context -> {
				assertThat(context).hasSingleBean(AzureSpeechConfigFactory.class);
				assertThat(context.getBean(AzureSpeechConfigFactory.class))
					.isSameAs(UserConfigFactoryConfiguration.CUSTOM_FACTORY);
			});
	}

	@Test
	void failsWhenTheApiKeyIsMissing() {
		selected().withPropertyValues("spring.ai.azure.speech.region=westeurope").run(context -> {
			assertThat(context).hasFailed();
			assertThat(context).getFailure()
				.rootCause()
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("spring.ai.azure.speech.api-key");
		});
	}

	@Test
	void failsWhenNeitherARegionNorAnEndpointIsSet() {
		selected().withPropertyValues("spring.ai.azure.speech.api-key=" + API_KEY).run(context -> {
			assertThat(context).hasFailed();
			assertThat(context).getFailure()
				.rootCause()
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("spring.ai.azure.speech.region")
				.hasMessageContaining("spring.ai.azure.speech.endpoint")
				.hasMessageContaining("neither was set");
		});
	}

	@Test
	void failsWhenBothARegionAndAnEndpointAreSet() {
		selected()
			.withPropertyValues("spring.ai.azure.speech.api-key=" + API_KEY, "spring.ai.azure.speech.region=westeurope",
					"spring.ai.azure.speech.endpoint=https://westeurope.api.cognitive.microsoft.com/")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context).getFailure().rootCause().hasMessageContaining("but both were set");
			});
	}

	@Test
	void startupFailureNeverRepeatsTheConfiguredKey() {
		selected().withPropertyValues("spring.ai.azure.speech.api-key=" + API_KEY).run(context -> {
			assertThat(context).hasFailed();
			Throwable failure = context.getStartupFailure();
			assertThat(failure).isNotNull();
			for (Throwable current = failure; current != null; current = current.getCause()) {
				assertThat(current.getMessage()).doesNotContain(API_KEY);
			}
		});
	}

	@Test
	void appliesACustomObservationConventionWhenPresent() {
		TranscriptionModelObservationConvention convention = Mockito
			.mock(TranscriptionModelObservationConvention.class);

		selected()
			.withPropertyValues("spring.ai.azure.speech.api-key=" + API_KEY, "spring.ai.azure.speech.region=westeurope")
			.withBean(TranscriptionModelObservationConvention.class, () -> convention)
			.run(context -> {
				AzureSpeechTranscriptionModel model = context.getBean(AzureSpeechTranscriptionModel.class);
				assertThat(ReflectionTestUtils.getField(model, "observationConvention")).isSameAs(convention);
			});
	}

	@Test
	void usesACustomObservationRegistryWhenPresent() {
		ObservationRegistry registry = ObservationRegistry.create();

		selected()
			.withPropertyValues("spring.ai.azure.speech.api-key=" + API_KEY, "spring.ai.azure.speech.region=westeurope")
			.withBean(ObservationRegistry.class, () -> registry)
			.run(context -> {
				AzureSpeechTranscriptionModel model = context.getBean(AzureSpeechTranscriptionModel.class);
				assertThat(ReflectionTestUtils.getField(model, "observationRegistry")).isSameAs(registry);
			});
	}

	@Configuration(proxyBeanMethods = false)
	static class PortableTranscriptionModelConfiguration {

		static final TranscriptionModel CUSTOM_MODEL = Mockito.mock(TranscriptionModel.class);

		@Bean
		TranscriptionModel transcriptionModel() {
			return CUSTOM_MODEL;
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class AzureTranscriptionModelConfiguration {

		static final AzureSpeechTranscriptionModel CUSTOM_MODEL = Mockito.mock(AzureSpeechTranscriptionModel.class);

		@Bean
		AzureSpeechTranscriptionModel azureModel() {
			return CUSTOM_MODEL;
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class UserConfigFactoryConfiguration {

		static final AzureSpeechConfigFactory CUSTOM_FACTORY = Mockito.mock(AzureSpeechConfigFactory.class);

		@Bean
		AzureSpeechConfigFactory azureSpeechConfigFactory() {
			return CUSTOM_FACTORY;
		}

	}

}
