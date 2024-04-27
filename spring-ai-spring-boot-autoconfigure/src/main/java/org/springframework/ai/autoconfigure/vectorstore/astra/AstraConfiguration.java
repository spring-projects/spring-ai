/*
 * Copyright 2024 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.autoconfigure.vectorstore.astra;

import org.springframework.boot.autoconfigure.cassandra.CqlSessionBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

import org.springframework.context.annotation.Configuration;

/**
 * @author Cédrick Lunven
 * @since 1.0.0
 */
@Configuration
public class AstraConfiguration {

	@Bean
	public CqlSessionBuilderCustomizer sessionBuilderCustomizer(AstraExtraSettings astraSettings) {
		if (null != astraSettings.getSecureConnectBundle()) {
			Path bundle = astraSettings.getSecureConnectBundle().toPath();
			return builder -> builder.withCloudSecureConnectBundle(bundle);
		}
		return (cqlSessionBuilder) -> {
			/* noop */};
	}

}