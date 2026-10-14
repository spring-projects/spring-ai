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

package org.springframework.ai.model.session.jdbc.autoconfigure;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.init.DatabaseInitializationProperties;

/**
 * Configuration properties for the JDBC-backed
 * {@link org.springframework.ai.session.jdbc.JdbcSessionRepository}.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
@ConfigurationProperties(JdbcSessionRepositoryProperties.CONFIG_PREFIX)
public class JdbcSessionRepositoryProperties extends DatabaseInitializationProperties {

	public static final String CONFIG_PREFIX = "spring.ai.session.repository.jdbc";

	private static final String DEFAULT_SCHEMA_LOCATION = "classpath:org/springframework/ai/session/jdbc/schema-@@platform@@.sql";

	/**
	 * Metadata keys to index in {@code AI_SESSION_INDEX} besides the principal name, so
	 * that {@code findByIndex} can answer for them.
	 */
	private Set<String> indexedAttributes = new LinkedHashSet<>();

	public Set<String> getIndexedAttributes() {
		return this.indexedAttributes;
	}

	public void setIndexedAttributes(Set<String> indexedAttributes) {
		this.indexedAttributes = indexedAttributes;
	}

	@Override
	public String getDefaultSchemaLocation() {
		return DEFAULT_SCHEMA_LOCATION;
	}

}
