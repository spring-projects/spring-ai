/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;

/**
 * Unit Tests for {@link Usage}.
 *
 * @author John Blum
 * @since 0.7.0
 */
public class UsageTests {

	private Usage mockUsage(Long promptTokens, Long generationTokens) {
		Usage mockUsage = mock(Usage.class);
		doReturn(promptTokens).when(mockUsage).getPromptTokens();
		doReturn(generationTokens).when(mockUsage).getGenerationTokens();
		doCallRealMethod().when(mockUsage).getTotalTokens();
		return mockUsage;
	}

	private void verifyUsage(Usage usage) {
		verify(usage, times(1)).getTotalTokens();
		verify(usage, times(1)).getPromptTokens();
		verify(usage, times(1)).getGenerationTokens();
		verifyNoMoreInteractions(usage);
	}

	@Test
	void totalTokensIsZeroWhenNoPromptOrGenerationMetadataPresent() {

		Usage usage = mockUsage(null, null);

		assertThat(usage.getTotalTokens()).isZero();
		verifyUsage(usage);
	}

	@Test
	void totalTokensEqualsPromptTokens() {

		Usage usage = mockUsage(10L, null);

		assertThat(usage.getTotalTokens()).isEqualTo(10L);
		verifyUsage(usage);
	}

	@Test
	void totalTokensEqualsGenerationTokens() {

		Usage usage = mockUsage(null, 15L);

		assertThat(usage.getTotalTokens()).isEqualTo(15L);
		verifyUsage(usage);
	}

	@Test
	void totalTokensEqualsPromptTokensPlusGenerationTokens() {

		Usage usage = mockUsage(10L, 15L);

		assertThat(usage.getTotalTokens()).isEqualTo(25L);
		verifyUsage(usage);
	}

}
