/*
 * Copyright 2024 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.yi.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.function.Consumer;

/**
 * @author Geng Rong
 */
public class ApiUtils {

	// Default base API URL
	public static final String DEFAULT_BASE_URL = "https://api.lingyiwanwu.com";

	/**
	 * Returns a Consumer that sets JSON content type and API key in HttpHeaders.
	 * @param apiKey The key used for API authentication.
	 * @return A Consumer function to apply on HttpHeaders instances, setting the
	 * authentication and content type.
	 */
	public static Consumer<HttpHeaders> getJsonContentHeaders(String apiKey) {
		return (headers) -> {
			headers.setBearerAuth(apiKey); // Set Bearer authentication
			headers.setContentType(MediaType.APPLICATION_JSON); // Set content type to
																// JSON
		};
	}

}
