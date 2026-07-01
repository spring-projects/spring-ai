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

package org.springframework.ai.chat.client.advisor.api;

import java.util.List;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.chat.client.advisor.DefaultAroundAdvisorChain;

/**
 * A base interface for advisor chains that can be used to chain multiple advisors
 * together, both for call and stream advisors.
 *
 * @author Thomas Vitale
 * @author Christian Tzolov
 * @since 1.0.0
 */
public interface BaseAdvisorChain extends CallAdvisorChain, StreamAdvisorChain {

	/**
	 * Returns a new {@link Builder} initialized with this chain's advisors and
	 * configuration, allowing it to be selectively modified before building a new chain.
	 *
	 * <p>
	 * Concrete {@link BaseAdvisorChain} classes must override this to return the most
	 * concrete builder implementation.
	 * @return a pre-populated {@link Builder}
	 */
	// TODO: change from default to abstract once all implementations override mutate()
	default Builder<?> mutate() {
		throw new UnsupportedOperationException("mutate() must be overridden to return the most concrete Builder");
	}

	/**
	 * Creates a new {@link Builder} for the default {@link BaseAdvisorChain}
	 * implementation.
	 * @param observationRegistry the observation registry to use
	 * @return a new {@link Builder}
	 */
	static Builder<?> builder(ObservationRegistry observationRegistry) {
		return new DefaultAroundAdvisorChain.Builder(observationRegistry);
	}

	/**
	 * Builder for creating a {@link BaseAdvisorChain} instance.
	 *
	 * @param <B> the concrete builder type, enabling fluent subtype chaining
	 */
	interface Builder<B extends Builder<B>> {

		/**
		 * Adds a single {@link Advisor} to the chain.
		 * @param advisor the advisor to add; must not be null
		 * @return this builder
		 */
		B push(Advisor advisor);

		/**
		 * Adds multiple {@link Advisor} instances to the chain.
		 * @param advisors the advisors to add; must not be null or contain null elements
		 * @return this builder
		 */
		B pushAll(List<? extends Advisor> advisors);

		/**
		 * Builds and returns the {@link BaseAdvisorChain}.
		 * @return the constructed chain
		 */
		BaseAdvisorChain build();

	}

}
