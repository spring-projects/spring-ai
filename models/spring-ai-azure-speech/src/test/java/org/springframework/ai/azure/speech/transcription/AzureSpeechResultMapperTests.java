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

package org.springframework.ai.azure.speech.transcription;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AzureSpeechResultMapper}, the pure conversions the SDK adapter uses.
 *
 * @author Spring AI Contributors
 */
class AzureSpeechResultMapperTests {

	private static final String WORDS_JSON = "{\"RecognitionStatus\":\"Success\",\"NBest\":[{\"Display\":\"hello world\","
			+ "\"Words\":[{\"Word\":\"hello\",\"Offset\":10000000,\"Duration\":5000000},"
			+ "{\"Word\":\"world\",\"Offset\":16000000,\"Duration\":4000000}]}]}";

	@Test
	void convertsHundredNanosecondTicksToADuration() {
		assertThat(AzureSpeechResultMapper.toDuration(BigInteger.valueOf(10_000_000L)))
			.isEqualTo(Duration.ofSeconds(1));
		assertThat(AzureSpeechResultMapper.toDuration(BigInteger.valueOf(12_345_000L)))
			.isEqualTo(Duration.ofNanos(1_234_500_000L));
	}

	@Test
	void treatsAMissingOrNegativeTickCountAsZero() {
		assertThat(AzureSpeechResultMapper.toDuration(null)).isEqualTo(Duration.ZERO);
		assertThat(AzureSpeechResultMapper.toDuration(BigInteger.valueOf(-5L))).isEqualTo(Duration.ZERO);
	}

	@Test
	void readsWordsFromTheFirstAlternative() {
		String json = "{\"RecognitionStatus\":\"Success\",\"NBest\":[" + "{\"Display\":\"hello world\",\"Words\":["
				+ "{\"Word\":\"hello\",\"Offset\":10000000,\"Duration\":5000000},"
				+ "{\"Word\":\"world\",\"Offset\":16000000,\"Duration\":4000000}]},"
				+ "{\"Display\":\"hello word\",\"Words\":[{\"Word\":\"ignored\",\"Offset\":0,\"Duration\":0}]}]}";

		List<AzureSpeechWord> words = AzureSpeechResultMapper.parseWords(json);

		assertThat(words).containsExactly(new AzureSpeechWord("hello", Duration.ofSeconds(1), Duration.ofMillis(500)),
				new AzureSpeechWord("world", Duration.ofMillis(1600), Duration.ofMillis(400)));
	}

	@Test
	void yieldsAnEmptyListRatherThanFabricatingWords() {
		assertThat(AzureSpeechResultMapper.parseWords(null)).isEmpty();
		assertThat(AzureSpeechResultMapper.parseWords("  ")).isEmpty();
		assertThat(AzureSpeechResultMapper.parseWords("{\"DisplayText\":\"hello\"}")).isEmpty();
		assertThat(AzureSpeechResultMapper.parseWords("{\"NBest\":[]}")).isEmpty();
		assertThat(AzureSpeechResultMapper.parseWords("{\"NBest\":[{\"Display\":\"hi\"}]}")).isEmpty();
	}

	@Test
	void survivesAnUnparseablePayloadWithoutLeakingIt() {
		assertThat(AzureSpeechResultMapper.parseWords("{not json")).isEmpty();
	}

	@Test
	void skipsAnEntryWithNoWordText() {
		String json = "{\"NBest\":[{\"Words\":[{\"Offset\":10000000,\"Duration\":1000000},"
				+ "{\"Word\":\"kept\",\"Offset\":20000000,\"Duration\":1000000}]}]}";

		assertThat(AzureSpeechResultMapper.parseWords(json)).extracting(AzureSpeechWord::text).containsExactly("kept");
	}

	// ----------------------------------------------------- option-conditioned segments

