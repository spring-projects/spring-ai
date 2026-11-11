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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.microsoft.cognitiveservices.speech.SpeechConfig;
import org.jspecify.annotations.Nullable;

/**
 * A recognition session that records everything the model does to it, and lets a test
 * decide exactly when each SDK action completes.
 * <p>
 * It stands in for the SDK adapter, so the whole lifecycle can be driven without the
 * Azure Speech recognizer, a service connection, or any sleeping.
 *
 * @author Spring AI Contributors
 */
final class FakeSpeechSession implements RecognitionSessionFactory, RecognitionSessionBuilder {

	final List<String> calls = Collections.synchronizedList(new ArrayList<>());

	final Map<ResourceKind, AtomicInteger> closeCounts = Collections.synchronizedMap(new EnumMap<>(ResourceKind.class));

	final CompletableFuture<Void> startFuture = new CompletableFuture<>();

	final CompletableFuture<Void> stopFuture = new CompletableFuture<>();

	volatile @Nullable RecognitionListener listener;

	volatile @Nullable RecognitionRequest request;

	volatile @Nullable PcmAudioSource source;

	volatile @Nullable Thread stopCallThread;

	volatile boolean startThrowsSynchronously;

	volatile boolean recognizerCloseThrows;

	volatile boolean recognitionRunning;

	volatile boolean recognitionRunningAtClose;

	volatile int startCalls;

	volatile int stopCalls;

	private volatile @Nullable CountDownLatch blockedStopGate;

	/**
	 * Makes the next stop submission block inside the SDK seam until the returned latch
	 * is counted down, so a test can prove which thread is held there.
	 */
	CountDownLatch blockStopSubmission() {
		CountDownLatch gate = new CountDownLatch(1);
		this.blockedStopGate = gate;
		return gate;
	}

	@Override
	public RecognitionSessionBuilder newSession(RecognitionRequest request, RecognitionListener listener) {
		this.request = request;
		this.listener = listener;
		return this;
	}

	@Override
	public AutoCloseable createStreamFormat() {
		return record(ResourceKind.STREAM_FORMAT);
	}

	@Override
	public AutoCloseable createPullStream(PcmAudioSource source) {
		this.source = source;
		return record(ResourceKind.PULL_STREAM);
	}

	@Override
	public AutoCloseable createAudioConfig() {
		return record(ResourceKind.AUDIO_CONFIG);
	}

	@Override
	public AutoCloseable createLanguageIdConfig() {
		return record(ResourceKind.LID_CONFIG);
	}

	@Override
	public RecognizerHandle createRecognizer(SpeechConfig speechConfig) {
		this.calls.add("create:RECOGNIZER");
		return new FakeRecognizer();
	}

	@Override
	public AutoCloseable createPhraseList() {
		return record(ResourceKind.PHRASE_LIST);
	}

	private AutoCloseable record(ResourceKind kind) {
		this.calls.add("create:" + kind);
		return () -> {
			this.closeCounts.computeIfAbsent(kind, key -> new AtomicInteger()).incrementAndGet();
			this.calls.add("close:" + kind);
		};
	}

	int closeCount(ResourceKind kind) {
		AtomicInteger counter = this.closeCounts.get(kind);
		return (counter != null) ? counter.get() : 0;
	}

	/**
	 * Delivers a final recognition result as the SDK would.
	 */
	void emitSegment(String text, long offsetMillis, long durationMillis) {
		requireListener().recognized(new AzureSpeechSegment(text, "result-" + text, Duration.ofMillis(offsetMillis),
				Duration.ofMillis(durationMillis), null, List.of(), null));
	}

	/**
	 * Delivers a final result carrying every optional field the SDK adapter can populate:
	 * a detected language, word timings, and the detailed result JSON.
	 * <p>
	 * The segment is forwarded as given, so this proves what the model does with a
	 * populated segment, not which fields the adapter would have populated. Use
	 * {@link #emitResult} for the latter.
	 */
	void emitDetailedSegment(AzureSpeechSegment segment) {
		requireListener().recognized(segment);
	}

	/**
	 * Delivers a final result the way the SDK adapter does: from the plain values a
	 * recognition result carries, reduced through the same option-conditioned mapping,
	 * using the options the model actually resolved for this request.
	 * <p>
	 * Everything the service would have attached is supplied here regardless of the
	 * options, exactly as a real result carries its JSON property and its detected
	 * language whether or not the request asked for them. Which of those reach the caller
	 * is therefore decided by the options under test, not by the test's own inputs.
	 */
	void emitResult(String text, String resultId, long offsetMillis, long durationMillis, @Nullable String detailedJson,
			@Nullable String detectedLanguage) {
		AzureSpeechAudioTranscriptionOptions options = requireRequest().options();
		requireListener().recognized(AzureSpeechResultMapper.toSegment(text, resultId, Duration.ofMillis(offsetMillis),
				Duration.ofMillis(durationMillis), detailedJson, () -> detectedLanguage,
				options.isLanguageIdentificationEnabled(), options.isWordLevelTimestampsEnabled(),
				options.isDetailedOutput()));
	}

	void emitNoMatch() {
		requireListener().noMatch();
	}

	void emitSessionStopped() {
		requireListener().sessionStopped();
	}

	void emitCanceled(CancellationEvent event) {
		requireListener().canceled(event);
	}

	private RecognitionRequest requireRequest() {
		RecognitionRequest current = this.request;
		if (current == null) {
			throw new IllegalStateException("no session has been opened yet");
		}
		return current;
	}

	private RecognitionListener requireListener() {
		RecognitionListener current = this.listener;
		if (current == null) {
			throw new IllegalStateException("no session has been opened yet");
		}
		return current;
	}

	final class FakeRecognizer implements RecognizerHandle {

		@Override
		public Future<Void> startContinuousRecognition() {
			FakeSpeechSession.this.calls.add("start");
			if (FakeSpeechSession.this.startThrowsSynchronously) {
				FakeSpeechSession.this.startCalls++;
				throw new IllegalStateException("fake start rejected");
			}
			FakeSpeechSession.this.recognitionRunning = true;
			FakeSpeechSession.this.startFuture
				.whenComplete((ignored, error) -> FakeSpeechSession.this.recognitionRunning = false);
			// Counted last, so observing it implies recognition is already marked
			// running and the completion handler is already registered.
			FakeSpeechSession.this.startCalls++;
			return FakeSpeechSession.this.startFuture;
		}

		@Override
		public Future<Void> stopContinuousRecognition() {
			FakeSpeechSession.this.stopCalls++;
			FakeSpeechSession.this.stopCallThread = Thread.currentThread();
			FakeSpeechSession.this.calls.add("stop");
			CountDownLatch gate = FakeSpeechSession.this.blockedStopGate;
			if (gate != null) {
				try {
					gate.await();
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}
			return FakeSpeechSession.this.stopFuture;
		}

		@Override
		public void close() throws Exception {
			FakeSpeechSession.this.recognitionRunningAtClose = FakeSpeechSession.this.recognitionRunning;
			FakeSpeechSession.this.closeCounts.computeIfAbsent(ResourceKind.RECOGNIZER, key -> new AtomicInteger())
				.incrementAndGet();
			FakeSpeechSession.this.calls.add("close:RECOGNIZER");
			if (FakeSpeechSession.this.recognizerCloseThrows) {
				throw new IllegalStateException("fake recognizer close failed");
			}
		}

	}

}
