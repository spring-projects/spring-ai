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

package org.springframework.ai.openai.setup;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.openai.core.http.AsyncStreamResponse;
import com.openai.core.http.StreamResponse;
import io.micrometer.observation.Observation;
import org.jspecify.annotations.Nullable;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Turns the streaming responses of the OpenAI SDK into a {@link Flux} for the OpenAI
 * models.
 * <p>
 * The models stream with the sync client by default:
 * {@link #stream(Supplier, Observation, Scheduler)} sends the request while the model
 * observation is the current one, so the HTTP observation and the {@code traceparent}
 * header it writes become its children. The async client sends the request from
 * {@code ForkJoinPool.commonPool()}, where the current observation is lost, so
 * {@link #streamAsync(Supplier)} is used only for an async client set on a model's
 * builder.
 *
 * @author sajeevKR
 * @since 2.1.0
 */
public final class OpenAiStreams {

	/**
	 * The default scheduler of {@link #stream(Supplier, Observation, Scheduler)}. A
	 * dedicated pool, sized like Reactor's shared {@link Schedulers#boundedElastic()}, so
	 * long-lived streams don't contend with unrelated blocking work elsewhere in the app,
	 * such as tool calls.
	 */
	private static final Scheduler DEFAULT_SCHEDULER = Schedulers.newBoundedElastic(
			Schedulers.DEFAULT_BOUNDED_ELASTIC_SIZE, Schedulers.DEFAULT_BOUNDED_ELASTIC_QUEUESIZE, "openai-stream");

	private OpenAiStreams() {
	}

	/**
	 * Returns the scheduler the OpenAI models stream on unless their builder sets another
	 * one.
	 * @return the default stream scheduler
	 */
	public static Scheduler defaultScheduler() {
		return DEFAULT_SCHEDULER;
	}

	/**
	 * Streams the items of a streaming response of the sync client. The request is sent,
	 * and the response read, on a thread of the given scheduler, which is held for the
	 * duration of the stream. The request is sent while the given observation is the
	 * current one.
	 * <p>
	 * A cancel closes the response: right away if it is being read, or as soon as it
	 * arrives if the request is still being sent. The thread is not interrupted, and an
	 * error caused by the cancel is not reported. The response is also closed when the
	 * stream completes or fails.
	 * @param request sends the request and returns its streaming response
	 * @param observation the observation to make current while the request is sent
	 * @param scheduler the scheduler to send the request and read the response on
	 * @param <T> the type of the streamed items
	 * @return the streamed items
	 */
	public static <T> Flux<T> stream(Supplier<StreamResponse<T>> request, Observation observation,
			Scheduler scheduler) {
		return Flux.create(sink -> {
			PendingResponse pendingResponse = new PendingResponse();
			sink.onCancel(pendingResponse::cancel);
			// Not cancelled with the subscription: interrupting a request in flight
			// would make OkHttp fail its HTTP observation.
			scheduler.schedule(() -> {
				try {
					StreamResponse<T> response;
					try (Observation.Scope ignored = observation.openScope()) {
						response = request.get();
					}
					if (pendingResponse.opened(response)) {
						try (response; Stream<T> items = response.stream()) {
							Iterator<T> iterator = items.iterator();
							while (!pendingResponse.isCancelled() && iterator.hasNext()) {
								sink.next(iterator.next());
							}
						}
					}
					sink.complete();
				}
				catch (Throwable ex) {
					Exceptions.throwIfJvmFatal(ex);
					if (!pendingResponse.isCancelled()) {
						sink.error(ex);
					}
				}
			});
		});
	}

	/**
	 * Streams the items of a streaming response of the async client. The response is
	 * closed when the subscription is cancelled.
	 * @param request sends the request and returns its streaming response
	 * @param <T> the type of the streamed items
	 * @return the streamed items
	 */
	public static <T> Flux<T> streamAsync(Supplier<AsyncStreamResponse<T>> request) {
		return Flux.create(sink -> {
			AsyncStreamResponse<T> response = request.get();
			sink.onDispose(response::close);
			response.subscribe(sink::next).onCompleteFuture().whenComplete((unused, throwable) -> {
				if (throwable != null) {
					sink.error(throwable);
				}
				else {
					sink.complete();
				}
			});
		});
	}

	/**
	 * Closes the response of a stream when the subscriber cancels. Whichever of
	 * {@link #opened} and {@link #cancel} runs second sees the other one and closes the
	 * response, so a response that arrives after the cancel is closed too.
	 */
	private static final class PendingResponse {

		private final AtomicBoolean cancelled = new AtomicBoolean();

		private final AtomicReference<@Nullable StreamResponse<?>> response = new AtomicReference<>();

		/**
		 * Records the opened response.
		 * @return {@code false} if the subscriber already cancelled, in which case the
		 * response is closed
		 */
		boolean opened(StreamResponse<?> response) {
			this.response.set(response);
			if (this.cancelled.get()) {
				response.close();
				return false;
			}
			return true;
		}

		void cancel() {
			this.cancelled.set(true);
			StreamResponse<?> response = this.response.get();
			if (response != null) {
				response.close();
			}
		}

		boolean isCancelled() {
			return this.cancelled.get();
		}

	}

}
