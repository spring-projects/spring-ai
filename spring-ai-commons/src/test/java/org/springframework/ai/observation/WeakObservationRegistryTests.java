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

package org.springframework.ai.observation;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link WeakObservationRegistry}.
 *
 * @author yuzhou
 */
class WeakObservationRegistryTests {

	@Test
	void rejectsNullDelegate() {
		assertThatThrownBy(() -> new WeakObservationRegistry(null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void delegatesWhileRegistryIsReachable() {
		ObservationRegistry registry = ObservationRegistry.create();
		AtomicInteger starts = new AtomicInteger();
		registry.observationConfig().observationHandler(new ObservationHandler<>() {
			@Override
			public boolean supportsContext(Observation.Context context) {
				return true;
			}

			@Override
			public void onStart(Observation.Context context) {
				starts.incrementAndGet();
			}
		});

		ObservationRegistry wrapper = new WeakObservationRegistry(registry);
		assertThat(wrapper.isNoop()).isFalse();
		Observation.start("weak-registry-test", wrapper).stop();
		assertThat(starts).hasValue(1);
	}

	@Test
	void doesNotPreventCollectionOfTheDelegate() {
		ObservationRegistry registry = ObservationRegistry.create();
		WeakReference<ObservationRegistry> ref = new WeakReference<>(registry);
		ObservationRegistry wrapper = new WeakObservationRegistry(registry);
		registry = null;

		assertThat(awaitCleared(ref)).as("delegate should be collectable once only weakly held").isTrue();
		assertThat(wrapper.isNoop()).isTrue();
	}

	static boolean awaitCleared(WeakReference<?> ref) {
		for (int i = 0; i < 50 && ref.get() != null; i++) {
			System.gc();
			System.runFinalization();
			try {
				Thread.sleep(20);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return ref.get() == null;
	}

}
