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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ObservationTermination}.
 */
class ObservationTerminationTests {

	private final List<String> events = new CopyOnWriteArrayList<>();

	private final ObservationRegistry registry = registry();

	private ObservationRegistry registry() {
		ObservationRegistry registry = ObservationRegistry.create();
		registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
			@Override
			public boolean supportsContext(Observation.Context context) {
				return true;
			}

			@Override
			public void onStop(Observation.Context context) {
				this.events.add("stop " + context.getName() + (context.getError() != null ? " error" : ""));
			}

			@Override
			public void onError(Observation.Context context) {
				this.events.add("error " + context.getName());
			}

			private final List<String> events = ObservationTerminationTests.this.events;

		});
		return registry;
	}

	@Test
	void stopsAfterUpstreamCompletionCallbacksAndBeforeDownstreamSeesCompletion() {
		Observation observation = Observation.start("op", this.registry);
		Flux<String> flux = Flux.just("a")
			.doOnComplete(() -> this.events.add("upstream complete"))
			.transform(ObservationTermination.stopOnTermination(observation))
			.doOnComplete(() -> this.events.add("downstream complete"));

		assertThat(flux.collectList().block(Duration.ofSeconds(5))).containsExactly("a");

		assertThat(this.events).containsExactly("upstream complete", "stop op", "downstream complete");
	}

	@Test
	void recordsTheErrorAndStopsBeforeDownstreamSeesIt() {
		Observation observation = Observation.start("op", this.registry);
		Flux<String> flux = Flux.<String>error(new IllegalStateException("boom"))
			.transform(ObservationTermination.stopOnTermination(observation))
			.doOnError(e -> this.events.add("downstream error"));

		assertThatThrownBy(() -> flux.blockLast(Duration.ofSeconds(5))).isInstanceOf(IllegalStateException.class);

		assertThat(this.events).containsExactly("error op", "stop op error", "downstream error");
	}

	@Test
	void stopsOnceWhenCancelledBeforeCompletion() {
		Observation observation = Observation.start("op", this.registry);
		Flux<String> flux = Flux.<String>never().transform(ObservationTermination.stopOnTermination(observation));

		Disposable subscription = flux.subscribe();
		subscription.dispose();

		assertThat(this.events).containsExactly("stop op");
	}

	@Test
	void nestedObservationsStopInnerFirstOnCancel() {
		Observation outer = Observation.start("outer", this.registry);
		Observation inner = Observation.start("inner", this.registry);
		Flux<String> flux = Flux.<String>never()
			.transform(ObservationTermination.stopOnTermination(inner))
			.transform(ObservationTermination.stopOnTermination(outer));

		flux.subscribe().dispose();

		// Cancellation is forwarded upstream before the enclosing observation stops.
		assertThat(this.events).containsExactly("stop inner", "stop outer");
	}

	@Test
	void stopsAtMostOnceWhenCancelledAfterCompletion() {
		Observation observation = Observation.start("op", this.registry);
		AtomicReference<Subscription> subscription = new AtomicReference<>();
		Flux<String> flux = Flux.just("a")
			.transform(ObservationTermination.stopOnTermination(observation))
			.doOnSubscribe(subscription::set)
			.doOnComplete(() -> subscription.get().cancel());

		assertThat(flux.collectList().block(Duration.ofSeconds(5))).containsExactly("a");

		assertThat(this.events).containsExactly("stop op");
	}

	@Test
	void stopsAtMostOnceWhenCancelledAfterAnError() {
		Observation observation = Observation.start("op", this.registry);
		AtomicReference<Subscription> subscription = new AtomicReference<>();
		Flux<String> flux = Flux.<String>error(new IllegalStateException("boom"))
			.transform(ObservationTermination.stopOnTermination(observation))
			.doOnSubscribe(subscription::set)
			.doOnError(e -> subscription.get().cancel());

		assertThatThrownBy(() -> flux.blockLast(Duration.ofSeconds(5))).isInstanceOf(IllegalStateException.class);

		assertThat(this.events).containsExactly("error op", "stop op error");
	}

}
