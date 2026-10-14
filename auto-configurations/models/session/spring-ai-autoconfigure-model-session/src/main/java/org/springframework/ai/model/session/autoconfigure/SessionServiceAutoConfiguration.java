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

package org.springframework.ai.model.session.autoconfigure;

import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.SessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for {@link DefaultSessionService}.
 *
 * <p>
 * Creates a {@link SessionService} bean, a {@link DefaultSessionService} wrapped by every
 * {@link SessionServiceDecorator} bean in order, whenever a single (or primary)
 * {@link SessionRepository} bean is present in the application context and no
 * {@link SessionService} bean has been declared by the application. This applies
 * regardless of which {@link SessionRepository} implementation is in use — in-memory,
 * JDBC, Redis, etc.
 *
 * <p>
 * The auto-configuration is ordered after the JDBC repository auto-configuration so that
 * the repository bean is guaranteed to be available when this configuration runs.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
@AutoConfiguration(
		afterName = "org.springframework.ai.model.session.jdbc.autoconfigure.JdbcSessionRepositoryAutoConfiguration")
@ConditionalOnClass(SessionService.class)
@ConditionalOnSingleCandidate(SessionRepository.class)
@ConditionalOnMissingBean(SessionService.class)
@EnableConfigurationProperties(SessionServiceProperties.class)
public class SessionServiceAutoConfiguration {

	@Bean
	SessionService sessionService(SessionRepository sessionRepository, SessionServiceProperties properties,
			ObjectProvider<SessionServiceDecorator> decorators) {
		SessionService service = DefaultSessionService.builder()
			.sessionRepository(sessionRepository)
			.defaultTimeToLive(properties.getTimeToLive())
			.allowSystemMessages(properties.isAllowSystemMessages())
			.build();
		for (SessionServiceDecorator decorator : decorators.orderedStream().toList()) {
			service = decorator.decorate(service);
		}
		return service;
	}

}
