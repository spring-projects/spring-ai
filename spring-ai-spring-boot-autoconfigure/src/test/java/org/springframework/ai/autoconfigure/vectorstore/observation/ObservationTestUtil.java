/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.autoconfigure.vectorstore.observation;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;

import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.vectorstore.observation.DefaultVectorStoreObservationConvention;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */

public class ObservationTestUtil {

	public static void assertObservationRegistry(TestObservationRegistry observationRegistry,
			VectorStoreProvider vectorStoreProvider, VectorStoreObservationContext.Operation operation) {
		TestObservationRegistryAssert.assertThat(observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultVectorStoreObservationConvention.DEFAULT_NAME)
			.that()
			.hasContextualNameEqualTo(vectorStoreProvider.value() + " " + operation.value())
			.hasBeenStarted()
			.hasBeenStopped();
	}

}
