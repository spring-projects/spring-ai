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

package org.springframework.ai.audio.tts;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link DefaultTextToSpeechOptions}.
 *
 * @author Alexandros Pappas
 */
class DefaultTextToSpeechOptionsTests {

	@Test
	void testBuilderWithAllFields() {
		TextToSpeechOptions options = DefaultTextToSpeechOptions.builder()
			.model("test-model")
			.voice("test-voice")
			.format("test-format")
			.speed(0.8)
			.build();

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getVoice()).isEqualTo("test-voice");
		assertThat(options.getFormat()).isEqualTo("test-format");
		assertThat(options.getSpeed()).isCloseTo(0.8, within(0.0001));
	}

	@Test
	void testCopy() {
		TextToSpeechOptions original = DefaultTextToSpeechOptions.builder()
			.model("test-model")
			.voice("test-voice")
			.format("test-format")
			.speed(0.8)
			.build();

		DefaultTextToSpeechOptions copied = original.copy();
		assertThat(copied).isNotSameAs(original).isEqualTo(original);
	}

	@Test
	void testDefaultValues() {
		DefaultTextToSpeechOptions options = DefaultTextToSpeechOptions.builder().build();
		assertThat(options.getModel()).isNull();
		assertThat(options.getVoice()).isNull();
		assertThat(options.getFormat()).isNull();
		assertThat(options.getSpeed()).isNull();
	}

}
