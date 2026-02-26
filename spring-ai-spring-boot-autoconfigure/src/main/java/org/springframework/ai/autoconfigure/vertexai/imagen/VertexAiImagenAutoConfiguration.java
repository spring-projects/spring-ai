/*
 * Copyright 2025-2026 the original author or authors.
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

package org.springframework.ai.autoconfigure.vertexai.imagen;

import java.io.IOException;

import com.google.cloud.vertexai.VertexAI;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.ai.vertexai.imagen.VertexAiImagenConnectionDetails;
import org.springframework.ai.vertexai.imagen.VertexAiImagenImageModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * AutoConfiguration for Vertex AI Imagen.
 *
 * @author Sami Marzouki
 */
@AutoConfiguration(after = {SpringAiRetryAutoConfiguration.class})
@ConditionalOnClass({VertexAI.class, VertexAiImagenImageModel.class})
@EnableConfigurationProperties({VertexAiImagenImageProperties.class, VertexAiImagenConnectionProperties.class})
@ImportAutoConfiguration(classes = {SpringAiRetryAutoConfiguration.class})
public class VertexAiImagenAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public VertexAiImagenConnectionDetails connectionDetails(
			VertexAiImagenConnectionProperties connectionProperties) throws IOException {
		Assert.hasText(connectionProperties.getProjectId(), "Vertex AI project-id must be set!");
		Assert.hasText(connectionProperties.getLocation(), "Vertex AI location must be set!");

		var connectionBuilder = VertexAiImagenConnectionDetails.builder()
				.projectId(connectionProperties.getProjectId())
				.location(connectionProperties.getLocation());

		if (StringUtils.hasText(connectionProperties.getApiEndpoint())) {
			connectionBuilder.apiEndpoint(connectionProperties.getApiEndpoint());
		}

		return connectionBuilder.build();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = VertexAiImagenImageProperties.CONFIG_PREFIX, name = "enabled",
			havingValue = "true", matchIfMissing = true)
	public VertexAiImagenImageModel imageModel(VertexAiImagenConnectionDetails connectionDetails,
											   VertexAiImagenImageProperties properties, RetryTemplate retryTemplate,
											   ObjectProvider<ObservationRegistry> observationRegistry,
											   ObjectProvider<ImageModelObservationConvention> observationConvention) {

		var imageModel = new VertexAiImagenImageModel(connectionDetails, properties.getOptions(),
				retryTemplate, observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));

		observationConvention.ifAvailable(imageModel::setObservationConvention);

		return imageModel;
	}

}
