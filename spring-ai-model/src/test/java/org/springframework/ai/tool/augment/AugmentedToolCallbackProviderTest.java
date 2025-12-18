/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.tool.augment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.ToolParam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link AugmentedToolCallbackProvider}.
 *
 * @author Christian Tzolov
 */
class AugmentedToolCallbackProviderTest {

	@Mock
	private ToolCallbackProvider mockDelegate;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	@DisplayName("Should create provider with delegate")
	void shouldCreateProviderWithDelegate() {
		AugmentedToolCallbackProvider<TestArgs> provider = new AugmentedToolCallbackProvider<>(this.mockDelegate,
				TestArgs.class, args -> {
				}, true);

		assertNotNull(provider);
	}

	@Test
	@DisplayName("Builder should require argumentType")
	void builderShouldRequireArgumentType() {
		IllegalStateException ex = assertThrows(IllegalStateException.class,
				() -> AugmentedToolCallbackProvider.builder().delegate(this.mockDelegate).argumentConsumer(args -> {
				}).build());

		assertEquals("argumentType is required", ex.getMessage());
	}

	@Test
	@DisplayName("Builder should require argumentConsumer")
	void builderShouldRequireArgumentConsumer() {
		IllegalStateException ex = assertThrows(IllegalStateException.class,
				() -> AugmentedToolCallbackProvider.<TestArgs>builder()
					.delegate(this.mockDelegate)
					.argumentType(TestArgs.class)
					.build());

		assertEquals("argumentConsumer is required", ex.getMessage());
	}

	@Test
	@DisplayName("Builder should not allow both provider delegate and toolObject")
	void builderShouldNotAllowBothDelegateAndToolObject() {
		IllegalStateException ex = assertThrows(IllegalStateException.class,
				() -> AugmentedToolCallbackProvider.<TestArgs>builder()
					.delegate(this.mockDelegate)
					.toolObject(new Object())
					.argumentType(TestArgs.class)
					.argumentConsumer(args -> {
					})
					.build());

		assertEquals("Cannot set both delegate and toolObject", ex.getMessage());
	}

	@Test
	@DisplayName("Builder should require either delegate or toolObject")
	void builderShouldRequireDelegateOrToolObject() {
		IllegalStateException ex = assertThrows(IllegalStateException.class,
				() -> AugmentedToolCallbackProvider.<TestArgs>builder()
					.argumentType(TestArgs.class)
					.argumentConsumer(args -> {
					})
					.build());

		assertEquals("Either delegate or toolObject must be set", ex.getMessage());
	}

	@Test
	@DisplayName("Builder should build successfully with delegate")
	void builderShouldBuildWithDelegate() {
		AugmentedToolCallbackProvider<TestArgs> provider = AugmentedToolCallbackProvider.<TestArgs>builder()
			.delegate(this.mockDelegate)
			.argumentType(TestArgs.class)
			.argumentConsumer(args -> {
			})
			.removeExtraArgumentsAfterProcessing(false)
			.build();

		assertNotNull(provider);
	}

	public record TestArgs(@ToolParam(description = "Test field", required = true) String value) {
	}

}
