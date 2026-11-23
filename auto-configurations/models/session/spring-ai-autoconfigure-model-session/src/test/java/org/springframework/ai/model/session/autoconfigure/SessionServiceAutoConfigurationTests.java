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

import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionAttributes;
import org.springframework.ai.session.SessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link SessionServiceAutoConfiguration}.
 *
 * @author Christian Tzolov
 */
class SessionServiceAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(SessionServiceAutoConfiguration.class));

	@Test
	void sessionServiceBeanIsNotCreatedWithoutRepository() {
		this.contextRunner.run(context -> assertThat(context).doesNotHaveBean(SessionService.class));
	}

	@Test
	void sessionServiceBeanIsCreatedWhenRepositoryPresent() {
		this.contextRunner.withBean(SessionRepository.class, () -> InMemorySessionRepository.builder().build())
			.run(context -> {
				assertThat(context).hasSingleBean(SessionService.class);
				assertThat(context).hasSingleBean(DefaultSessionService.class);
			});
	}

	@Test
	void sessionServiceBeanIsNotCreatedWithAmbiguousRepositories() {
		this.contextRunner.withBean("first", SessionRepository.class, () -> InMemorySessionRepository.builder().build())
			.withBean("second", SessionRepository.class, () -> InMemorySessionRepository.builder().build())
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).doesNotHaveBean(SessionService.class);
			});
	}

	@Test
	void sessionServiceUsesPrimaryRepositoryWhenSeveralExist() {
		SessionRepository primary = InMemorySessionRepository.builder().build();
		this.contextRunner.withBean("first", SessionRepository.class, () -> primary, bd -> bd.setPrimary(true))
			.withBean("second", SessionRepository.class, () -> InMemorySessionRepository.builder().build())
			.run(context -> assertThat(context).hasSingleBean(SessionService.class));
	}

	@Test
	void timeToLiveDefaultsTo60Days() {
		this.contextRunner.withBean(SessionRepository.class, () -> InMemorySessionRepository.builder().build())
			.run(context -> assertThat(context.getBean(SessionServiceProperties.class).getTimeToLive())
				.isEqualTo(Duration.ofDays(60)));
	}

	@Test
	void timeToLiveIsBoundFromProperties() {
		this.contextRunner.withBean(SessionRepository.class, () -> InMemorySessionRepository.builder().build())
			.withPropertyValues("spring.ai.session.time-to-live=2h")
			.run(context -> assertThat(context.getBean(SessionServiceProperties.class).getTimeToLive())
				.isEqualTo(Duration.ofHours(2)));
	}

	@Test
	void storingSystemMessagesIsDisabledByDefault() {
		this.contextRunner.withBean(SessionRepository.class, () -> InMemorySessionRepository.builder().build())
			.run(context -> {
				assertThat(context.getBean(SessionServiceProperties.class).isAllowSystemMessages()).isFalse();
				SessionService service = context.getBean(SessionService.class);
				Session session = service
					.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "alice").build());
				assertThatIllegalArgumentException()
					.isThrownBy(() -> service.appendMessage(session.id(), new SystemMessage("Answer in French.")))
					.withMessageContaining("spring.ai.session.allow-system-messages=true");
			});
	}

	@Test
	void storingSystemMessagesCanBeEnabledWithProperty() {
		this.contextRunner.withBean(SessionRepository.class, () -> InMemorySessionRepository.builder().build())
			.withPropertyValues("spring.ai.session.allow-system-messages=true")
			.run(context -> {
				SessionService service = context.getBean(SessionService.class);
				Session session = service
					.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "alice").build());
				service.appendMessage(session.id(), new SystemMessage("Answer in French."));
				assertThat(service.getMessages(session.id())).extracting(m -> m.getText())
					.containsExactly("Answer in French.");
			});
	}

	@Test
	void sessionServiceBeanBacksOffWhenUserDefined() {
		SessionService customService = mock(SessionService.class);
		this.contextRunner.withBean(SessionRepository.class, () -> InMemorySessionRepository.builder().build())
			.withBean(SessionService.class, () -> customService)
			.run(context -> {
				assertThat(context).hasSingleBean(SessionService.class);
				assertThat(context.getBean(SessionService.class)).isSameAs(customService);
				assertThat(context).doesNotHaveBean(DefaultSessionService.class);
			});
	}

}
