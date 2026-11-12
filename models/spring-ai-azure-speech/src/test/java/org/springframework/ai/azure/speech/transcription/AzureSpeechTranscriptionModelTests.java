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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import com.microsoft.cognitiveservices.speech.PropertyId;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.observation.DefaultTranscriptionModelObservationConvention;
import org.springframework.ai.audio.transcription.observation.TranscriptionModelObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.audio.transcription.observation.TranscriptionModelObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * End-to-end tests for {@link AzureSpeechTranscriptionModel#call}, driven through the
 * recognition seam so that no SDK recognizer, no native callback, and no service
 * connection is involved, and no test sleeps for a fixed interval.
 *
 * @author Spring AI Contributors
 */
@Timeout(60)
class AzureSpeechTranscriptionModelTests {

	private static final byte[] WAV = TestWav.pcm(16000, 1, 16, 320);

	private static final String WORDS_JSON = "{\"RecognitionStatus\":\"Success\",\"NBest\":[{\"Display\":\"hello world\","
			+ "\"Words\":[{\"Word\":\"hello\",\"Offset\":10000000,\"Duration\":5000000},"
			+ "{\"Word\":\"world\",\"Offset\":16000000,\"Duration\":4000000}]}]}";

	private static final String SECOND_WORDS_JSON = "{\"RecognitionStatus\":\"Success\",\"NBest\":[{\"Display\":\"bonjour\","
			+ "\"Words\":[{\"Word\":\"bonjour\",\"Offset\":150000000,\"Duration\":5000000}]}]}";

	/**
	 * Loads the SDK's native library once, so that a deadline measured in a test is never
	 * racing a one-off class-initialization cost.
	 */
	@BeforeAll
	static void warmUpSpeechConfigNatives() {
		SpeechConfig.fromSubscription("warm-up", "westus").close();
	}

	private final FakeSpeechSession session = new FakeSpeechSession();

	private final CountingConfigFactory configFactory = new CountingConfigFactory();

	private final TestObservationRegistry observationRegistry = TestObservationRegistry.create();

	private final AtomicReference<RecognitionRun> run = new AtomicReference<>();

	private final ExecutorService ownerExecutor = AzureSpeechTranscriptionModel.daemonPool("test-owner-");

	private final ExecutorService cleanupExecutor = AzureSpeechTranscriptionModel.daemonPool("test-cleanup-");

	@AfterEach
	void shutDownExecutors() {
		this.ownerExecutor.shutdownNow();
		this.cleanupExecutor.shutdownNow();
	}

	// ------------------------------------------------------------------ results

	@Test
	void finalSegmentsAreReturnedInOrderAndJoinedWithASpace() throws Exception {
		AzureSpeechTranscriptionModel model = model(options().build(), Duration.ofMillis(20));
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(model);

		startRecognition();
		this.session.emitSegment("the first", 0, 1000);
		this.session.emitSegment("the second", 1000, 1000);
		this.session.emitSegment("the third", 2000, 500);
		this.session.emitSessionStopped();

		AudioTranscriptionResponse response = call.get(5, TimeUnit.SECONDS);

		assertThat(response.getResult().getOutput()).isEqualTo("the first the second the third");
		AzureSpeechAudioTranscriptionMetadata metadata = (AzureSpeechAudioTranscriptionMetadata) response.getResult()
			.getMetadata();
		assertThat(metadata.getSegments()).extracting(AzureSpeechSegment::text)
			.containsExactly("the first", "the second", "the third");
		assertThat((Object) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.SEGMENT_COUNT))
			.isEqualTo(3);
		assertThat((Object) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.AUDIO_OFFSET))
			.isEqualTo(Duration.ZERO);
		assertThat((Object) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.AUDIO_DURATION))
			.isEqualTo(Duration.ofMillis(2500));
		assertThat((Object) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.PROVIDER))
			.isEqualTo(AiProvider.AZURE_SPEECH.value());
		assertThat((Object) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.MODEL))
			.isEqualTo(AzureSpeechAudioTranscriptionOptions.DEFAULT_MODEL);
		assertThat(response.getMetadata().containsKey(AzureSpeechAudioTranscriptionMetadata.DETECTED_LANGUAGES))
			.isFalse();
	}

	@Test
	void everySegmentFieldTheAdapterPopulatesReachesTheCallerUnchanged() throws Exception {
		// The response-metadata half of the per-segment/per-response criterion is
		// asserted above. This is the per-segment half, for the fields a detailed,
		// language-identified, word-timestamped recognition fills in: before this test
		// they were only ever asserted by the credential-gated live test, which is
		// skipped whenever no key is present.
		AzureSpeechTranscriptionModel model = model(options().build(), Duration.ofMillis(20));
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(model);
		AzureSpeechSegment first = new AzureSpeechSegment("guten Tag", "result-1", Duration.ofMillis(100),
				Duration.ofMillis(900), "de-DE",
				List.of(new AzureSpeechWord("guten", Duration.ofMillis(100), Duration.ofMillis(400)),
						new AzureSpeechWord("Tag", Duration.ofMillis(600), Duration.ofMillis(400))),
				"{\"NBest\":[{\"Display\":\"guten Tag\"}]}");
		AzureSpeechSegment second = new AzureSpeechSegment("good day", "result-2", Duration.ofMillis(1500),
				Duration.ofMillis(500), "en-US", List.of(), "{\"NBest\":[{\"Display\":\"good day\"}]}");

		startRecognition();
		this.session.emitDetailedSegment(first);
		this.session.emitDetailedSegment(second);
		this.session.emitSessionStopped();

		AudioTranscriptionResponse response = call.get(5, TimeUnit.SECONDS);

		AzureSpeechAudioTranscriptionMetadata metadata = (AzureSpeechAudioTranscriptionMetadata) response.getResult()
			.getMetadata();
		// Every record component, not just the text: nothing is rewritten, dropped or
		// merged on the way out.
		assertThat(metadata.getSegments()).containsExactly(first, second);
		AzureSpeechSegment delivered = metadata.getSegments().get(0);
		assertThat(delivered.text()).isEqualTo("guten Tag");
		assertThat(delivered.resultId()).isEqualTo("result-1");
		assertThat(delivered.offset()).isEqualTo(Duration.ofMillis(100));
		assertThat(delivered.duration()).isEqualTo(Duration.ofMillis(900));
		assertThat(delivered.end()).isEqualTo(Duration.ofSeconds(1));
		assertThat(delivered.language()).isEqualTo("de-DE");
		assertThat(delivered.words()).extracting(AzureSpeechWord::text).containsExactly("guten", "Tag");
		assertThat(delivered.words()).extracting(AzureSpeechWord::offset)
			.containsExactly(Duration.ofMillis(100), Duration.ofMillis(600));
		assertThat(delivered.words()).extracting(AzureSpeechWord::duration)
			.containsExactly(Duration.ofMillis(400), Duration.ofMillis(400));
		assertThat(delivered.detailedJson()).isEqualTo("{\"NBest\":[{\"Display\":\"guten Tag\"}]}");
		assertThat(metadata.getSegments().get(1).words()).isEmpty();

		// The response metadata derives from those segments rather than restating them.
		assertThat((Object) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.AUDIO_OFFSET))
			.isEqualTo(Duration.ofMillis(100));
		assertThat((Object) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.AUDIO_DURATION))
			.isEqualTo(Duration.ofMillis(1900));
		assertThat((Object) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.DETECTED_LANGUAGES))
			.isEqualTo(List.of("de-DE", "en-US"));
	}

	@Test
	void aMultiSegmentRecognitionWithEveryOptionalFeatureOnFillsEveryField() throws Exception {
		// AC13's enabled half, driven through the same option-conditioned mapping the SDK
		// adapter uses, so the options under test -- not prebuilt segments -- are what
		// decide which optional fields the caller sees.
		// Language identification is requested at the start of the audio, which a
		// region configuration supports; the continuous mode's endpoint requirement is
		// a separate criterion, asserted by its own test.
		AzureSpeechTranscriptionModel model = model(options().candidateLanguages(List.of("en-US", "fr-FR"))
			.languageIdMode(AzureSpeechAudioTranscriptionOptions.LanguageIdMode.AT_START)
			.outputFormat(AzureSpeechAudioTranscriptionOptions.OutputFormat.DETAILED)
			.wordLevelTimestamps(true)
			.build(), Duration.ofMillis(20));
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(model);

		startRecognition();
		this.session.emitResult("hello world", "result-1", 100, 900, WORDS_JSON, "en-US");
		this.session.emitResult("bonjour", "result-2", 1500, 500, SECOND_WORDS_JSON, "fr-FR");
		this.session.emitResult("hello again", "result-3", 2500, 500, WORDS_JSON, "en-US");
		this.session.emitSessionStopped();

		AudioTranscriptionResponse response = call.get(5, TimeUnit.SECONDS);

		assertThat(response.getResult().getOutput()).isEqualTo("hello world bonjour hello again");
		List<AzureSpeechSegment> segments = ((AzureSpeechAudioTranscriptionMetadata) response.getResult().getMetadata())
			.getSegments();
		assertThat(segments).hasSize(3);
		assertThat(segments).extracting(AzureSpeechSegment::text)
			.containsExactly("hello world", "bonjour", "hello again");
		assertThat(segments).extracting(AzureSpeechSegment::resultId)
			.containsExactly("result-1", "result-2", "result-3");
		assertThat(segments).extracting(AzureSpeechSegment::offset)
			.containsExactly(Duration.ofMillis(100), Duration.ofMillis(1500), Duration.ofMillis(2500));
		assertThat(segments).extracting(AzureSpeechSegment::duration)
			.containsExactly(Duration.ofMillis(900), Duration.ofMillis(500), Duration.ofMillis(500));
		assertThat(segments).extracting(AzureSpeechSegment::end)
			.containsExactly(Duration.ofMillis(1000), Duration.ofMillis(2000), Duration.ofMillis(3000));
		// Every optional field, on every segment: language identification, word
		// timestamps and detailed output were all requested, so all three are populated.
		assertThat(segments).extracting(AzureSpeechSegment::language).containsExactly("en-US", "fr-FR", "en-US");
		assertThat(segments).extracting(AzureSpeechSegment::detailedJson)
			.containsExactly(WORDS_JSON, SECOND_WORDS_JSON, WORDS_JSON);
		assertThat(segments.get(0).words()).containsExactly(
				new AzureSpeechWord("hello", Duration.ofSeconds(1), Duration.ofMillis(500)),
				new AzureSpeechWord("world", Duration.ofMillis(1600), Duration.ofMillis(400)));
		assertThat(segments.get(1).words()).extracting(AzureSpeechWord::text).containsExactly("bonjour");
		assertThat(segments.get(2).words()).extracting(AzureSpeechWord::text).containsExactly("hello", "world");

		// The per-response metadata, including the key only a detected language produces.
		assertThat((Object) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.SEGMENT_COUNT))
			.isEqualTo(3);
		assertThat((Object) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.AUDIO_OFFSET))
			.isEqualTo(Duration.ofMillis(100));
		assertThat((Object) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.AUDIO_DURATION))
			.isEqualTo(Duration.ofMillis(2900));
		assertThat((Object) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.DETECTED_LANGUAGES))
			.isEqualTo(List.of("en-US", "fr-FR"));
		assertThat((Object) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.PROVIDER))
			.isEqualTo(AiProvider.AZURE_SPEECH.value());
	}

	@Test
	void aMultiSegmentRecognitionWithEveryOptionalFeatureOffLeavesThoseFieldsAbsent() throws Exception {
		// AC13's disabled half. The service offers exactly the same payload as the test
		// above -- a detected language and a detailed JSON result on every segment -- but
		// nothing requested those features, so none of it may reach the caller and none
		// of it may be fabricated either.
		AzureSpeechTranscriptionModel model = model(options().build(), Duration.ofMillis(20));
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(model);

		startRecognition();
		this.session.emitResult("hello world", "result-1", 100, 900, WORDS_JSON, "en-US");
		this.session.emitResult("bonjour", "result-2", 1500, 500, SECOND_WORDS_JSON, "fr-FR");
		this.session.emitResult("hello again", "result-3", 2500, 500, WORDS_JSON, "en-US");
		this.session.emitSessionStopped();

		AudioTranscriptionResponse response = call.get(5, TimeUnit.SECONDS);

		assertThat(response.getResult().getOutput()).isEqualTo("hello world bonjour hello again");
		List<AzureSpeechSegment> segments = ((AzureSpeechAudioTranscriptionMetadata) response.getResult().getMetadata())
			.getSegments();
		assertThat(segments).hasSize(3);
		assertThat(segments).allSatisfy(segment -> {
			assertThat(segment.language()).isNull();
			assertThat(segment.words()).isEmpty();
			assertThat(segment.detailedJson()).isNull();
		});
		// What does not depend on an option is still exactly what the service reported.
		assertThat(segments).extracting(AzureSpeechSegment::text)
			.containsExactly("hello world", "bonjour", "hello again");
		assertThat(segments).extracting(AzureSpeechSegment::resultId)
			.containsExactly("result-1", "result-2", "result-3");
		assertThat(segments).extracting(AzureSpeechSegment::offset)
			.containsExactly(Duration.ofMillis(100), Duration.ofMillis(1500), Duration.ofMillis(2500));
		assertThat(segments).extracting(AzureSpeechSegment::duration)
			.containsExactly(Duration.ofMillis(900), Duration.ofMillis(500), Duration.ofMillis(500));

		// The derived response metadata is unchanged, except that no language was
		// detected, so its key is absent rather than present and empty.
		assertThat((Object) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.SEGMENT_COUNT))
			.isEqualTo(3);
		assertThat((Object) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.AUDIO_OFFSET))
			.isEqualTo(Duration.ofMillis(100));
		assertThat((Object) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.AUDIO_DURATION))
			.isEqualTo(Duration.ofMillis(2900));
		assertThat(response.getMetadata().containsKey(AzureSpeechAudioTranscriptionMetadata.DETECTED_LANGUAGES))
			.isFalse();
	}

	@Test
	void aSessionThatMatchedNothingYieldsEmptyTextAndACount() throws Exception {
		AzureSpeechTranscriptionModel model = model(options().build(), Duration.ofMillis(20));
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(model);

		startRecognition();
		this.session.emitNoMatch();
		this.session.emitNoMatch();
		this.session.emitSessionStopped();

		AudioTranscriptionResponse response = call.get(5, TimeUnit.SECONDS);

		assertThat(response.getResult().getOutput()).isEmpty();
		assertThat((Object) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.NO_MATCH_COUNT))
			.isEqualTo(2);
		assertThat((Object) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.SEGMENT_COUNT))
			.isEqualTo(0);
	}

	@Test
	void endOfStreamIsAnOrdinarySuccess() throws Exception {
		AzureSpeechTranscriptionModel model = model(options().build(), Duration.ofMillis(20));
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(model);

		startRecognition();
		this.session.emitSegment("done", 0, 100);
		this.session.emitCanceled(CancellationEvent.endOfStream("EndOfStream"));

		assertThat(call.get(5, TimeUnit.SECONDS).getResult().getOutput()).isEqualTo("done");
		assertThat(this.session.stopCalls).isZero();
	}

	// ------------------------------------------------------------------ failures

	@Test
	void anErrorCancellationBecomesTheDocumentedException() {
		AzureSpeechTranscriptionModel model = model(options().build(), Duration.ofSeconds(5));
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(model);

		startRecognition();
		this.session.emitSegment("partial", 0, 100);
		this.session.emitCanceled(CancellationEvent.error("Error", "AuthenticationFailure", "WebSocket 401"));

		AzureSpeechTranscriptionException failure = failureOf(call);

		assertThat(failure.getCancellationReason()).isEqualTo("Error");
		assertThat(failure.getErrorCode()).isEqualTo("AuthenticationFailure");
		assertThat(failure.getMessage()).contains("reason=Error")
			.contains("errorCode=AuthenticationFailure")
			.contains("check the API key, token and region or endpoint");
		// The SDK's free-text detail is never repeated, only the fixed hint.
		assertThat(failure.getMessage()).doesNotContain("WebSocket 401");
		assertThat(this.session.stopCalls).isZero();
	}

	@Test
	void aCancellationDetailCarryingACredentialNeverReachesTheCaller() {
		AzureSpeechTranscriptionModel model = model(options().build(), Duration.ofSeconds(5));
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(model);
		String poisoned = "auth failed for https://westus.stt.speech.microsoft.com/?subscription-key=test-key "
				+ "while reading /Users/someone/secret/audio.wav (Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig)";

		startRecognition();
		this.session.emitCanceled(CancellationEvent.error("Error", "AuthenticationFailure", poisoned));

		AzureSpeechTranscriptionException failure = failureOf(call);

		assertThat(renderWholeChain(failure)).doesNotContain("test-key")
			.doesNotContain("subscription-key")
			.doesNotContain("/Users/someone/secret/audio.wav")
			.doesNotContain("eyJhbGciOiJIUzI1NiJ9")
			.doesNotContain("Bearer ");
		assertThat(failure.getErrorCode()).isEqualTo("AuthenticationFailure");
	}

	@Test
	void aConfigFactoryFailureCarryingTheKeyIsReducedToItsType() {
		AzureSpeechTranscriptionModel model = modelWithFactory(() -> {
			throw new IllegalStateException(
					"cannot build SpeechConfig for key test-key in westus from /etc/azure/secret.json");
		});

		AzureSpeechTranscriptionException failure = failureOf(callAsync(model));

		assertThat(failure.getMessage()).contains("java.lang.IllegalStateException");
		assertThat(renderWholeChain(failure)).doesNotContain("test-key")
			.doesNotContain("westus")
			.doesNotContain("/etc/azure/secret.json");
	}

	@Test
	void aFactoryFailureOfAPublicModelTypeIsAlsoReducedToItsType() {
		// The factory is untrusted whatever it throws: this module's own public exception
		// types are not a sanitization bypass, and neither is a cause or a suppressed
		// throwable hanging off one.
		AzureSpeechTranscriptionException poisoned = new AzureSpeechTranscriptionException(
				"cannot build SpeechConfig: key=test-key in /etc/azure/secret.json",
				new IllegalStateException("Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig"));
		poisoned.addSuppressed(
				new IllegalStateException("https://westus.stt.speech.microsoft.com/?subscription-key=test-key"));

		AzureSpeechTranscriptionException failure = failureOf(callAsync(modelWithFactory(() -> {
			throw poisoned;
		})));

		assertThat(failure).isNotSameAs(poisoned);
		assertThat(failure.getMessage()).contains("configuration factory failed")
			.contains(AzureSpeechTranscriptionException.class.getName());
		assertThat(failure.getCause()).isNull();
		assertThat(renderWholeChain(failure)).doesNotContain("test-key")
			.doesNotContain("subscription-key")
			.doesNotContain("/etc/azure/secret.json")
			.doesNotContain("eyJhbGciOiJIUzI1NiJ9")
			.doesNotContain("Bearer ");
	}

	@Test
	void aFactoryThatSuppliesNoConfigurationFailsTheCallWithoutOpeningASession() {
		AzureSpeechTranscriptionException failure = failureOf(callAsync(modelWithFactory(() -> null)));

		assertThat(failure.getMessage()).contains("returned no SpeechConfig");
		assertThat(this.session.listener).isNull();
	}

	@Test
	void anUnrequestedCancellationByUserIsAnError() {
		AzureSpeechTranscriptionModel model = model(options().build(), Duration.ofSeconds(5));
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(model);

		startRecognition();
		this.session.emitCanceled(CancellationEvent.cancelledByUser("CancelledByUser"));

		assertThat(failureOf(call).getCancellationReason()).isEqualTo("CancelledByUser");
	}

	@Test
	void anErrorInsideTheCorroborationWindowUpgradesAProvisionalSuccess() {
		AzureSpeechTranscriptionModel model = model(options().build(), Duration.ofSeconds(30));
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(model);

		startRecognition();
		this.session.emitSessionStopped();
		this.session.emitCanceled(CancellationEvent.error("Error", "ServiceTimeout", "upstream gave up"));

		AzureSpeechTranscriptionException failure = failureOf(call);

		assertThat(failure.getErrorCode()).isEqualTo("ServiceTimeout");
		assertThat(this.run.get().getLateEvents()).isZero();
	}

	@Test
	void anErrorArrivingAfterTheWindowClosesIsDroppedAndCounted() throws Exception {
		AzureSpeechTranscriptionModel model = model(options().build(), Duration.ofMillis(5));
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(model);

		startRecognition();
		this.session.emitSegment("kept", 0, 100);
		this.session.emitSessionStopped();
		AudioTranscriptionResponse response = call.get(5, TimeUnit.SECONDS);

		this.session.emitCanceled(CancellationEvent.error("Error", "ServiceTimeout", "too late"));

		assertThat(response.getResult().getOutput()).isEqualTo("kept");
		assertThat(this.run.get().getLateEvents()).isEqualTo(1);
	}

	@Test
	void aSegmentDeliveredAfterTheOutcomeIsDecidedIsDropped() throws Exception {
		AzureSpeechTranscriptionModel model = model(options().build(), Duration.ofMillis(5));
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(model);

		startRecognition();
		this.session.emitSegment("kept", 0, 100);
		this.session.emitSessionStopped();
		AudioTranscriptionResponse response = call.get(5, TimeUnit.SECONDS);

		this.session.emitSegment("dropped", 100, 100);

		assertThat(response.getResult().getOutput()).isEqualTo("kept");
		assertThat(this.run.get().getLateEvents()).isEqualTo(1);
	}

	// ------------------------------------------------------------------ deadline and
	// stop

	@Test
	void aSessionThatNeverEndsTimesOutAndIsStoppedExactlyOnce() {
		AzureSpeechTranscriptionModel model = model(options().timeout(Duration.ofMillis(300)).build(),
				Duration.ofMillis(20));
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(model);

		startRecognition();

		AzureSpeechTranscriptionException failure = failureOf(call);

		assertThat(failure.getMessage()).contains("timed out after");
		awaitUntil(() -> this.session.stopCalls == 1, "the stop to be submitted");
		assertThat(this.session.stopCalls).isEqualTo(1);
	}

	@Test
	void noStopIsRequestedWhenTheServiceSignalledItsOwnTermination() throws Exception {
		AzureSpeechTranscriptionModel model = model(options().build(), Duration.ofMillis(20));
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(model);

		startRecognition();
		this.session.emitSessionStopped();
		call.get(5, TimeUnit.SECONDS);

		assertThat(this.session.stopCalls).isZero();
	}

	@Test
	void theStopReachesTheSdkOnlyAfterTheStartFutureIsTerminal() {
		AzureSpeechTranscriptionModel model = model(options().timeout(Duration.ofMillis(200)).build(),
				Duration.ofMillis(20));
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(model);

		awaitUntil(() -> this.session.startCalls == 1, "the start to be submitted");
		// The deadline fires while the start future is still outstanding.
		failureOf(call);
		assertThat(this.session.stopCalls).isZero();

		this.session.startFuture.complete(null);
		awaitUntil(() -> this.session.stopCalls == 1, "the deferred stop to be submitted");

		assertThat(this.session.calls).containsSubsequence("start", "stop");
	}

	@Test
	void theRecognizerIsNeverClosedWhileAnActionIsStillRunning() throws Exception {
		AzureSpeechTranscriptionModel model = model(options().build(), Duration.ofMillis(20));
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(model);

		startRecognition();
		this.session.emitSessionStopped();
		call.get(5, TimeUnit.SECONDS);

		assertThat(this.session.recognitionRunningAtClose).isFalse();
		assertThat(this.session.calls).containsSubsequence("start", "close:RECOGNIZER");
	}

	// ------------------------------------------------------------------ resources

	@Test
	void everyResourceIsReleasedExactlyOnceBeforeAnOrdinaryCallReturns() throws Exception {
		AzureSpeechTranscriptionModel model = model(options().phrases(List.of("Contoso")).build(),
				Duration.ofMillis(20));
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(model);

		startRecognition();
		this.session.emitSessionStopped();
		call.get(5, TimeUnit.SECONDS);

		assertThat(this.run.get().getPendingResourceKinds()).isEmpty();
		assertThat(this.run.get().getInFlightAcquisitions()).isZero();
		assertThat(this.configFactory.creations).hasValue(1);
		for (ResourceKind kind : List.of(ResourceKind.STREAM_FORMAT, ResourceKind.PULL_STREAM,
				ResourceKind.AUDIO_CONFIG, ResourceKind.RECOGNIZER, ResourceKind.PHRASE_LIST)) {
			assertThat(this.session.closeCount(kind)).as("close count for %s", kind).isEqualTo(1);
		}
	}

	@Test
	void resourcesAreReleasedInStrictReverseAcquisitionOrder() throws Exception {
		AzureSpeechTranscriptionModel model = model(options().build(), Duration.ofMillis(20));
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(model);

		startRecognition();
		this.session.emitSessionStopped();
		call.get(5, TimeUnit.SECONDS);

		assertThat(this.session.calls).containsSubsequence("close:RECOGNIZER", "close:AUDIO_CONFIG",
				"close:PULL_STREAM", "close:STREAM_FORMAT");
	}

	@Test
	void aFailingCloseNeverStrandsTheResourcesBelowIt() throws Exception {
		this.session.recognizerCloseThrows = true;
		AzureSpeechTranscriptionModel model = model(options().build(), Duration.ofMillis(20));
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(model);

		startRecognition();
		this.session.emitSessionStopped();
		call.get(5, TimeUnit.SECONDS);

		assertThat(this.run.get().getPendingResourceKinds()).isEmpty();
		assertThat(this.session.closeCount(ResourceKind.AUDIO_CONFIG)).isEqualTo(1);
		assertThat(this.session.closeCount(ResourceKind.STREAM_FORMAT)).isEqualTo(1);
	}

	@Test
	void aSynchronousStartRejectionFailsTheCallAndStillReleasesEverything() {
		this.session.startThrowsSynchronously = true;
		AzureSpeechTranscriptionModel model = model(options().build(), Duration.ofMillis(20));

		AzureSpeechTranscriptionException failure = failureOf(callAsync(model));

		assertThat(failure.getMessage()).contains("could not be submitted").contains("IllegalStateException");
		assertThat(failure.getMessage()).doesNotContain("test-key");
		awaitUntil(() -> this.session.closeCount(ResourceKind.RECOGNIZER) == 1, "the recognizer to be closed");
	}

	// ------------------------------------------------------------------ audio input

	@Test
	void anUnsupportedHeaderIsRejectedBeforeTheConfigFactoryIsCalled() {
		AzureSpeechTranscriptionModel model = model(options().build(), Duration.ofSeconds(5));

		// Sanitizing owner-step failures must not swallow the caller's own argument
		// error: the audio format verdict is model-built text and is passed through.
		assertThatIllegalArgumentException().isThrownBy(
				() -> model.call(new AudioTranscriptionPrompt(new ByteArrayResource(TestWav.pcm(44100, 2, 16, 64)))))
			.isInstanceOf(AzureSpeechAudioFormatException.class)
			.withMessageContaining("Unsupported audio format");
		assertThat(this.configFactory.creations).hasValue(0);
		assertThat(this.session.calls).isEmpty();
	}

	@Test
	void anExtensibleSubtypeSharingThePcmFormatTagIsRejectedBeforeTheConfigFactoryIsCalled() {
		AzureSpeechTranscriptionModel model = model(options().build(), Duration.ofSeconds(5));
		byte[] vendorSubtype = TestWav.extensible(16000, TestWav.subFormatGuid(0x00000001, 0x72), 64);

		assertThatIllegalArgumentException()
			.isThrownBy(() -> model.call(new AudioTranscriptionPrompt(new ByteArrayResource(vendorSubtype))))
			.isInstanceOf(AzureSpeechAudioFormatException.class)
			.withMessageContaining("extensible subformat 00000001-0000-0010-8000-00aa00389b72");
		assertThat(this.configFactory.creations).hasValue(0);
		assertThat(this.session.calls).isEmpty();
	}

	@Test
	void aPoisonedHeaderReadOfAPublicModelTypeIsReducedToItsType() {
		// The caller's stream is untrusted whatever it throws from a header read: this
		// module's own public exception types are not a sanitization bypass there any
		// more than they are in the configuration factory, and neither is a cause or a
		// suppressed throwable hanging off one.
		AzureSpeechTranscriptionException poisoned = new AzureSpeechTranscriptionException(
				"read failed for /Users/someone/secret/audio.wav?subscription-key=test-key",
				new IllegalStateException("Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig"));
		poisoned.addSuppressed(new IllegalStateException("key=test-key in /etc/azure/secret.json"));
		AzureSpeechTranscriptionModel model = model(options().build(), Duration.ofSeconds(5));

		AzureSpeechTranscriptionException failure = failureOf(callAsync(model, new PoisonedStreamResource(poisoned)));

		assertThat(failure).isNotSameAs(poisoned);
		assertThat(failure.getMessage()).contains("Failed to read the WAV header")
			.contains(AzureSpeechTranscriptionException.class.getName());
		assertThat(failure.getCause()).isNull();
		assertThat(renderWholeChain(failure)).doesNotContain("test-key")
			.doesNotContain("subscription-key")
			.doesNotContain("/Users/someone/secret/audio.wav")
			.doesNotContain("/etc/azure/secret.json")
			.doesNotContain("eyJhbGciOiJIUzI1NiJ9")
			.doesNotContain("Bearer ");
		assertThat(this.configFactory.creations).hasValue(0);
		assertThat(this.session.calls).isEmpty();
	}

	@Test
	void aPoisonedHeaderReadCannotMasqueradeAsTheParsersFormatVerdict() {
		// An audio-format exception is the model's own verdict on content it parsed. A
		// stream that throws one is not making that verdict, so the caller must not be
		// handed it, and must not be handed its free text either.
		AzureSpeechAudioFormatException poisoned = new AzureSpeechAudioFormatException(
				"Unsupported audio format: /etc/azure/secret.json declares subscription-key=test-key");
		AzureSpeechTranscriptionModel model = model(options().build(), Duration.ofSeconds(5));

		AzureSpeechTranscriptionException failure = failureOf(callAsync(model, new PoisonedStreamResource(poisoned)));

		assertThat(failure).isNotSameAs(poisoned);
		assertThat(failure.getMessage()).contains("Failed to read the WAV header")
			.contains(AzureSpeechAudioFormatException.class.getName());
		assertThat(renderWholeChain(failure)).doesNotContain("test-key")
			.doesNotContain("subscription-key")
			.doesNotContain("/etc/azure/secret.json");
		assertThat(this.configFactory.creations).hasValue(0);
		assertThat(this.session.calls).isEmpty();
	}

	@Test
	void anUncheckedHeaderReadFailureStillNamesTheTypeThatCausedIt() {
		AzureSpeechTranscriptionModel model = model(options().build(), Duration.ofSeconds(5));

		AzureSpeechTranscriptionException failure = failureOf(callAsync(model,
				new PoisonedStreamResource(new UncheckedIOException(new IOException("socket closed by peer")))));

		assertThat(failure.getMessage())
			.isEqualTo("Failed to read the WAV header (" + UncheckedIOException.class.getName() + ")");
	}

	@Test
	void aDeclaredPayloadOverTheLimitIsRejectedBeforeAnySdkObjectIsCreated() {
		AzureSpeechTranscriptionModel model = model(options().maxAudioSize(DataSize.ofBytes(64)).build(),
				Duration.ofSeconds(5));

		AzureSpeechTranscriptionException failure = failureOf(
				callAsync(model, new GuardedResource(TestWav.pcm(16000, 1, 16, 320))));

		assertThat(failure.getMessage()).contains("exceeds the configured limit of 64");
		assertThat(this.configFactory.creations).hasValue(0);
		assertThat(this.session.calls).isEmpty();
	}

	@Test
	void theSizePrecheckUsesOnlyTheHeaderAndOpensTheResourceOnce() {
		GuardedResource resource = new GuardedResource(TestWav.pcm(16000, 1, 16, 320));
		AzureSpeechTranscriptionModel model = model(options().maxAudioSize(DataSize.ofBytes(64)).build(),
				Duration.ofSeconds(5));

		failureOf(callAsync(model, resource));

		assertThat(resource.opens).hasValue(1);
	}

	@Test
	void anUndeclaredPayloadOverTheLimitFailsTheSessionFromOffTheCallbackThread() throws Exception {
		AzureSpeechTranscriptionModel model = model(
				options().maxAudioSize(DataSize.ofBytes(16)).timeout(Duration.ofSeconds(5)).build(),
				Duration.ofMillis(20));
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(model,
				new ByteArrayResource(TestWav.pcmWithDeclaredLength(16000, 0L, 320)));

		startRecognition();
		awaitUntil(() -> this.session.source != null, "the pull stream to be created");

		PcmAudioSource pullSource = this.session.source;
		AtomicInteger bytesServed = new AtomicInteger(-1);
		Thread pullThread = new Thread(() -> bytesServed.set(pullSource.read(new byte[64])), "fake-pull-callback");
		pullThread.start();
		pullThread.join(5000);
		assertThat(bytesServed).hasValue(0);

		awaitUntil(() -> this.session.stopCalls == 1, "the deferred stop to be submitted");
		this.session.stopFuture.complete(null);

		AzureSpeechTranscriptionException failure = failureOf(call);

		assertThat(failure.getMessage()).contains("exceeds the configured limit of 16");
		assertThat(this.session.stopCallThread).isNotSameAs(pullThread);
		assertThat(this.session.stopCalls).isEqualTo(1);
	}

	// ------------------------------------------------------------------ continuous
	// language identification

	@Test
	void continuousLanguageIdOverARegionConfigurationIsRejectedBeforeASessionIsOpened() {
		AtomicReference<SpeechConfig> created = new AtomicReference<>();
		AzureSpeechTranscriptionModel model = modelWithFactory(() -> {
			SpeechConfig speechConfig = SpeechConfig.fromSubscription("test-key", "westus");
			created.set(speechConfig);
			return speechConfig;
		}, continuousLanguageId().build());

		Throwable failure = catchThrowable(() -> model.call(new AudioTranscriptionPrompt(new ByteArrayResource(WAV))));

		assertThat(failure).isInstanceOf(AzureSpeechConfigurationException.class)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Continuous language identification requires a SpeechConfig created from an endpoint")
			.hasMessageContaining("spring.ai.azure.speech.endpoint")
			.hasMessageContaining("AzureSpeechConfigFactory");
		// Nothing was opened on top of the rejected configuration.
		assertThat(this.session.listener).isNull();
		assertThat(this.session.calls).isEmpty();
		// The configuration this operation owned was released, exactly once: a second
		// close would have been reported as a suppressed cleanup failure.
		assertThat(failure.getSuppressed()).isEmpty();
		assertThatThrownBy(() -> created.get().getProperty(PropertyId.SpeechServiceConnection_Endpoint))
			.as("the SDK rejects property access once the configuration is closed")
			.isNotNull();
	}

	@Test
	void continuousLanguageIdOverAnEndpointConfigurationProceeds() throws Exception {
		AzureSpeechTranscriptionModel model = modelWithFactory(
				() -> SpeechConfig
					.fromEndpoint(URI.create("wss://westus.stt.speech.microsoft.com/speech/universal/v2")),
				continuousLanguageId().build());
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(model);

		startRecognition();
		this.session.emitSegment("bonjour", 0, 100);
		this.session.emitSessionStopped();

		assertThat(call.get(5, TimeUnit.SECONDS).getResult().getOutput()).isEqualTo("bonjour");
		assertThat(this.session.calls).contains("create:LID_CONFIG", "create:RECOGNIZER");
	}

	@Test
	void atStartLanguageIdNeedsNoEndpointAndProceeds() throws Exception {
		AzureSpeechTranscriptionModel model = model(options().candidateLanguages(List.of("en-US", "fr-FR"))
			.languageIdMode(AzureSpeechAudioTranscriptionOptions.LanguageIdMode.AT_START)
			.build(), Duration.ofMillis(20));
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(model);

		startRecognition();
		this.session.emitSegment("hello", 0, 100);
		this.session.emitSessionStopped();

		assertThat(call.get(5, TimeUnit.SECONDS).getResult().getOutput()).isEqualTo("hello");
		assertThat(this.configFactory.creations).hasValue(1);
	}

	// ------------------------------------------------------------------ observation and
	// secrets

	@Test
	void theObservationCarriesTheProviderAndModelAndNoStreamTag() throws Exception {
		AzureSpeechTranscriptionModel model = model(options().model("my-model").build(), Duration.ofMillis(20));
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(model);

		startRecognition();
		this.session.emitSessionStopped();
		AudioTranscriptionResponse response = call.get(5, TimeUnit.SECONDS);

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultTranscriptionModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_PROVIDER.asString(), AiProvider.AZURE_SPEECH.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.REQUEST_MODEL.asString(), "my-model")
			.doesNotHaveHighCardinalityKeyValueWithKey(HighCardinalityKeyNames.REQUEST_STREAM.asString())
			.hasBeenStarted()
			.hasBeenStopped();
		assertThat(response).isNotNull();
	}

	@Test
	void anErrorIsRecordedOnTheObservation() {
		AzureSpeechTranscriptionModel model = model(options().build(), Duration.ofSeconds(5));
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(model);

		startRecognition();
		this.session.emitCanceled(CancellationEvent.error("Error", "Forbidden", "denied"));
		failureOf(call);

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo(DefaultTranscriptionModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasBeenStarted()
			.hasBeenStopped();
	}

	@Test
	void neitherMetadataNorAnObservationEverCarriesTheConfiguredKey() throws Exception {
		AzureSpeechTranscriptionModel model = model(options().endpointId("deployment-guid").build(),
				Duration.ofMillis(20));
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(model);

		startRecognition();
		this.session.emitSegment("hello", 0, 100);
		this.session.emitSessionStopped();
		AudioTranscriptionResponse response = call.get(5, TimeUnit.SECONDS);

		String rendered = response.getMetadata().entrySet().toString() + response.getResult();
		assertThat(rendered).doesNotContain("test-key").doesNotContain("westus");
		assertThat((Object) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.ENDPOINT_ID))
			.isEqualTo("deployment-guid");
	}

	// ------------------------------------------------------------------ helpers

	private AzureSpeechAudioTranscriptionOptions.Builder options() {
		return AzureSpeechAudioTranscriptionOptions.builder().timeout(Duration.ofSeconds(5));
	}

	private AzureSpeechTranscriptionModel model(AzureSpeechAudioTranscriptionOptions defaults,
			Duration corroborationWindow) {
		return AzureSpeechTranscriptionModel.builder()
			.speechConfigFactory(this.configFactory)
			.defaultOptions(defaults)
			.observationRegistry(this.observationRegistry)
			.sessionFactory(this.session)
			.ownerExecutor(this.ownerExecutor)
			.cleanupExecutor(this.cleanupExecutor)
			.corroborationWindow(corroborationWindow)
			.runObserver(this.run::set)
			.build();
	}

	private AzureSpeechAudioTranscriptionOptions.Builder continuousLanguageId() {
		return options().candidateLanguages(List.of("en-US", "fr-FR"))
			.languageIdMode(AzureSpeechAudioTranscriptionOptions.LanguageIdMode.CONTINUOUS);
	}

	private AzureSpeechTranscriptionModel modelWithFactory(AzureSpeechConfigFactory factory) {
		return modelWithFactory(factory, options().build());
	}

	private AzureSpeechTranscriptionModel modelWithFactory(AzureSpeechConfigFactory factory,
			AzureSpeechAudioTranscriptionOptions defaults) {
		return AzureSpeechTranscriptionModel.builder()
			.speechConfigFactory(factory)
			.defaultOptions(defaults)
			.observationRegistry(this.observationRegistry)
			.sessionFactory(this.session)
			.ownerExecutor(this.ownerExecutor)
			.cleanupExecutor(this.cleanupExecutor)
			.corroborationWindow(Duration.ofMillis(20))
			.runObserver(this.run::set)
			.build();
	}

	private CompletableFuture<AudioTranscriptionResponse> callAsync(AzureSpeechTranscriptionModel model) {
		return callAsync(model, new ByteArrayResource(WAV));
	}

	private CompletableFuture<AudioTranscriptionResponse> callAsync(AzureSpeechTranscriptionModel model,
			Resource audio) {
		return CompletableFuture.supplyAsync(() -> model.call(new AudioTranscriptionPrompt(audio)));
	}

	/**
	 * Waits until the session is open and the start action has reached the SDK, then
	 * completes the start future, which is what the SDK does once the native call
	 * returns.
	 */
	private void startRecognition() {
		awaitUntil(() -> this.session.startCalls == 1, "the start to be submitted");
		this.session.startFuture.complete(null);
		awaitUntil(() -> !this.session.recognitionRunning, "the start future to settle");
	}

	private static AzureSpeechTranscriptionException failureOf(CompletableFuture<AudioTranscriptionResponse> call) {
		try {
			call.get(10, TimeUnit.SECONDS);
		}
		catch (ExecutionException ex) {
			Throwable cause = ex.getCause();
			if (cause instanceof AzureSpeechTranscriptionException failure) {
				return failure;
			}
			throw new AssertionError("expected an AzureSpeechTranscriptionException but got " + cause, cause);
		}
		catch (InterruptedException | TimeoutException ex) {
			throw new AssertionError(ex);
		}
		throw new AssertionError("expected the call to fail");
	}

	/**
	 * Renders a throwable, every cause and every suppressed throwable, messages and stack
	 * traces alike, so an assertion can scan the whole chain for a secret.
	 */
	private static String renderWholeChain(Throwable throwable) {
		StringBuilder rendered = new StringBuilder();
		Deque<Throwable> pending = new ArrayDeque<>(List.of(throwable));
		Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		while (!pending.isEmpty()) {
			Throwable current = pending.pop();
			if (!seen.add(current)) {
				continue;
			}
			StringWriter writer = new StringWriter();
			current.printStackTrace(new PrintWriter(writer));
			rendered.append(writer);
			if (current.getCause() != null) {
				pending.push(current.getCause());
			}
			Collections.addAll(pending, current.getSuppressed());
		}
		return rendered.toString();
	}

	private static void awaitUntil(BooleanSupplier condition, String what) {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < deadline) {
			if (condition.getAsBoolean()) {
				return;
			}
			Thread.onSpinWait();
		}
		throw new AssertionError("timed out waiting for " + what);
	}

	private static final class CountingConfigFactory implements AzureSpeechConfigFactory {

		private final AtomicInteger creations = new AtomicInteger();

		@Override
		public SpeechConfig create() {
			this.creations.incrementAndGet();
			return SpeechConfig.fromSubscription("test-key", "westus");
		}

	}

	/**
	 * A caller-supplied resource whose stream throws a chosen unchecked exception on the
	 * very first header read, standing in for any {@link Resource} implementation the
	 * model does not control.
	 */
	private static final class PoisonedStreamResource extends ByteArrayResource {

		private final RuntimeException failure;

		PoisonedStreamResource(RuntimeException failure) {
			super(WAV);
			this.failure = failure;
		}

		@Override
		public InputStream getInputStream() {
			return new InputStream() {

				@Override
				public int read() {
					throw PoisonedStreamResource.this.failure;
				}

				@Override
				public int read(byte[] buffer, int offset, int length) {
					throw PoisonedStreamResource.this.failure;
				}

			};
		}

	}

	/**
	 * Fails the test if the model reaches for resource metadata, or opens the audio more
	 * than once.
	 */
	private static final class GuardedResource extends ByteArrayResource {

		private final AtomicInteger opens = new AtomicInteger();

		GuardedResource(byte[] content) {
			super(content);
		}

		@Override
		public InputStream getInputStream() throws IOException {
			if (this.opens.incrementAndGet() > 1) {
				throw new AssertionError("the audio resource must be opened exactly once");
			}
			return super.getInputStream();
		}

		@Override
		public long contentLength() {
			throw new AssertionError("contentLength() must not be called");
		}

		@Override
		public boolean isFile() {
			throw new AssertionError("isFile() must not be called");
		}

		@Override
		public File getFile() throws IOException {
			throw new AssertionError("getFile() must not be called");
		}

	}

}
