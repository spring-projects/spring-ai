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
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import com.microsoft.cognitiveservices.speech.PropertyId;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponseMetadata;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.transcription.observation.DefaultTranscriptionModelObservationConvention;
import org.springframework.ai.audio.transcription.observation.TranscriptionModelObservationContext;
import org.springframework.ai.audio.transcription.observation.TranscriptionModelObservationConvention;
import org.springframework.ai.audio.transcription.observation.TranscriptionModelObservationDocumentation;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@link TranscriptionModel} backed by Azure AI Speech continuous recognition.
 * <p>
 * The audio is streamed to the service rather than uploaded: the model reads the WAV
 * header, confirms the payload is the 16-bit mono PCM the service documents, and serves
 * the samples through a pull stream. Every final result the session produces before the
 * outcome is decided becomes one {@link AzureSpeechSegment}, and {@code call} joins their
 * texts with a single space.
 * <p>
 * {@code stream} runs that same session and emits one response per final segment as the
 * service produces it, each carrying that segment's own text and metadata.
 * <p>
 * Each operation runs its acquisition sequence on its own worker, so the calling thread
 * never waits on a native call and the configured timeout always bounds what the caller
 * sees. Everything the operation creates — the audio stream, the {@link SpeechConfig} the
 * factory handed over, and every SDK object built from it — is closed at most once. A
 * call that runs to its own end accounts for all of it as closed or failed before
 * returning. A call cut short by its deadline, an interrupt or a cancellation publishes
 * without that wait, so a resource whose close is still waiting on a native call stays
 * pending behind it — indefinitely, if that call never returns. See {@link #stream} for
 * what the paths that are cut short leave behind.
 *
 * @author Spring AI Contributors
 * @since 2.1.0
 */
public class AzureSpeechTranscriptionModel implements TranscriptionModel {

	private static final TranscriptionModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultTranscriptionModelObservationConvention();

	private final AzureSpeechConfigFactory speechConfigFactory;

	private final AzureSpeechAudioTranscriptionOptions defaultOptions;

	private final ObservationRegistry observationRegistry;

	private final RecognitionSessionFactory sessionFactory;

	private final Executor ownerExecutor;

	private final Executor cleanupExecutor;

	private final Executor publisherExecutor;

	private final ThreadFactory waiterThreadFactory;

	private final LongSupplier nanoTime;

	private final long corroborationWindowNanos;

	private final @Nullable PauseController pauses;

	private final @Nullable Consumer<RecognitionRun> runObserver;

	private TranscriptionModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	AzureSpeechTranscriptionModel(Builder builder) {
		Assert.notNull(builder.speechConfigFactory, "speechConfigFactory must not be null");
		Assert.notNull(builder.defaultOptions, "defaultOptions must not be null");
		this.speechConfigFactory = builder.speechConfigFactory;
		this.defaultOptions = builder.defaultOptions;
		this.observationRegistry = Objects.requireNonNullElse(builder.observationRegistry, ObservationRegistry.NOOP);
		this.sessionFactory = Objects.requireNonNullElseGet(builder.sessionFactory, SdkRecognitionSessionFactory::new);
		this.ownerExecutor = Objects.requireNonNullElseGet(builder.ownerExecutor,
				() -> daemonPool("azure-speech-owner-"));
		this.cleanupExecutor = Objects.requireNonNullElseGet(builder.cleanupExecutor,
				() -> daemonPool("azure-speech-cleanup-"));
		this.publisherExecutor = Objects.requireNonNullElseGet(builder.publisherExecutor,
				() -> daemonPool("azure-speech-publisher-"));
		this.waiterThreadFactory = Objects.requireNonNullElseGet(builder.waiterThreadFactory,
				() -> daemonThreadFactory("azure-speech-action-waiter-"));
		this.nanoTime = Objects.requireNonNullElse(builder.nanoTime, System::nanoTime);
		this.corroborationWindowNanos = (builder.corroborationWindow != null) ? builder.corroborationWindow.toNanos()
				: Duration.ofMillis(RecognitionRun.DEFAULT_CORROBORATION_WINDOW_MILLIS).toNanos();
		this.pauses = builder.pauses;
		this.runObserver = builder.runObserver;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Creates the executor the model uses for cleanup and for observing recognizer
	 * actions.
	 * <p>
	 * It must reject rather than run a task on the submitting thread: a caller-runs
	 * policy would execute a deferred stop on the SDK's own pull-callback thread, which
	 * is precisely what deferring it avoids. {@code ThreadPoolExecutor}'s default abort
	 * policy gives that guarantee, so {@code execute} either queues the task or throws.
	 * <p>
	 * The pool is unbounded, so a close that blocks in a native call never leaves another
	 * operation's cleanup queued behind it.
	 */
	static ExecutorService daemonPool(String namePrefix) {
		return Executors.newCachedThreadPool(daemonThreadFactory(namePrefix));
	}

	static ThreadFactory daemonThreadFactory(String namePrefix) {
		AtomicLong counter = new AtomicLong();
		return runnable -> {
			Thread thread = new Thread(runnable, namePrefix + counter.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		};
	}

	@Override
	public AudioTranscriptionResponse call(AudioTranscriptionPrompt prompt) {
		Assert.notNull(prompt, "prompt must not be null");
		Resource audioResource = prompt.getInstructions();
		Assert.notNull(audioResource, "audio resource must not be null");

		AzureSpeechAudioTranscriptionOptions options = mergedOptions(prompt);
		AzureSpeechOptionsValidator.validate(options);

		TranscriptionModelObservationContext observationContext = TranscriptionModelObservationContext.builder()
			.prompt(new AudioTranscriptionPrompt(audioResource, options))
			.provider(AiProvider.AZURE_SPEECH.value())
			.build();

		return TranscriptionModelObservationDocumentation.TRANSCRIPTION_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				AudioTranscriptionResponse response = transcribe(audioResource, options);
				observationContext.setResponse(response);
				return response;
			});
	}

	/**
	 * Streams one {@link AudioTranscriptionResponse} per final segment, in recognition
	 * order, from the same continuous recognition session {@link #call} uses.
	 * <p>
	 * Each element carries exactly the text the service recognized for that segment,
	 * along with that one segment's metadata; interim hypotheses are never emitted.
	 * Concatenating the emitted texts is therefore not guaranteed to reproduce what
	 * {@code call} returns: {@code call} aggregates by joining segment texts with a
	 * single space, which suits neither every script nor every punctuation style, and no
	 * segment text is rewritten here to make the two agree. Callers who need another
	 * aggregation have the verbatim segments in
	 * {@link AzureSpeechAudioTranscriptionMetadata#getSegments()}.
	 * <p>
	 * The configured timeout is measured from subscription rather than from this method.
	 * Within it the operation always concludes, however long the SDK takes, and its
	 * terminal signal is then handed to the subscriber as soon as the subscriber's own
	 * demand allows it to be delivered. Only an operation that reaches its own end
	 * publishes with release complete: it waits until nothing is still being acquired and
	 * every resource has settled. A deadline, an interrupt or a cancellation publishes
	 * the outcome already decided — which may be a success the SDK supplied moments
	 * earlier — without that wait, leaving a native call or an audio read that has not
	 * returned to be cleaned up behind it, on the cleanup executor.
	 * <p>
	 * Cancelling the subscription begins releasing what the operation acquired, and asks
	 * the recognizer to stop if one exists and the SDK has not already reported the
	 * session ending; nothing is emitted or signaled to that subscriber afterwards. What
	 * is promised of that release is what every other path promises: each resource is
	 * closed at most once, close is attempted once where attempting it is safe, and a
	 * close that throws is reported rather than retried. The one resource for which no
	 * attempt is made is a recognizer holding an SDK action whose completion no thread
	 * can observe, which the SDK could refuse to close; it is settled as failed instead.
	 * Settling as closed or failed is what an operation publishing at its own end waits
	 * for. A cancelled subscription publishes at once, so it guarantees neither that
	 * every handle has been released nor that every one has settled: a close still
	 * waiting on a native call keeps its resource pending for as long as that call runs,
	 * which is indefinitely if it never returns.
	 * <p>
	 * Segments are handed over on an SDK callback thread and republished on
	 * {@link Schedulers#boundedElastic()}, which the whole application shares. A backing
	 * thread is therefore reused across operations and alongside other work scheduled
	 * there, so a subscriber that blocks in {@code map} or in {@code subscribe} delays
	 * that thread's other users as well.
	 */
	@Override
	public Flux<AudioTranscriptionResponse> stream(AudioTranscriptionPrompt prompt) {
		Assert.notNull(prompt, "prompt must not be null");
		Resource audioResource = prompt.getInstructions();
		Assert.notNull(audioResource, "audio resource must not be null");

		return Flux.deferContextual(contextView -> {
			AzureSpeechAudioTranscriptionOptions options = mergedOptions(prompt);
			AzureSpeechOptionsValidator.validate(options);

			TranscriptionModelObservationContext observationContext = TranscriptionModelObservationContext.builder()
				.prompt(new AudioTranscriptionPrompt(audioResource, options))
				.provider(AiProvider.AZURE_SPEECH.value())
				.streaming(true)
				.build();
			Observation observation = TranscriptionModelObservationDocumentation.TRANSCRIPTION_MODEL_OPERATION
				.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
						this.observationRegistry);
			Observation parentObservation = contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null);
			observation.parentObservation(parentObservation);
			// Started under the parent's scope, as the other streaming models do, so a
			// tracing handler takes the parent span from it and not from the thread.
			try (Observation.Scope ignored = (parentObservation != null) ? parentObservation.openScope()
					: Observation.Scope.NOOP) {
				observation.start();
			}

			// Aggregated exactly as call() aggregates, so a handler reading the
			// observation's response sees the whole transcript rather than one segment.
			StringJoiner transcript = new StringJoiner(" ");
			Flux<AudioTranscriptionResponse> responses = Flux
				.<AzureSpeechSegment>create(sink -> subscribe(audioResource, options, sink))
				// Segments are handed over on an SDK callback thread while the run's
				// lock is held, so the hop to this operation's own worker happens before
				// anything downstream, including the mapping below, can run.
				.publishOn(Schedulers.boundedElastic())
				.map(segment -> {
					transcript.add(segment.text());
					return toResponse(Outcome.success(List.of(segment), 0), options);
				})
				.doOnComplete(() -> observationContext.setResponse(new AudioTranscriptionResponse(
						new AudioTranscription(transcript.toString()), new AudioTranscriptionResponseMetadata())));
			return observed(responses, observation);
		});
	}

	public void setObservationConvention(TranscriptionModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention must not be null");
		this.observationConvention = observationConvention;
	}

	private AzureSpeechAudioTranscriptionOptions mergedOptions(AudioTranscriptionPrompt prompt) {
		return AzureSpeechAudioTranscriptionOptions.builder()
			.from(this.defaultOptions)
			.merge(prompt.getOptions())
			.build();
	}

	private AudioTranscriptionResponse transcribe(Resource audioResource,
			AzureSpeechAudioTranscriptionOptions options) {
		RecognitionRun run = newRun(options, null);
		try {
			this.ownerExecutor.execute(() -> acquireAndStart(audioResource, options, run));
		}
		catch (RejectedExecutionException ex) {
			throw startFailure(ex);
		}

		Outcome outcome = run.awaitOutcome();
		RuntimeException error = outcome.error();
		if (error != null) {
			throw error;
		}
		return toResponse(outcome, options);
	}

	/**
	 * Starts one streaming operation for a new subscriber.
	 * <p>
	 * Two tasks are submitted, and the subscriber's thread is neither of them: the owner
	 * runs the acquisition sequence, which may block inside a native call for as long as
	 * the SDK takes, and the publisher waits for the outcome, which is what enforces the
	 * deadline and delivers the terminal signal. The publisher is submitted first, so a
	 * rejected owner submission always has someone to report it, and it runs on an
	 * executor the model owns rather than the configurable owner executor, which an
	 * application may have given a single thread.
	 */
	private void subscribe(Resource audioResource, AzureSpeechAudioTranscriptionOptions options,
			FluxSink<AzureSpeechSegment> sink) {
		RecognitionRun run = newRun(options, sink::next);
		// Fires on cancellation only, and immediately if this subscription was already
		// cancelled by the time the registration ran.
		sink.onCancel(run::onSubscriberCancel);
		try {
			this.publisherExecutor.execute(() -> publish(run, sink));
		}
		catch (RejectedExecutionException ex) {
			// The owner has not been submitted yet, so nothing was acquired.
			sink.error(startFailure(ex));
			return;
		}
		try {
			this.ownerExecutor.execute(() -> acquireAndStart(audioResource, options, run));
		}
		catch (RejectedExecutionException ex) {
			// The publisher is already waiting, so this failure is decided, cleaned up
			// after and signaled through the one path every other failure uses.
			run.onOwnerFailure(startFailure(ex));
		}
	}

	/**
	 * Waits for the operation's outcome and turns it into the stream's terminal signal.
	 * <p>
	 * Segments reached the sink as they were recognized, so there is nothing left to emit
	 * here. A cancelled subscription needs no special case either: Reactor discards a
	 * terminal signal issued after cancellation, which is what the contract requires.
	 */
	private static void publish(RecognitionRun run, FluxSink<AzureSpeechSegment> sink) {
		Outcome outcome;
		try {
			outcome = run.awaitOutcome();
		}
		catch (RuntimeException ex) {
			sink.error(ex);
			return;
		}
		RuntimeException error = outcome.error();
		if (error != null) {
			sink.error(error);
		}
		else {
			sink.complete();
		}
	}

	/**
	 * Ends the observation with the stream — on completion, error or cancellation, and
	 * exactly once — before the terminal signal reaches the subscriber, unlike
	 * {@code doFinally}, so a caller that blocks on the stream finds it already stopped.
	 */
	private static Flux<AudioTranscriptionResponse> observed(Flux<AudioTranscriptionResponse> responses,
			Observation observation) {
		AtomicBoolean stopped = new AtomicBoolean();
		Runnable stop = () -> {
			if (stopped.compareAndSet(false, true)) {
				observation.stop();
			}
		};
		return responses.doOnError(observation::error)
			.doOnError(ex -> stop.run())
			.doOnComplete(stop)
			.doOnCancel(stop)
			.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
	}

	private RecognitionRun newRun(AzureSpeechAudioTranscriptionOptions options,
			@Nullable Consumer<AzureSpeechSegment> segmentListener) {
		RecognitionRun run = new RecognitionRun(this.cleanupExecutor, this.waiterThreadFactory, this.nanoTime,
				options.resolvedTimeout().toNanos(), this.corroborationWindowNanos, this.pauses, segmentListener);
		if (this.runObserver != null) {
			this.runObserver.accept(run);
		}
		return run;
	}

	private static AzureSpeechTranscriptionException startFailure(RejectedExecutionException ex) {
		return new AzureSpeechTranscriptionException(
				"Azure Speech transcription could not be started (" + ex.getClass().getName() + ")");
	}

	/**
	 * The owner sequence: every blocking call it makes goes through the run, so the
	 * operation always knows whether something is being acquired and always owns whatever
	 * was acquired, including a call still in flight when the outcome was decided.
	 */
	private void acquireAndStart(Resource audioResource, AzureSpeechAudioTranscriptionOptions options,
			RecognitionRun run) {
		try {
			InputStream source = run.registerOrClose(ResourceKind.SOURCE, () -> openStream(audioResource));
			if (source == null) {
				return;
			}
			WavHeader[] header = new WavHeader[1];
			run.trackBlockingStep(() -> header[0] = readHeader(source));
			long maxBytes = options.resolvedMaxAudioSize().toBytes();
			if (header[0].hasDeclaredDataLength() && header[0].declaredDataBytes() > maxBytes) {
				throw AzureSpeechTranscriptionException.audioTooLarge(maxBytes);
			}

			SpeechConfig speechConfig = run.registerOrClose(ResourceKind.SPEECH_CONFIG, this::createSpeechConfig);
			if (speechConfig == null) {
				return;
			}
			requireEndpointForContinuousLanguageId(speechConfig, options);

			RecognitionRequest request = new RecognitionRequest(header[0].sampleRate(), options);
			RecognitionSessionBuilder session = this.sessionFactory.newSession(request, new RunListener(run));

			if (run.registerOrClose(ResourceKind.STREAM_FORMAT, session::createStreamFormat) == null) {
				return;
			}
			// The SDK's own disposal of the pull stream and the model's cleanup chain
			// converge on the audio source's single idempotent close.
			PcmPullSource pullSource = new PcmPullSource(source, header[0], maxBytes, run,
					() -> run.closeRegistered(ResourceKind.SOURCE));
			if (run.registerOrClose(ResourceKind.PULL_STREAM, () -> session.createPullStream(pullSource)) == null) {
				return;
			}
			if (run.registerOrClose(ResourceKind.AUDIO_CONFIG, session::createAudioConfig) == null) {
				return;
			}
			if (options.isLanguageIdentificationEnabled()
					&& run.registerOrClose(ResourceKind.LID_CONFIG, session::createLanguageIdConfig) == null) {
				return;
			}

			RecognizerHandle recognizer = run.registerOrClose(ResourceKind.RECOGNIZER,
					() -> session.createRecognizer(speechConfig));
			if (recognizer == null) {
				return;
			}
			List<String> phrases = options.getPhrases();
			if (phrases != null && !phrases.isEmpty()
					&& run.registerOrClose(ResourceKind.PHRASE_LIST, session::createPhraseList) == null) {
				return;
			}

			run.pauseIfRequested(PausePoint.BEFORE_RESERVE_START);
			RecognizerAction startAction = run.reserveStart();
			if (startAction == null) {
				throw new AzureSpeechAbortedException("Azure Speech recognition was decided before start");
			}
			run.pauseIfRequested(PausePoint.AFTER_RESERVE_START);
			Future<Void> startFuture = run.submitAndWire(recognizer::startContinuousRecognition, startAction);
			run.trackBlockingStep(() -> {
				startFuture.get();
				run.pauseIfRequested(PausePoint.AFTER_START_FUTURE_TERMINAL);
			});
		}
		catch (AzureSpeechAbortedException ex) {
			// The outcome was already decided; whatever was acquired is already
			// scheduled for release.
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			run.onOwnerFailure(AzureSpeechTranscriptionException.interrupted(ex));
		}
		catch (Exception ex) {
			run.onOwnerFailure(toFailure(ex));
		}
	}

	/**
	 * Calls the user-supplied factory and reduces anything it throws to that throwable's
	 * type name.
	 * <p>
	 * Sanitizing here rather than where the failure is finally reported is what makes the
	 * guarantee independent of the exception class: a factory is free to throw this
	 * module's own public exception types, and nothing stops the message, the cause, or a
	 * suppressed throwable of such an instance from repeating the configured key, a
	 * token, or a path. Only the type name crosses this boundary, and no part of the
	 * original throwable graph is attached to what replaces it.
	 */
	private SpeechConfig createSpeechConfig() {
		SpeechConfig speechConfig;
		try {
			speechConfig = this.speechConfigFactory.create();
		}
		catch (Exception ex) {
			throw new AzureSpeechTranscriptionException(
					"The Azure Speech configuration factory failed (" + ex.getClass().getName() + ")");
		}
		if (speechConfig == null) {
			throw new AzureSpeechTranscriptionException(
					"The Azure Speech configuration factory returned no SpeechConfig");
		}
		return speechConfig;
	}

	/**
	 * Rejects continuous language identification over a configuration the SDK cannot run
	 * it on, before a session is opened or a recognizer is created.
	 * <p>
	 * Azure Speech supports continuous language identification only for a
	 * {@link SpeechConfig} created from an endpoint, so a region-based configuration —
	 * what the default factory builds — would otherwise reach an unsupported SDK path
	 * with the mode already set. The configuration is owned by this operation and is
	 * released by the ordinary cleanup chain.
	 */
	private static void requireEndpointForContinuousLanguageId(SpeechConfig speechConfig,
			AzureSpeechAudioTranscriptionOptions options) {
		if (options.getLanguageIdMode() != AzureSpeechAudioTranscriptionOptions.LanguageIdMode.CONTINUOUS) {
			return;
		}
		if (!StringUtils.hasText(speechConfig.getProperty(PropertyId.SpeechServiceConnection_Endpoint))) {
			throw new AzureSpeechConfigurationException(
					"Continuous language identification requires a SpeechConfig created from an endpoint; "
							+ "configure spring.ai.azure.speech.endpoint or return an endpoint-based SpeechConfig "
							+ "from the AzureSpeechConfigFactory");
		}
	}

	private static InputStream openStream(Resource audioResource) {
		try {
			return audioResource.getInputStream();
		}
		catch (IOException | RuntimeException ex) {
			throw AzureSpeechTranscriptionException.audioReadFailure("Failed to open the audio resource",
					ex.getClass().getName());
		}
	}

	/**
	 * Parses the WAV header, with the caller's own stream kept behind
	 * {@link GuardedHeaderSource} for the length of the parse.
	 * <p>
	 * The parser's verdict on malformed content is model-built text and is reported as
	 * the approved {@link AzureSpeechAudioFormatException}. A failure the caller's stream
	 * threw is not: it is reduced to its type name here, before it can be mistaken for
	 * such a verdict.
	 */
	private static WavHeader readHeader(InputStream source) {
		try {
			return WavHeaderReader.read(new GuardedHeaderSource(source));
		}
		catch (GuardedHeaderSource.StreamReadFailure ex) {
			throw AzureSpeechTranscriptionException.audioReadFailure("Failed to read the WAV header",
					ex.underlyingType());
		}
		catch (IOException ex) {
			throw AzureSpeechTranscriptionException.audioReadFailure("Failed to read the WAV header",
					ex.getClass().getName());
		}
	}

	/**
	 * Reduces an owner-step failure to an exception the caller may see.
	 * <p>
	 * Only exceptions this module created itself carry messages that are known to be
	 * built from model-owned text. Everything else, including a {@code RuntimeException}
	 * the SDK threw while a resource was acquired, is reduced to its type name: such a
	 * message can repeat the configured key, a token, or a path, and nothing guarantees
	 * otherwise.
	 * <p>
	 * The types listed here are therefore only ever trusted because every instance that
	 * reaches this method was built by the model. An exception a user-supplied
	 * {@link AzureSpeechConfigFactory} threw never does, whatever its class: it is
	 * sanitized at the boundary, by {@link #createSpeechConfig()}, before it can be
	 * matched against this list. The same holds for an exception a caller-supplied
	 * {@link InputStream} threw while the WAV header was read: it is sanitized by
	 * {@link GuardedHeaderSource}, so only the parser's own format verdicts arrive here
	 * as {@link AzureSpeechAudioFormatException}.
	 */
	private static RuntimeException toFailure(Exception ex) {
		if (ex instanceof AzureSpeechActionSubmissionException submissionFailure) {
			// Already sanitized, but the caller should never see an internal type.
			return new AzureSpeechTranscriptionException(String.valueOf(submissionFailure.getMessage()));
		}
		if (ex instanceof AzureSpeechTranscriptionException || ex instanceof AzureSpeechResourceException
				|| ex instanceof AzureSpeechCleanupException || ex instanceof AzureSpeechAudioFormatException
				|| ex instanceof AzureSpeechConfigurationException) {
			return (RuntimeException) ex;
		}
		if (ex instanceof ExecutionException executionException) {
			Throwable cause = executionException.getCause();
			return new AzureSpeechTranscriptionException("Azure Speech recognition could not be started ("
					+ ((cause != null) ? cause.getClass().getName() : ex.getClass().getName()) + ")");
		}
		return new AzureSpeechTranscriptionException(
				"Azure Speech recognition failed (" + ex.getClass().getName() + ")");
	}

	private AudioTranscriptionResponse toResponse(Outcome outcome, AzureSpeechAudioTranscriptionOptions options) {
		List<AzureSpeechSegment> segments = outcome.segments();
		String text = segments.stream().map(AzureSpeechSegment::text).collect(Collectors.joining(" "));
		AudioTranscription transcription = new AudioTranscription(text)
			.withTranscriptionMetadata(new AzureSpeechAudioTranscriptionMetadata(segments));

		AudioTranscriptionResponseMetadata metadata = new AudioTranscriptionResponseMetadata();
		metadata.put(AzureSpeechAudioTranscriptionMetadata.PROVIDER, AiProvider.AZURE_SPEECH.value());
		metadata.put(AzureSpeechAudioTranscriptionMetadata.MODEL, options.getModel());
		metadata.put(AzureSpeechAudioTranscriptionMetadata.SEGMENT_COUNT, segments.size());
		metadata.put(AzureSpeechAudioTranscriptionMetadata.NO_MATCH_COUNT, outcome.noMatchCount());
		if (!segments.isEmpty()) {
			Duration offset = segments.get(0).offset();
			metadata.put(AzureSpeechAudioTranscriptionMetadata.AUDIO_OFFSET, offset);
			metadata.put(AzureSpeechAudioTranscriptionMetadata.AUDIO_DURATION,
					segments.get(segments.size() - 1).end().minus(offset));
		}
		if (StringUtils.hasText(options.getEndpointId())) {
			metadata.put(AzureSpeechAudioTranscriptionMetadata.ENDPOINT_ID, options.getEndpointId());
		}
		List<String> detectedLanguages = segments.stream()
			.map(AzureSpeechSegment::language)
			.filter(Objects::nonNull)
			.distinct()
			.toList();
		if (!detectedLanguages.isEmpty()) {
			metadata.put(AzureSpeechAudioTranscriptionMetadata.DETECTED_LANGUAGES, detectedLanguages);
		}
		return new AudioTranscriptionResponse(transcription, metadata);
	}

	/**
	 * Keeps the WAV parser's reads of a caller-supplied stream inside the model's
	 * sanitization boundary.
	 * <p>
	 * {@link WavHeaderReader} reads the stream directly, and an {@link InputStream} the
	 * caller provided may throw any unchecked exception — including one of this module's
	 * own public types, whose message, cause, and suppressed throwables would otherwise
	 * be published unchanged as though the parser had produced them. Each read is
	 * therefore reduced here to the thrown type's name and nothing else, exactly as a
	 * failing {@link AzureSpeechConfigFactory} is at {@link #createSpeechConfig()}.
	 * <p>
	 * Only the reads are delegated. The stream itself stays owned by the operation's
	 * {@link ResourceKind#SOURCE} registration, so {@code close()} here is the inherited
	 * no-op and the audio is never closed out from under the payload read.
	 */
	private static final class GuardedHeaderSource extends InputStream {

		private final InputStream delegate;

		GuardedHeaderSource(InputStream delegate) {
			this.delegate = delegate;
		}

		@Override
		public int read() throws IOException {
			try {
				return this.delegate.read();
			}
			catch (RuntimeException ex) {
				throw new StreamReadFailure(ex);
			}
		}

		@Override
		public int read(byte[] buffer, int offset, int length) throws IOException {
			try {
				return this.delegate.read(buffer, offset, length);
			}
			catch (RuntimeException ex) {
				throw new StreamReadFailure(ex);
			}
		}

		@Override
		public long skip(long count) throws IOException {
			try {
				return this.delegate.skip(count);
			}
			catch (RuntimeException ex) {
				throw new StreamReadFailure(ex);
			}
		}

		@Override
		public int available() throws IOException {
			try {
				return this.delegate.available();
			}
			catch (RuntimeException ex) {
				throw new StreamReadFailure(ex);
			}
		}

		/**
		 * The single fact about an unchecked stream failure that may leave the guard: the
		 * name of the type that was thrown.
		 * <p>
		 * The throwable itself is dropped at the point of the read, so no message, cause,
		 * or suppressed throwable the caller's stream wrote can travel with this signal.
		 * The signal carries no message and no stack trace of its own either, because
		 * nothing is meant to report it: {@link #readHeader(InputStream)} translates it
		 * immediately, and it is not a type {@link #toFailure(Exception)} trusts even if
		 * some later path were to route it there.
		 */
		private static final class StreamReadFailure extends RuntimeException {

			private final String underlyingType;

			StreamReadFailure(RuntimeException cause) {
				super(null, null, false, false);
				this.underlyingType = cause.getClass().getName();
			}

			String underlyingType() {
				return this.underlyingType;
			}

		}

	}

	/**
	 * Forwards SDK events to the run. Every method may run on an SDK callback thread, so
	 * none of them blocks or calls back into the SDK.
	 */
	private record RunListener(RecognitionRun run) implements RecognitionListener {

		@Override
		public void recognized(AzureSpeechSegment segment) {
			this.run.onRecognized(segment);
		}

		@Override
		public void noMatch() {
			this.run.onNoMatch();
		}

		@Override
		public void canceled(CancellationEvent event) {
			this.run.onCanceled(event);
		}

		@Override
		public void sessionStopped() {
			this.run.onSessionStopped();
		}

	}

	public static final class Builder {

		private @Nullable AzureSpeechConfigFactory speechConfigFactory;

		private @Nullable AzureSpeechAudioTranscriptionOptions defaultOptions;

		private @Nullable ObservationRegistry observationRegistry;

		private @Nullable RecognitionSessionFactory sessionFactory;

		private @Nullable Executor ownerExecutor;

		private @Nullable Executor cleanupExecutor;

		private @Nullable Executor publisherExecutor;

		private @Nullable ThreadFactory waiterThreadFactory;

		private @Nullable LongSupplier nanoTime;

		private @Nullable Duration corroborationWindow;

		private @Nullable PauseController pauses;

		private @Nullable Consumer<RecognitionRun> runObserver;

		private Builder() {
		}

		/**
		 * Sets the factory that supplies one {@link SpeechConfig} per operation, whose
		 * ownership transfers to the model. Required.
		 */
		public Builder speechConfigFactory(AzureSpeechConfigFactory speechConfigFactory) {
			this.speechConfigFactory = speechConfigFactory;
			return this;
		}

		/**
		 * Sets the options every request inherits from. Required.
		 */
		public Builder defaultOptions(AzureSpeechAudioTranscriptionOptions defaultOptions) {
			this.defaultOptions = defaultOptions;
			return this;
		}

		/**
		 * Sets the observation registry. Defaults to {@link ObservationRegistry#NOOP}.
		 */
		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		/**
		 * Sets the executor that runs each operation's acquisition sequence. Defaults to
		 * an unbounded daemon pool owned by the model.
		 */
		public Builder ownerExecutor(Executor ownerExecutor) {
			this.ownerExecutor = ownerExecutor;
			return this;
		}

		Builder sessionFactory(RecognitionSessionFactory sessionFactory) {
			this.sessionFactory = sessionFactory;
			return this;
		}

		Builder cleanupExecutor(Executor cleanupExecutor) {
			this.cleanupExecutor = cleanupExecutor;
			return this;
		}

		Builder publisherExecutor(Executor publisherExecutor) {
			this.publisherExecutor = publisherExecutor;
			return this;
		}

		Builder waiterThreadFactory(ThreadFactory waiterThreadFactory) {
			this.waiterThreadFactory = waiterThreadFactory;
			return this;
		}

		Builder nanoTime(LongSupplier nanoTime) {
			this.nanoTime = nanoTime;
			return this;
		}

		Builder corroborationWindow(Duration corroborationWindow) {
			this.corroborationWindow = corroborationWindow;
			return this;
		}

		Builder pauses(PauseController pauses) {
			this.pauses = pauses;
			return this;
		}

		Builder runObserver(Consumer<RecognitionRun> runObserver) {
			this.runObserver = runObserver;
			return this;
		}

		public AzureSpeechTranscriptionModel build() {
			return new AzureSpeechTranscriptionModel(this);
		}

	}

}
