package org.springframework.ai.autoconfigure.observation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.micrometer.model.ModelCompletionContentObservationFilter;
import org.springframework.ai.micrometer.model.ModelPromptContentObservationFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Spring AI observations.
 *
 * @author Thomas Vitale
 */
@AutoConfiguration(
		afterName = "org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration.class")
@EnableConfigurationProperties({ AiObservationProperties.class })
public class AiObservationAutoConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(AiObservationAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = AiObservationProperties.CONFIG_PREFIX, name = "include-prompt",
			havingValue = "true")
	ModelPromptContentObservationFilter llmPromptContentObservationFilter() {
		logger.warn(
				"You have enabled the inclusion of the prompt content in the observations, with the risk of exposing sensitive or private information. Please, be careful!");
		return new ModelPromptContentObservationFilter();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = AiObservationProperties.CONFIG_PREFIX, name = "include-completion",
			havingValue = "true")
	ModelCompletionContentObservationFilter llmCompletionContentObservationFilter() {
		logger.warn(
				"You have enabled the inclusion of the completion content in the observations, with the risk of exposing sensitive or private information. Please, be careful!");
		return new ModelCompletionContentObservationFilter();
	}

}
