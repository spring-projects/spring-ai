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

package org.springframework.ai.anthropic;

import com.anthropic.core.JsonValue;
import com.anthropic.models.ApiErrorObject;
import com.anthropic.models.AuthenticationError;
import com.anthropic.models.BillingError;
import com.anthropic.models.ErrorObject;
import com.anthropic.models.ErrorResponse;
import com.anthropic.models.GatewayTimeoutError;
import com.anthropic.models.InvalidRequestError;
import com.anthropic.models.NotFoundError;
import com.anthropic.models.OverloadedError;
import com.anthropic.models.PermissionError;
import com.anthropic.models.RateLimitError;
import org.jspecify.annotations.Nullable;

/**
 * Failure of a single request inside an Anthropic message batch.
 *
 * <p>
 * Individual failures are surfaced per request rather than thrown, so that a single bad
 * entry does not hide the results of the rest of the batch.
 *
 * @param type the Anthropic error type, for example {@code invalid_request_error},
 * {@code rate_limit_error} or {@code overloaded_error}
 * @param message the human-readable error message
 * @param requestId the Anthropic request identifier, when reported
 * @author Ricken Bazolo
 * @since 2.0.0
 * @see <a href="https://platform.claude.com/docs/en/api/errors">Anthropic error types</a>
 */
public record AnthropicBatchError(String type, String message, @Nullable String requestId) {

	private static final String UNKNOWN_TYPE = "unknown_error";

	static AnthropicBatchError from(ErrorResponse errorResponse) {
		String requestId = errorResponse.requestId().orElse(null);
		AnthropicBatchError error = errorResponse.error().accept(new ErrorObject.Visitor<AnthropicBatchError>() {
			@Override
			public AnthropicBatchError visitInvalidRequestError(InvalidRequestError error) {
				return of(error._type(), error.message(), requestId);
			}

			@Override
			public AnthropicBatchError visitAuthenticationError(AuthenticationError error) {
				return of(error._type(), error.message(), requestId);
			}

			@Override
			public AnthropicBatchError visitBillingError(BillingError error) {
				return of(error._type(), error.message(), requestId);
			}

			@Override
			public AnthropicBatchError visitPermissionError(PermissionError error) {
				return of(error._type(), error.message(), requestId);
			}

			@Override
			public AnthropicBatchError visitNotFoundError(NotFoundError error) {
				return of(error._type(), error.message(), requestId);
			}

			@Override
			public AnthropicBatchError visitRateLimitError(RateLimitError error) {
				return of(error._type(), error.message(), requestId);
			}

			@Override
			public AnthropicBatchError visitTimeoutError(GatewayTimeoutError error) {
				return of(error._type(), error.message(), requestId);
			}

			@Override
			public AnthropicBatchError visitApiError(ApiErrorObject error) {
				return of(error._type(), error.message(), requestId);
			}

			@Override
			public AnthropicBatchError visitOverloadedError(OverloadedError error) {
				return of(error._type(), error.message(), requestId);
			}

			@Override
			public AnthropicBatchError unknown(@Nullable JsonValue json) {
				return new AnthropicBatchError(UNKNOWN_TYPE, String.valueOf(json), requestId);
			}
		});
		return error;
	}

	private static AnthropicBatchError of(JsonValue type, String message, @Nullable String requestId) {
		// JsonValue extends the raw JsonField type, so asString() erases to
		// Optional<Object>.
		Object typeValue = type.asString().orElse(null);
		return new AnthropicBatchError(typeValue instanceof String text ? text : UNKNOWN_TYPE, message, requestId);
	}

}
