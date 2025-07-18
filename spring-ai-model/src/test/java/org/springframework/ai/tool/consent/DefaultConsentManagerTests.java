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

package org.springframework.ai.tool.consent;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.annotation.RequiresConsent.ConsentLevel;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DefaultConsentManager}.
 *
 * @author Hyunjoon Park
 */
class DefaultConsentManagerTests {

	private DefaultConsentManager consentManager;

	private AtomicInteger consentRequestCount;

	private BiFunction<String, Map<String, Object>, Boolean> alwaysApproveHandler;

	private BiFunction<String, Map<String, Object>, Boolean> alwaysDenyHandler;

	@BeforeEach
	void setUp() {
		consentRequestCount = new AtomicInteger(0);
		alwaysApproveHandler = (message, params) -> {
			consentRequestCount.incrementAndGet();
			return true;
		};
		alwaysDenyHandler = (message, params) -> {
			consentRequestCount.incrementAndGet();
			return false;
		};
	}

	@Test
	void everyTimeConsentAlwaysRequiresNewConsent() {
		// Given
		consentManager = new DefaultConsentManager(alwaysApproveHandler);
		Map<String, Object> params = Map.of("param", "value");

		// When - First call
		boolean granted1 = consentManager.requestConsent("tool1", "Approve?", ConsentLevel.EVERY_TIME, new String[0],
				params);

		// Then
		assertThat(granted1).isTrue();
		assertThat(consentRequestCount.get()).isEqualTo(1);

		// When - Second call
		boolean granted2 = consentManager.requestConsent("tool1", "Approve?", ConsentLevel.EVERY_TIME, new String[0],
				params);

		// Then
		assertThat(granted2).isTrue();
		assertThat(consentRequestCount.get()).isEqualTo(2);
	}

	@Test
	void sessionConsentRemembersApproval() {
		// Given
		consentManager = new DefaultConsentManager(alwaysApproveHandler);
		Map<String, Object> params = Map.of("param", "value");

		// When - First call
		boolean granted1 = consentManager.requestConsent("tool1", "Approve?", ConsentLevel.SESSION, new String[0],
				params);

		// Then
		assertThat(granted1).isTrue();
		assertThat(consentRequestCount.get()).isEqualTo(1);

		// When - Second call (should use stored consent)
		boolean granted2 = consentManager.requestConsent("tool1", "Approve?", ConsentLevel.SESSION, new String[0],
				params);

		// Then
		assertThat(granted2).isTrue();
		assertThat(consentRequestCount.get()).isEqualTo(1); // No new request
	}

	@Test
	void rememberConsentPersistsApproval() {
		// Given
		consentManager = new DefaultConsentManager(alwaysApproveHandler);
		Map<String, Object> params = Map.of("param", "value");

		// When - First call
		boolean granted1 = consentManager.requestConsent("tool1", "Approve?", ConsentLevel.REMEMBER, new String[0],
				params);

		// Then
		assertThat(granted1).isTrue();
		assertThat(consentRequestCount.get()).isEqualTo(1);

		// When - Multiple subsequent calls
		for (int i = 0; i < 5; i++) {
			boolean granted = consentManager.requestConsent("tool1", "Approve?", ConsentLevel.REMEMBER, new String[0],
					params);
			assertThat(granted).isTrue();
		}

		// Then - Still only one consent request
		assertThat(consentRequestCount.get()).isEqualTo(1);
	}

	@Test
	void deniedConsentIsNotStored() {
		// Given
		consentManager = new DefaultConsentManager(alwaysDenyHandler);
		Map<String, Object> params = Map.of("param", "value");

		// When - First call (denied)
		boolean granted1 = consentManager.requestConsent("tool1", "Approve?", ConsentLevel.SESSION, new String[0],
				params);

		// Then
		assertThat(granted1).isFalse();
		assertThat(consentRequestCount.get()).isEqualTo(1);

		// When - Second call (should request again since previous was denied)
		boolean granted2 = consentManager.requestConsent("tool1", "Approve?", ConsentLevel.SESSION, new String[0],
				params);

		// Then
		assertThat(granted2).isFalse();
		assertThat(consentRequestCount.get()).isEqualTo(2);
	}

