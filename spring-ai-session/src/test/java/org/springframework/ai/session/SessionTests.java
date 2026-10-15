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

package org.springframework.ai.session;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link Session}.
 *
 * @author Christian Tzolov
 */
class SessionTests {

	@Test
	void sessionIsItsIdLifetimeAndMetadata() {
		Instant created = Instant.parse("2026-01-01T00:00:00Z");
		Session session = Session.builder()
			.id("s")
			.createdAt(created)
			.expiresAt(null)
			.metadata(Map.of("tenant", "acme"))
			.metadata(SessionAttributes.AGENT_NAME, "support")
			.build();

		assertThat(session.id()).isEqualTo("s");
		assertThat(session.createdAt()).isEqualTo(created);
		assertThat(session.expiresAt()).isNull();
		assertThat(session.metadata()).containsEntry("tenant", "acme")
			.containsEntry(SessionAttributes.AGENT_NAME, "support");
		assertThat(SessionAttributes.agentName(session)).isEqualTo("support");
	}

	@Test
	void builderDefaultsToNoExpiryAndRequiresId() {
		Session session = Session.builder().id("s").build();

		assertThat(session.expiresAt()).isNull();
		assertThat(session.metadata()).isEmpty();
		assertThatIllegalArgumentException().isThrownBy(() -> Session.builder().build());
	}

}
