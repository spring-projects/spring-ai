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

package org.springframework.ai.chat.client.advisor;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.content.Content;
import org.springframework.ai.content.MediaContent;
import org.springframework.ai.tokenizer.TokenCountEstimator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LastMaxTokenSizeContentPurger}.
 *
 * @author Junhyeok Lee
 */
class LastMaxTokenSizeContentPurgerTests {

	@Test
	void purgeExcessWhenDatumIsEmpty() {
		LastMaxTokenSizeContentPurger purger = new LastMaxTokenSizeContentPurger(mock(TokenCountEstimator.class), 100);
		List<Content> result = purger.purgeExcess(List.of(), 0);
		assertThat(result).isEmpty();
	}

	@Test
	void purgeExcessWhenMaxTokenSizeIsZero() {
		LastMaxTokenSizeContentPurger purger = new LastMaxTokenSizeContentPurger(mock(TokenCountEstimator.class), 0);
		List<Content> result = purger.purgeExcess(List.of(mock(MediaContent.class), mock(MediaContent.class)), 100);
		assertThat(result).isEmpty();
	}

	@Test
	void purgeExcessWhenNoExcessTokens() {
		LastMaxTokenSizeContentPurger purger = new LastMaxTokenSizeContentPurger(mock(TokenCountEstimator.class), 100);
		List<Content> result = purger.purgeExcess(List.of(mock(MediaContent.class), mock(MediaContent.class)), 100);
		assertThat(result).hasSize(2);
	}

	@Test
	void purgeExcessWhenSomeContentNeedsToBePurged() {
		TokenCountEstimator tokenCountEstimator = mock(TokenCountEstimator.class);
		LastMaxTokenSizeContentPurger purger = new LastMaxTokenSizeContentPurger(tokenCountEstimator, 100);
		when(tokenCountEstimator.estimate(any(MediaContent.class))).thenReturn(100);
		List<Content> result = purger.purgeExcess(List.of(mock(MediaContent.class), mock(MediaContent.class)), 200);
		assertThat(result).hasSize(1);
	}

	@Test
	void purgeExcessWhenAllContentMustBePurged() {
		TokenCountEstimator tokenCountEstimator = mock(TokenCountEstimator.class);
		LastMaxTokenSizeContentPurger purger = new LastMaxTokenSizeContentPurger(tokenCountEstimator, 100);
		when(tokenCountEstimator.estimate(any(MediaContent.class))).thenReturn(110);
		List<Content> result = purger.purgeExcess(List.of(mock(MediaContent.class), mock(MediaContent.class)), 220);
		assertThat(result).isEmpty();
	}

}