	@Test
	void consentWithCategories() {
		// Given
		consentManager = new DefaultConsentManager(alwaysApproveHandler);
		Map<String, Object> params = Map.of("param", "value");
		String[] categories = { "destructive", "data-modification" };

		// When - Grant consent for specific categories
		boolean granted1 = consentManager.requestConsent("tool1", "Approve?", ConsentLevel.SESSION, categories, params);

		// Then
		assertThat(granted1).isTrue();
		assertThat(consentRequestCount.get()).isEqualTo(1);

		// When - Same tool, same categories (should use stored consent)
		boolean granted2 = consentManager.requestConsent("tool1", "Approve?", ConsentLevel.SESSION, categories, params);

		// Then
		assertThat(granted2).isTrue();
		assertThat(consentRequestCount.get()).isEqualTo(1);

		// When - Same tool, different categories (should request new consent)
		String[] differentCategories = { "read-only" };
		boolean granted3 = consentManager.requestConsent("tool1", "Approve?", ConsentLevel.SESSION, differentCategories,
				params);

		// Then
		assertThat(granted3).isTrue();
		assertThat(consentRequestCount.get()).isEqualTo(2);
	}

	@Test
	void revokeConsentForSpecificTool() {
		// Given
		consentManager = new DefaultConsentManager(alwaysApproveHandler);
		Map<String, Object> params = Map.of("param", "value");

		// When - Grant consent
		consentManager.requestConsent("tool1", "Approve?", ConsentLevel.SESSION, new String[0], params);
		assertThat(consentRequestCount.get()).isEqualTo(1);

		// When - Revoke consent
		consentManager.revokeConsent("tool1", new String[0]);

		// When - Request consent again (should need new approval)
		consentManager.requestConsent("tool1", "Approve?", ConsentLevel.SESSION, new String[0], params);

		// Then
		assertThat(consentRequestCount.get()).isEqualTo(2);
	}

	@Test
	void clearAllConsents() {
		// Given
		consentManager = new DefaultConsentManager(alwaysApproveHandler);
		Map<String, Object> params = Map.of("param", "value");

		// When - Grant consent for multiple tools
		consentManager.requestConsent("tool1", "Approve?", ConsentLevel.SESSION, new String[0], params);
		consentManager.requestConsent("tool2", "Approve?", ConsentLevel.REMEMBER, new String[0], params);
		assertThat(consentRequestCount.get()).isEqualTo(2);

		// When - Clear all consents
		consentManager.clearAllConsents();

		// When - Request consent again (should need new approvals)
		consentManager.requestConsent("tool1", "Approve?", ConsentLevel.SESSION, new String[0], params);
		consentManager.requestConsent("tool2", "Approve?", ConsentLevel.REMEMBER, new String[0], params);

		// Then
		assertThat(consentRequestCount.get()).isEqualTo(4);
	}

	@Test
	void defaultConstructorAlwaysDeniesConsent() {
		// Given
		consentManager = new DefaultConsentManager();
		Map<String, Object> params = Map.of("param", "value");

		// When
		boolean granted = consentManager.requestConsent("tool1", "Approve?", ConsentLevel.SESSION, new String[0],
				params);

		// Then
		assertThat(granted).isFalse();
	}

	@Test
	void hasValidConsentChecksStoredConsent() {
		// Given
		consentManager = new DefaultConsentManager(alwaysApproveHandler);
		Map<String, Object> params = Map.of("param", "value");

		// When - No consent stored yet
		boolean hasConsent1 = consentManager.hasValidConsent("tool1", ConsentLevel.SESSION, new String[0]);

		// Then
		assertThat(hasConsent1).isFalse();

		// When - Grant consent
		consentManager.requestConsent("tool1", "Approve?", ConsentLevel.SESSION, new String[0], params);

		// Then - Now has valid consent
		boolean hasConsent2 = consentManager.hasValidConsent("tool1", ConsentLevel.SESSION, new String[0]);
		assertThat(hasConsent2).isTrue();

		// When - Check EVERY_TIME consent (always false)
		boolean hasConsent3 = consentManager.hasValidConsent("tool1", ConsentLevel.EVERY_TIME, new String[0]);
		assertThat(hasConsent3).isFalse();
	}

}