	@Test
	void populatesEveryOptionalFieldWhenItsFeatureRan() {
		AzureSpeechSegment segment = AzureSpeechResultMapper.toSegment("hello world", "result-1",
				Duration.ofMillis(100), Duration.ofMillis(900), WORDS_JSON, () -> "en-US", true, true, true);

		assertThat(segment.text()).isEqualTo("hello world");
		assertThat(segment.resultId()).isEqualTo("result-1");
		assertThat(segment.offset()).isEqualTo(Duration.ofMillis(100));
		assertThat(segment.duration()).isEqualTo(Duration.ofMillis(900));
		assertThat(segment.end()).isEqualTo(Duration.ofSeconds(1));
		assertThat(segment.language()).isEqualTo("en-US");
		assertThat(segment.words()).containsExactly(
				new AzureSpeechWord("hello", Duration.ofSeconds(1), Duration.ofMillis(500)),
				new AzureSpeechWord("world", Duration.ofMillis(1600), Duration.ofMillis(400)));
		assertThat(segment.detailedJson()).isEqualTo(WORDS_JSON);
	}

	@Test
	void leavesEveryOptionalFieldAbsentWhenNoFeatureRan() {
		// The service's own payload is offered in full; none of it may be published,
		// and the detected language must not even be asked for.
		AtomicInteger languageReads = new AtomicInteger();

		AzureSpeechSegment segment = AzureSpeechResultMapper.toSegment("hello world", "result-1",
				Duration.ofMillis(100), Duration.ofMillis(900), WORDS_JSON, () -> {
					languageReads.incrementAndGet();
					return "en-US";
				}, false, false, false);

		assertThat(segment.language()).isNull();
		assertThat(segment.words()).isEmpty();
		assertThat(segment.detailedJson()).isNull();
		assertThat(languageReads).hasValue(0);
		// The fields that do not depend on an option are unaffected.
		assertThat(segment.text()).isEqualTo("hello world");
		assertThat(segment.resultId()).isEqualTo("result-1");
		assertThat(segment.offset()).isEqualTo(Duration.ofMillis(100));
		assertThat(segment.duration()).isEqualTo(Duration.ofMillis(900));
	}

	@Test
	void eachOptionalFeatureControlsOnlyItsOwnField() {
		AzureSpeechSegment languageOnly = AzureSpeechResultMapper.toSegment("hello world", "result-1", Duration.ZERO,
				Duration.ofMillis(900), WORDS_JSON, () -> "en-US", true, false, false);
		assertThat(languageOnly.language()).isEqualTo("en-US");
		assertThat(languageOnly.words()).isEmpty();
		assertThat(languageOnly.detailedJson()).isNull();

		AzureSpeechSegment wordsOnly = AzureSpeechResultMapper.toSegment("hello world", "result-1", Duration.ZERO,
				Duration.ofMillis(900), WORDS_JSON, () -> "en-US", false, true, false);
		assertThat(wordsOnly.language()).isNull();
		assertThat(wordsOnly.words()).extracting(AzureSpeechWord::text).containsExactly("hello", "world");
		// Word timings are published without republishing the raw payload they came from.
		assertThat(wordsOnly.detailedJson()).isNull();

		AzureSpeechSegment detailedOnly = AzureSpeechResultMapper.toSegment("hello world", "result-1", Duration.ZERO,
				Duration.ofMillis(900), WORDS_JSON, () -> "en-US", false, false, true);
		assertThat(detailedOnly.language()).isNull();
		assertThat(detailedOnly.words()).isEmpty();
		assertThat(detailedOnly.detailedJson()).isEqualTo(WORDS_JSON);
	}

	@Test
	void requestedFeaturesTheServiceDidNotAnswerStillYieldAbsentFields() {
		// A simple-format response carries no JSON property at all, and language
		// identification can return no language. Neither is invented.
		AzureSpeechSegment segment = AzureSpeechResultMapper.toSegment("hello", "result-1", Duration.ZERO,
				Duration.ofMillis(500), null, () -> null, true, true, true);

		assertThat(segment.language()).isNull();
		assertThat(segment.words()).isEmpty();
		assertThat(segment.detailedJson()).isNull();
	}

}
