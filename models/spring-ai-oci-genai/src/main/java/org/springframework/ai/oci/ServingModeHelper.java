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

package org.springframework.ai.oci;

import com.oracle.bmc.generativeaiinference.model.DedicatedServingMode;
import com.oracle.bmc.generativeaiinference.model.OnDemandServingMode;
import com.oracle.bmc.generativeaiinference.model.ServingMode;

/**
 * Helper class to load the OCI Gen AI
 * {@link com.oracle.bmc.generativeaiinference.model.ServingMode}
 *
 * @author Anders Swanson
 */
public final class ServingModeHelper {

	private ServingModeHelper() {
	}

	/**
	 * Retrieves a specific type of ServingMode based on the provided serving mode string.
	 * @param servingMode The serving mode as a string. Supported options are 'dedicated'
	 * and 'on-demand'.
	 * @param model The model identifier to be used with the serving mode.
	 * @return A ServingMode instance configured according to the provided parameters.
	 * @throws IllegalArgumentException If the specified serving mode is not supported.
	 */
	public static ServingMode get(String servingMode, String model) {
		return switch (servingMode) {
			case "dedicated" -> DedicatedServingMode.builder().endpointId(model).build();
			case "on-demand" -> OnDemandServingMode.builder().modelId(model).build();
			default -> throw new IllegalArgumentException(String.format(
					"Unknown serving mode for OCI Gen AI: %s. Supported options are 'dedicated' and 'on-demand'",
					servingMode));
		};
	}

}
