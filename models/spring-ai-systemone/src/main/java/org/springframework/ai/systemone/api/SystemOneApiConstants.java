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

package org.springframework.ai.systemone.api;

import java.time.Duration;

/**
 * Client defaults of the System One API.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class SystemOneApiConstants {

	/** The API root of the hosted System One service. */
	public static final String DEFAULT_BASE_URL = "https://api.typesafe.ai";

	/** The model used when none is configured. */
	public static final String DEFAULT_MODEL = SystemOneModel.LATEST.id();

	/**
	 * Timeout applied to each HTTP operation when the client builds its own transport.
	 */
	public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

	private SystemOneApiConstants() {
	}

}
