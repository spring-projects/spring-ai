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

package org.springframework.ai.model;

import java.util.Optional;

/**
 * Strategy interface for looking up known runtime capabilities of a model.
 *
 * @author Jewoo Shin
 * @since 2.0.1
 */
@FunctionalInterface
public interface ModelCapabilitiesProvider {

	/**
	 * Return known capabilities for the requested model, or {@link Optional#empty()} when
	 * this provider has no metadata for it.
	 * @param request the lookup request
	 * @return optional capabilities for the requested model
	 */
	Optional<ModelCapabilities> getCapabilities(ModelCapabilitiesRequest request);

}
