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
 * Auto-configuration of the {@link SessionService}: a {@link DefaultSessionService} on
 * the single {@link SessionRepository} bean, whatever its implementation, wrapped by
 * every {@link SessionServiceDecorator} bean in order. Backs off when the application
 * declares a {@code SessionService} of its own.
 * <p>
 * Ordered after the JDBC repository auto-configuration: a bean condition sees only the
 * definitions registered before it, so the repository must be defined by then.
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
