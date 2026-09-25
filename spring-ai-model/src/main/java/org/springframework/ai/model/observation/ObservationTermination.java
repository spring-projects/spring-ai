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

package org.springframework.ai.model.observation;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import io.micrometer.observation.Observation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

/**
 * Stops an {@link Observation} when the reactive sequence it wraps ends.
 * <p>
 * The observation is stopped from {@code doOnTerminate}, before the terminal signal
 * reaches downstream operators, so an observation that wraps a nested sequence stops
 * after the observations nested inside it. {@code doFinally} runs after the signal has
 * propagated downstream, which stops the outer observation first. A cancellation stops
 * the observation too, and it is stopped at most once.
 *
 * @author Sangkyoon Nam
 * @since 2.1.0
 */
public final class ObservationTermination {

	private ObservationTermination() {
	}

	/**
	 * Return a transformer that records an error on the observation and stops it when the
	 * sequence completes, errors or is cancelled.
	 * @param observation the observation wrapping the sequence
	 * @param <T> the element type
	 * @return the transformer to pass to {@link Flux#transform(Function)}
	 */
	public static <T> Function<Flux<T>, Flux<T>> stopOnTermination(Observation observation) {
		AtomicBoolean stopped = new AtomicBoolean();
		Runnable stop = () -> {
			if (stopped.compareAndSet(false, true)) {
				observation.stop();
			}
		};
		return flux -> flux.doOnError(observation::error).doOnTerminate(stop).doFinally(signal -> {
			if (signal == SignalType.CANCEL) {
				stop.run();
			}
		});
	}

}
