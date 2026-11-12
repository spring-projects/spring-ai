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

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import com.microsoft.cognitiveservices.speech.SpeechConfig;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.observation.DefaultTranscriptionModelObservationConvention;
import org.springframework.ai.audio.transcription.observation.TranscriptionModelObservationContext;
import org.springframework.ai.audio.transcription.observation.TranscriptionModelObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.audio.transcription.observation.TranscriptionModelObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for {@link AzureSpeechTranscriptionModel#stream}, driven through the
 * recognition seam so that no SDK recognizer, no native callback, and no service
 * connection is involved, and no test sleeps for a fixed interval.
 *
 * @author Spring AI Contributors
 */
@Timeout(60)
class AzureSpeechTranscriptionModelStreamTests {

	private static final byte[] WAV = TestWav.pcm(16000, 1, 16, 320);

	private static final Duration VERIFY_BUDGET = Duration.ofSeconds(10);

	/**
	 * Loads the SDK's native library once, so that a deadline measured in a test is never
	 * racing a one-off class-initialization cost.
	 */
	@BeforeAll
	static void warmUpSpeechConfigNatives() {
		SpeechConfig.fromSubscription("warm-up", "westus").close();
	}

	private final FakeSpeechSession session = new FakeSpeechSession();

	private final TestObservationRegistry observationRegistry = TestObservationRegistry.create();

	private final AtomicReference<RecognitionRun> run = new AtomicReference<>();

	private final AtomicInteger configCreations = new AtomicInteger();

	private final ExecutorService ownerExecutor = AzureSpeechTranscriptionModel.daemonPool("stream-owner-");

	private final ExecutorService cleanupExecutor = AzureSpeechTranscriptionModel.daemonPool("stream-cleanup-");

	private final ExecutorService publisherExecutor = AzureSpeechTranscriptionModel.daemonPool("stream-publisher-");

	@AfterEach
	void shutDownExecutors() {
		this.ownerExecutor.shutdownNow();
		this.cleanupExecutor.shutdownNow();
		this.publisherExecutor.shutdownNow();
	}

	// ------------------------------------------------------------------ emission

	@Test
	void everyFinalSegmentIsEmittedInOrderCarryingOnlyItsOwnMetadata() {
		AtomicReference<Set<ResourceKind>> pendingAtCompletion = new AtomicReference<>();
		AtomicInteger inFlightAtCompletion = new AtomicInteger(-1);
		Flux<AudioTranscriptionResponse> stream = model(options().build(), Duration.ofMillis(20)).stream(prompt())
			.doOnComplete(() -> {
				pendingAtCompletion.set(this.run.get().getPendingResourceKinds());
				inFlightAtCompletion.set(this.run.get().getInFlightAcquisitions());
			});

		StepVerifier.create(stream).then(() -> {
			startRecognition(this.session);
			this.session.emitSegment("the first", 0, 1000);
			this.session.emitSegment("the second", 1000, 500);
			this.session.emitSessionStopped();
		})
			.assertNext(response -> assertSegment(response, "the first", 0, 1000))
			.assertNext(response -> assertSegment(response, "the second", 1000, 500))
			.expectComplete()
			.verify(VERIFY_BUDGET);

		// The service reported the end of the session itself, so no stop was requested.
		assertThat(this.session.stopCalls).isZero();
		// An ordinary completion carries no residual at the instant it is signaled, not
		// merely once cleanup has caught up with the subscriber.
		assertThat(pendingAtCompletion.get()).isEmpty();
		assertThat(inFlightAtCompletion).hasValue(0);
		assertThat(this.session.closeCount(ResourceKind.RECOGNIZER)).isEqualTo(1);
	}

	@Test
	void segmentTextIsEmittedVerbatimWhileCallKeepsItsOwnSpaceJoinedAggregation() throws Exception {
		List<String> emitted = Collections.synchronizedList(new ArrayList<>());

		StepVerifier
			.create(model(options().build(), Duration.ofMillis(20)).stream(prompt())
				.doOnNext(response -> emitted.add(response.getResult().getOutput())))
			.then(() -> emitTranscript(this.session))
			.expectNextCount(3)
			.expectComplete()
			.verify(VERIFY_BUDGET);

		// Nothing is prefixed, trimmed or rewritten to make the elements concatenate.
		assertThat(emitted).containsExactly("Hello.", "世界。", "Bye.");

		FakeSpeechSession callSession = new FakeSpeechSession();
		AzureSpeechTranscriptionModel callModel = model(callSession, options().build(), Duration.ofMillis(20));
		CompletableFuture<AudioTranscriptionResponse> call = CompletableFuture
			.supplyAsync(() -> callModel.call(prompt()));
		emitTranscript(callSession);

		// call() aggregates with a single space, which no emitted element imitates: the
		// two are documented as not being guaranteed to agree.
		assertThat(call.get(10, TimeUnit.SECONDS).getResult().getOutput()).isEqualTo("Hello. 世界。 Bye.");
		assertThat(String.join("", emitted)).isNotEqualTo("Hello. 世界。 Bye.");
	}

	@Test
	void aSegmentArrivingAfterTheOutcomeIsDecidedIsNeverEmitted() {
		StepVerifier.create(model(options().build(), Duration.ofMillis(5)).stream(prompt())).then(() -> {
			startRecognition(this.session);
			this.session.emitSegment("in time", 0, 100);
			this.session.emitSessionStopped();
			awaitUntil(() -> this.run.get().isDecided(), "the corroboration window to close");
			this.session.emitSegment("too late", 100, 100);
		})
			.assertNext(response -> assertThat(response.getResult().getOutput()).isEqualTo("in time"))
			.expectComplete()
			.verify(VERIFY_BUDGET);

		assertThat(this.run.get().getLateEvents()).isEqualTo(1);
	}

	@Test
	void anElementIsNeverDeliveredOnTheThreadThatHandedItOver() {
		Recorder recorder = new Recorder();
		subscribe(model(options().build(), Duration.ofMillis(20)).stream(prompt()), recorder);
		String handingOverThread = Thread.currentThread().getName();

		// This thread stands in for the SDK callback thread, which hands segments over
		// while the run's lock is held and must never run anything downstream.
		startRecognition(this.session);
		this.session.emitSegment("hello", 0, 100);
		awaitUntil(() -> recorder.texts.size() == 1, "the segment to be delivered");

		assertThat(recorder.deliveryThreads).isNotEmpty().doesNotContain(handingOverThread);

		this.session.emitSessionStopped();
		awaitUntil(recorder.completed::get, "the stream to complete");
		assertThat(recorder.error.get()).isNull();
	}

	// ------------------------------------------------------------------ failures

	@Test
	void anErrorCancellationEndsTheStreamAfterTheSegmentsItAlreadyEmitted() {
		StepVerifier.create(model(options().build(), Duration.ofMillis(20)).stream(prompt())).then(() -> {
			startRecognition(this.session);
			this.session.emitSegment("partial", 0, 100);
			this.session.emitCanceled(
					CancellationEvent.error("Error", "AuthenticationFailure", "WebSocket 401 for key test-key"));
		})
			.assertNext(response -> assertThat(response.getResult().getOutput()).isEqualTo("partial"))
			.expectErrorSatisfies(error -> assertThat(error).isInstanceOf(AzureSpeechTranscriptionException.class)
				.hasMessageContaining("reason=Error")
				.hasMessageContaining("errorCode=AuthenticationFailure")
				.hasMessageNotContaining("WebSocket 401")
				.hasMessageNotContaining("test-key"))
			.verify(VERIFY_BUDGET);
	}

	@Test
	void aSessionThatNeverEndsFailsTheStreamWithinTheConfiguredTimeout() {
		StepVerifier
			.create(model(options().timeout(Duration.ofSeconds(1)).build(), Duration.ofMillis(20)).stream(prompt()))
			.then(() -> startRecognition(this.session))
			.expectErrorSatisfies(error -> assertThat(error).isInstanceOf(AzureSpeechTranscriptionException.class)
				.hasMessageContaining("timed out after"))
			.verify(VERIFY_BUDGET);

		// The deadline asked the recognizer to stop, and cleanup followed it.
		awaitUntil(() -> this.session.stopCalls == 1, "the stop to reach the SDK");
		this.session.stopFuture.complete(null);
		awaitUntil(() -> this.session.closeCount(ResourceKind.RECOGNIZER) == 1, "the recognizer to be closed");
	}

	@Test
	void anInvalidOptionCombinationFailsTheStreamRatherThanTheSubscription() {
		AzureSpeechAudioTranscriptionOptions invalid = AzureSpeechAudioTranscriptionOptions.builder()
			.language("en-US")
			.candidateLanguages(List.of("en-US", "fr-FR"))
			.build();

		StepVerifier
			.create(model(options().build(), Duration.ofMillis(20))
				.stream(new AudioTranscriptionPrompt(new ByteArrayResource(WAV), invalid)))
			.expectErrorSatisfies(error -> assertThat(error).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("mutually exclusive"))
			.verify(VERIFY_BUDGET);

		assertThat(this.configCreations).hasValue(0);
		assertThat(this.session.calls).isEmpty();
		assertThat(this.run.get()).isNull();
	}

	@Test
	void aRejectedPublisherSubmissionFailsTheStreamBeforeAnythingIsAcquired() {
		this.publisherExecutor.shutdownNow();

		StepVerifier.create(model(options().build(), Duration.ofMillis(20)).stream(prompt()))
			.expectErrorSatisfies(error -> assertThat(error).isInstanceOf(AzureSpeechTranscriptionException.class)
				.hasMessageContaining("could not be started")
				.hasMessageContaining(RejectedExecutionException.class.getName()))
			.verify(VERIFY_BUDGET);

		assertThat(this.configCreations).hasValue(0);
		assertThat(this.session.calls).isEmpty();
	}

	@Test
	void aRejectedOwnerSubmissionIsPublishedThroughTheWaitingPublisher() {
		this.ownerExecutor.shutdownNow();

		StepVerifier.create(model(options().build(), Duration.ofMillis(20)).stream(prompt()))
			.expectErrorSatisfies(error -> assertThat(error).isInstanceOf(AzureSpeechTranscriptionException.class)
				.hasMessageContaining("could not be started")
				.hasMessageContaining(RejectedExecutionException.class.getName()))
			.verify(VERIFY_BUDGET);

		assertThat(this.configCreations).hasValue(0);
		assertThat(this.run.get().isPublished()).isTrue();
	}

	@Test
	void aPayloadOverTheConfiguredLimitEndsTheStreamAfterWhatItAlreadyEmitted() throws Exception {
		Recorder recorder = new Recorder();
		AzureSpeechTranscriptionModel model = model(options().maxAudioSize(DataSize.ofBytes(16)).build(),
				Duration.ofMillis(20));
		subscribe(model
			.stream(new AudioTranscriptionPrompt(new ByteArrayResource(TestWav.pcmWithDeclaredLength(16000, 0L, 320)))),
				recorder);

		startRecognition(this.session);
		awaitUntil(() -> this.session.source != null, "the pull stream to be created");
		this.session.emitSegment("before the limit", 0, 100);
		awaitUntil(() -> recorder.texts.size() == 1, "the first segment to be delivered");

		PcmAudioSource pullSource = this.session.source;
		Thread pullThread = new Thread(() -> pullSource.read(new byte[64]), "fake-pull-callback");
		pullThread.start();
		pullThread.join(5000);

		awaitUntil(() -> this.session.stopCalls == 1, "the deferred stop to be submitted");
		this.session.stopFuture.complete(null);
		awaitUntil(() -> recorder.error.get() != null, "the stream to fail");

		assertThat(recorder.error.get()).isInstanceOf(AzureSpeechTranscriptionException.class)
			.hasMessageContaining("exceeds the configured limit of 16");
		// What the session produced before the payload failure is not retracted.
		assertThat(recorder.texts).containsExactly("before the limit");
		assertThat(recorder.completed).isFalse();
		// The stop was dispatched off the SDK's own reader thread, as it is for call().
		assertThat(this.session.stopCallThread).isNotSameAs(pullThread);
	}

	@Test
	void eachSubscriptionRunsAndReleasesItsOwnSession() {
		Flux<AudioTranscriptionResponse> stream = model(options().build(), Duration.ofMillis(20)).stream(prompt());

		StepVerifier.create(stream).then(() -> {
			startRecognition(this.session);
			this.session.emitSegment("first subscription", 0, 100);
			this.session.emitSessionStopped();
		}).expectNextCount(1).expectComplete().verify(VERIFY_BUDGET);
		RecognitionRun first = this.run.get();

		StepVerifier.create(stream).then(() -> {
			awaitUntil(() -> this.session.startCalls == 2, "the second start to be submitted");
			this.session.emitSegment("second subscription", 0, 100);
			this.session.emitSessionStopped();
		}).expectNextCount(1).expectComplete().verify(VERIFY_BUDGET);

		// A cold publisher: each subscription acquires, owns and releases its own set.
		assertThat(this.run.get()).isNotSameAs(first);
		assertThat(this.configCreations).hasValue(2);
		assertThat(this.session.closeCount(ResourceKind.RECOGNIZER)).isEqualTo(2);
		assertThat(this.session.closeCount(ResourceKind.AUDIO_CONFIG)).isEqualTo(2);
	}

	// ------------------------------------------------------------------ cancellation

	@Test
	void aCancellationStopsTheRecognizerReleasesEverythingAndSignalsNothingFurther() throws Exception {
		Recorder recorder = new Recorder();
		Disposable subscription = subscribe(
				model(options().timeout(Duration.ofSeconds(30)).build(), Duration.ofMillis(20)).stream(prompt()),
				recorder);

		startRecognition(this.session);
		this.session.emitSegment("heard", 0, 100);
		awaitUntil(() -> recorder.texts.size() == 1, "the first segment to be delivered");

		subscription.dispose();

		awaitUntil(() -> this.session.stopCalls == 1, "the stop to reach the SDK");
		this.session.stopFuture.complete(null);
		awaitUntil(() -> this.session.closeCount(ResourceKind.RECOGNIZER) == 1, "the recognizer to be closed");
		assertThat(this.run.get().awaitFullyAccounted(5000)).isTrue();

		assertThat(this.session.stopCalls).isEqualTo(1);
		assertThat(this.session.closeCount(ResourceKind.AUDIO_CONFIG)).isEqualTo(1);
		assertThat(this.session.closeCount(ResourceKind.PULL_STREAM)).isEqualTo(1);
		assertThat(this.session.closeCount(ResourceKind.STREAM_FORMAT)).isEqualTo(1);
		assertThat(this.session.recognitionRunningAtClose).isFalse();
		// What was delivered before the cancellation stands; nothing follows it.
		assertThat(recorder.texts).containsExactly("heard");
		recorder.assertNoTerminalSignal();
	}

	@Test
	void aCancellationWhileTheHeaderIsBeingReadLeavesOnlyTheInFlightResidual() throws Exception {
		BlockedHeaderResource audio = new BlockedHeaderResource();
		Recorder recorder = new Recorder();
		Disposable subscription = subscribe(
				model(options().timeout(Duration.ofSeconds(30)).build(), Duration.ofMillis(20))
					.stream(new AudioTranscriptionPrompt(audio)),
				recorder);

		assertThat(audio.reading.await(10, TimeUnit.SECONDS)).isTrue();
		// The audio source is the only resource that exists, and reading its header is
		// the only work in flight.
		assertThat(this.run.get().getPendingResourceKinds()).isEqualTo(EnumSet.of(ResourceKind.SOURCE));
		assertThat(this.run.get().getInFlightAcquisitions()).isEqualTo(1);

		subscription.dispose();

		awaitUntil(() -> this.run.get().isPublished(), "the cancellation to be published");
		awaitUntil(() -> this.run.get().getPendingResourceKinds().isEmpty(), "the audio source to be closed");

		// Cleanup closed the source out from under the blocked read, so the read is the
		// whole residual and is reported on its own.
		assertThat(this.run.get().getInFlightAcquisitions()).isEqualTo(1);
		assertThat(audio.closes).hasValue(1);
		// Nothing was built on top of a header that never parsed, so nothing was stopped.
		assertThat(this.configCreations).hasValue(0);
		assertThat(this.session.calls).isEmpty();
		assertThat(this.session.startCalls).isZero();
		assertThat(this.session.stopCalls).isZero();
		recorder.assertNoTerminalSignal();

		audio.release.countDown();

		awaitUntil(() -> this.run.get().getInFlightAcquisitions() == 0, "the blocked read to return");
		assertThat(this.run.get().awaitFullyAccounted(5000)).isTrue();
		assertThat(audio.closes).hasValue(1);
		assertThat(recorder.texts).isEmpty();
		recorder.assertNoTerminalSignal();
	}

	// ------------------------------------------------------------------ observation

	@Test
	void theStreamingObservationCarriesTheStreamTagAndTheWholeTranscript() {
		AtomicReference<AudioTranscriptionResponse> observed = new AtomicReference<>();
		AtomicReference<String> renderedKeyValues = new AtomicReference<>("");
		this.observationRegistry.observationConfig()
			.observationHandler(new ObservationHandler<TranscriptionModelObservationContext>() {

				@Override
				public boolean supportsContext(Observation.Context context) {
					return context instanceof TranscriptionModelObservationContext;
				}

				@Override
				public void onStop(TranscriptionModelObservationContext context) {
					observed.set(context.getResponse());
					renderedKeyValues.set(context.getAllKeyValues().toString());
				}

			});

		StepVerifier.create(model(options().model("my-model").build(), Duration.ofMillis(20)).stream(prompt()))
			.then(() -> emitTranscript(this.session))
			.expectNextCount(3)
			.expectComplete()
			.verify(VERIFY_BUDGET);

		// The handler sees the transcript the whole stream amounted to, aggregated the
		// way call() aggregates it, and not just the last element.
		assertThat(observed.get()).isNotNull();
		assertThat(observed.get().getResult().getOutput()).isEqualTo("Hello. 世界。 Bye.");
		assertThat(renderedKeyValues.get()).doesNotContain("test-key").doesNotContain("westus");
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultTranscriptionModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_PROVIDER.asString(), AiProvider.AZURE_SPEECH.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.REQUEST_MODEL.asString(), "my-model")
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.REQUEST_STREAM.asString(), "true")
			.hasBeenStarted()
			.hasBeenStopped();
	}

	@Test
	void theObservationIsStoppedWhenTheSubscriberCancels() {
		Recorder recorder = new Recorder();
		Disposable subscription = subscribe(
				model(options().timeout(Duration.ofSeconds(30)).build(), Duration.ofMillis(20)).stream(prompt()),
				recorder);

		startRecognition(this.session);
		subscription.dispose();

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultTranscriptionModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.REQUEST_STREAM.asString(), "true")
			.hasBeenStarted()
			.hasBeenStopped();

		awaitUntil(() -> this.session.stopCalls == 1, "the stop to reach the SDK");
		this.session.stopFuture.complete(null);
		awaitUntil(() -> this.session.closeCount(ResourceKind.RECOGNIZER) == 1, "the recognizer to be closed");
		recorder.assertNoTerminalSignal();
	}

	@Test
	void nothingIsAcquiredAndNoObservationIsStartedBeforeASubscriberArrives() {
		Flux<AudioTranscriptionResponse> stream = model(options().timeout(Duration.ofMillis(100)).build(),
				Duration.ofMillis(20))
			.stream(prompt());

		assertThat(stream).isNotNull();
		assertThat(this.run.get()).isNull();
		assertThat(this.configCreations).hasValue(0);
		assertThat(this.session.calls).isEmpty();
		TestObservationRegistryAssert.assertThat(this.observationRegistry).hasNumberOfObservationsEqualTo(0);
	}

	// ------------------------------------------------------------------ helpers

	private static void assertSegment(AudioTranscriptionResponse response, String text, long offsetMillis,
			long durationMillis) {
		assertThat(response.getResult().getOutput()).isEqualTo(text);
		AzureSpeechAudioTranscriptionMetadata metadata = (AzureSpeechAudioTranscriptionMetadata) response.getResult()
			.getMetadata();
		assertThat(metadata.getSegments()).extracting(AzureSpeechSegment::text).containsExactly(text);
		assertThat((Object) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.SEGMENT_COUNT))
			.isEqualTo(1);
		assertThat((Object) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.AUDIO_OFFSET))
			.isEqualTo(Duration.ofMillis(offsetMillis));
		assertThat((Object) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.AUDIO_DURATION))
			.isEqualTo(Duration.ofMillis(durationMillis));
		assertThat((Object) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.PROVIDER))
			.isEqualTo(AiProvider.AZURE_SPEECH.value());
	}

	private static AudioTranscriptionPrompt prompt() {
		return new AudioTranscriptionPrompt(new ByteArrayResource(WAV));
	}

	private AzureSpeechAudioTranscriptionOptions.Builder options() {
		return AzureSpeechAudioTranscriptionOptions.builder().timeout(Duration.ofSeconds(5));
	}

	private AzureSpeechTranscriptionModel model(AzureSpeechAudioTranscriptionOptions defaults,
			Duration corroborationWindow) {
		return model(this.session, defaults, corroborationWindow);
	}

	private AzureSpeechTranscriptionModel model(FakeSpeechSession session,
			AzureSpeechAudioTranscriptionOptions defaults, Duration corroborationWindow) {
		return AzureSpeechTranscriptionModel.builder().speechConfigFactory(() -> {
			this.configCreations.incrementAndGet();
			return SpeechConfig.fromSubscription("test-key", "westus");
		})
			.defaultOptions(defaults)
			.observationRegistry(this.observationRegistry)
			.sessionFactory(session)
			.ownerExecutor(this.ownerExecutor)
			.cleanupExecutor(this.cleanupExecutor)
			.publisherExecutor(this.publisherExecutor)
			.corroborationWindow(corroborationWindow)
			.runObserver(this.run::set)
			.build();
	}

	private static Disposable subscribe(Flux<AudioTranscriptionResponse> stream, Recorder recorder) {
		return stream.subscribe(response -> {
			recorder.deliveryThreads.add(Thread.currentThread().getName());
			recorder.texts.add(response.getResult().getOutput());
		}, recorder.error::set, () -> recorder.completed.set(true));
	}

	/**
	 * Drives the three-segment transcript both halves of the aggregation contract use.
	 */
	private void emitTranscript(FakeSpeechSession session) {
		startRecognition(session);
		session.emitSegment("Hello.", 0, 500);
		session.emitSegment("世界。", 500, 500);
		session.emitSegment("Bye.", 1000, 500);
		session.emitSessionStopped();
	}

	/**
	 * Waits until the session is open and the start action has reached the SDK, then
	 * completes the start future, which is what the SDK does once the native call
	 * returns.
	 */
	private void startRecognition(FakeSpeechSession session) {
		awaitUntil(() -> session.startCalls == 1, "the start to be submitted");
		session.startFuture.complete(null);
		awaitUntil(() -> !session.recognitionRunning, "the start future to settle");
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

	/**
	 * Records everything one subscriber is given, so a test can assert what it was never
	 * given after it cancelled.
	 */
	private static final class Recorder {

		private final List<String> texts = Collections.synchronizedList(new ArrayList<>());

		private final List<String> deliveryThreads = Collections.synchronizedList(new ArrayList<>());

		private final AtomicReference<Throwable> error = new AtomicReference<>();

		private final AtomicBoolean completed = new AtomicBoolean();

		void assertNoTerminalSignal() {
			assertThat(this.error.get()).isNull();
			assertThat(this.completed).isFalse();
		}

	}

	/**
	 * Audio whose header read blocks until the test releases it, standing in for a source
	 * that is slow or stuck while the operation is aborted around it.
	 */
	private static final class BlockedHeaderResource extends ByteArrayResource {

		private final CountDownLatch reading = new CountDownLatch(1);

		private final CountDownLatch release = new CountDownLatch(1);

		private final AtomicInteger closes = new AtomicInteger();

		BlockedHeaderResource() {
			super(WAV);
		}

		@Override
		public InputStream getInputStream() {
			return new InputStream() {

				@Override
				public int read() throws IOException {
					return blockThenEnd();
				}

				@Override
				public int read(byte[] buffer, int offset, int length) throws IOException {
					return blockThenEnd();
				}

				@Override
				public void close() {
					BlockedHeaderResource.this.closes.incrementAndGet();
				}

			};
		}

		private int blockThenEnd() throws IOException {
			this.reading.countDown();
			try {
				this.release.await();
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IOException("interrupted", ex);
			}
			return -1;
		}

	}

}
