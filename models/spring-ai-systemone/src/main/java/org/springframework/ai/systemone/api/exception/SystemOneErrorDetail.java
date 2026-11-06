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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import org.springframework.ai.util.JacksonUtils;

/**
 * The parsed {@code detail} of a System One error response.
 *
 * <p>
 * The API wraps every error in a single {@code detail} field, but that field takes three
 * different shapes depending on where the request failed:
 *
 * <pre>{@code
 * // an application error - 400, 401, 403
 * {"detail":{"error_type":"authentication_error","message":"Cannot authenticate with the server."}}
 *
 * // request validation - 422
 * {"detail":[{"type":"missing","loc":["body","questions"],"msg":"Field required"}]}
 *
 * // an unrouted path - 404
 * {"detail":"Not Found"}
 * }</pre>
 *
 * All three are normalised onto this record: {@link #message()} always carries something
 * human-readable, {@link #errorType()} is set only by the object form, and
 * {@link #validationErrors()} is populated only by the array form. A compatible server's
 * {@code {"error":"..."}} body, as Ollama sends it, is read like the string form.
 *
 * <p>
 * This is a best-effort reading of the body. The raw text always remains available as
 * {@link SystemOneApiException#body()}, so an unrecognised shape costs nothing.
 *
 * @param errorType the machine-readable kind, for example {@code authentication_error} or
 * {@code api_usage_error}, or {@code null} when the body did not state one
 * @param message a human-readable description, or {@code null} when none could be read
 * @param validationErrors the per-field validation failures, empty unless the response
 * was a 422
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record SystemOneErrorDetail(@Nullable String errorType, @Nullable String message,
		List<ValidationError> validationErrors) {

	public SystemOneErrorDetail {
		validationErrors = validationErrors == null ? List.of() : List.copyOf(validationErrors);
	}

	/**
	 * Reads the {@code detail} out of a raw error body.
	 * @param body the raw response body, or {@code null}
	 * @return the parsed detail, or {@code null} when the body was absent, empty, not
	 * JSON, or carried neither a {@code detail} field nor a string {@code error} field
	 */
	public static @Nullable SystemOneErrorDetail parse(@Nullable String body) {
		if (body == null || body.isBlank()) {
			return null;
		}
		try {
			return parse(JacksonUtils.getDefaultJsonMapper().readTree(body));
		}
		catch (RuntimeException ex) {
			// A body that is not JSON at all - a proxy error page, say - or a detail
			// whose fields are not the strings the envelope documents. The caller still
			// has the raw text.
			return null;
		}
	}

	private static @Nullable SystemOneErrorDetail parse(JsonNode root) {
		JsonNode detail = root.get("detail");
		if (detail == null || detail.isNull()) {
			// Ollama, serving the same protocol, reports errors as {"error": "..."}.
			JsonNode error = root.get("error");
			return (error != null && error.isString()) ? new SystemOneErrorDetail(null, error.stringValue(), List.of())
					: null;
		}
		if (detail.isString()) {
			return new SystemOneErrorDetail(null, detail.stringValue(), List.of());
		}
		if (detail.isArray()) {
			List<ValidationError> errors = new ArrayList<>();
			for (JsonNode element : detail) {
				errors.add(toValidationError(element));
			}
			return new SystemOneErrorDetail(null, render(errors), Collections.unmodifiableList(errors));
		}
		if (detail.isObject()) {
			return new SystemOneErrorDetail(text(detail, "error_type"), text(detail, "message"), List.of());
		}
		return null;
	}

	private static ValidationError toValidationError(JsonNode node) {
		List<String> loc = new ArrayList<>();
		JsonNode locNode = node.get("loc");
		if (locNode != null && locNode.isArray()) {
			for (JsonNode element : locNode) {
				loc.add(element.asString());
			}
		}
		return new ValidationError(text(node, "type"), loc, text(node, "msg"));
	}

	private static String render(List<ValidationError> errors) {
		StringJoiner joiner = new StringJoiner("; ");
		errors.forEach(error -> joiner.add(error.toString()));
		return joiner.toString();
	}

	private static @Nullable String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return (value == null || value.isNull()) ? null : value.asString();
	}

	/**
	 * @return the message with the error type appended when one was stated, as it appears
	 * in an exception message
	 */
	@Override
	public String toString() {
		if (this.message == null) {
			return this.errorType == null ? "" : "[" + this.errorType + "]";
		}
		return this.errorType == null ? this.message : this.message + " [" + this.errorType + "]";
	}

	/**
	 * One entry of a 422 validation failure.
	 *
	 * @param type the validation rule that failed, for example {@code missing}
	 * @param loc the path to the offending field, for example
	 * {@code ["body", "questions"]}
	 * @param msg what was wrong with it
	 */
	public record ValidationError(@Nullable String type, List<String> loc, @Nullable String msg) {

		public ValidationError {
			loc = loc == null ? List.of() : List.copyOf(loc);
		}

		/**
		 * @return the {@link #loc()} path joined with dots, for example
		 * {@code body.questions}
		 */
		public String path() {
			return String.join(".", this.loc);
		}

		@Override
		public String toString() {
			String path = path();
			return path.isEmpty() ? String.valueOf(this.msg) : path + ": " + this.msg;
		}
	}

}
