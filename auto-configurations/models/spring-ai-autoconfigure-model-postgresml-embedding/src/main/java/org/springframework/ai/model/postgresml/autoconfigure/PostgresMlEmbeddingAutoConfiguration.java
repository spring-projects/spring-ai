/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.model.postgresml.autoconfigure;

import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.postgresml.PostgresMlEmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Auto-configuration class for PostgresMlEmbeddingModel.
 *
 * @author Utkarsh Srivastava
 * @author Christian Tzolov
 */
@AutoConfiguration(after = JdbcTemplateAutoConfiguration.class)
@ConditionalOnClass(PostgresMlEmbeddingModel.class)
@ConditionalOnProperty(name = SpringAIModelProperties.EMBEDDING_MODEL, havingValue = SpringAIModels.POSTGRESML,
		matchIfMissing = true)
@EnableConfigurationProperties(PostgresMlEmbeddingProperties.class)
public class PostgresMlEmbeddingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public PostgresMlEmbeddingModel postgresMlEmbeddingModel(JdbcTemplate jdbcTemplate,
			PostgresMlEmbeddingProperties embeddingProperties) {

		return new PostgresMlEmbeddingModel(jdbcTemplate, embeddingProperties.getOptions(),
				embeddingProperties.isCreateExtension());
	}

}
