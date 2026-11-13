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

package org.springframework.ai.systemone.api.exception;

import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpHeaders;

/**
 * Raised on HTTP 529, the non-standard status the API uses to say it is temporarily
 * overloaded. Retry after a short delay.
 *
 * <p>
 * This extends {@link SystemOneInternalServerException} rather than sitting beside it,
 * because 529 is numerically a 5xx and both official SDKs report it as their
 * internal-server error. Catching {@code SystemOneInternalServerException} therefore
 * still catches an overload, exactly as the documented Python and JavaScript patterns do,
 * while catching this type narrows to the one 5xx that is worth retrying on its own
 * terms.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public class SystemOneOverloadedException extends SystemOneInternalServerException {

	/** The non-standard status code this exception maps. */
	public static final int OVERLOADED_STATUS = 529;

	public SystemOneOverloadedException(String message, int status, @Nullable String body, HttpHeaders headers,
			String endpoint) {
		super(message, status, body, headers, endpoint);
	}

}
