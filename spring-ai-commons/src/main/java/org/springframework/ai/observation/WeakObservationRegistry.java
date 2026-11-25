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
import java.util.Objects;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;

/**
 * {@link ObservationRegistry} that holds its delegate by {@link WeakReference}.
 *
 * <p>
 * Micrometer's {@code OkHttpObservationInterceptor} stores the registry in a strong
 * field. When that interceptor is attached to an HTTP client retained by a SDK
 * {@link java.lang.ref.Cleaner} (or another GC root outside the Spring context), the
 * chain {@code Cleaner -> client -> interceptor -> registry -> handlers ->
 * ApplicationContext} keeps a closed context alive. Delegating through a weak reference
 * lets the context become unreachable after close; subsequent observations no-op via
 * {@link ObservationRegistry#NOOP}. See gh-7455.
 *
 * @author yuzhou
 */
public final class WeakObservationRegistry implements ObservationRegistry {

	private final WeakReference<ObservationRegistry> delegate;

	public WeakObservationRegistry(ObservationRegistry delegate) {
		this.delegate = new WeakReference<>(Objects.requireNonNull(delegate, "delegate"));
	}

	private ObservationRegistry delegate() {
		ObservationRegistry registry = this.delegate.get();
		return registry != null ? registry : ObservationRegistry.NOOP;
	}

	@Override
	public @Nullable Observation getCurrentObservation() {
		return delegate().getCurrentObservation();
	}

	@Override
	public Observation.@Nullable Scope getCurrentObservationScope() {
		return delegate().getCurrentObservationScope();
	}

	@Override
	public void setCurrentObservationScope(Observation.@Nullable Scope current) {
		delegate().setCurrentObservationScope(current);
	}

	@Override
	public ObservationConfig observationConfig() {
		return delegate().observationConfig();
	}

	@Override
	public boolean isNoop() {
		return delegate().isNoop();
	}

}
