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

package org.springframework.ai.openai.responses;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import com.openai.core.ObjectMappers;
import com.openai.models.responses.Response;

import org.springframework.core.io.ClassPathResource;

/**
 * Loads recorded {@code /v1/responses} payloads from
 * {@code src/test/resources/responses}.
 *
 * @author Dimitar Proynov
 */
final class ResponsesTestFixtures {

	private ResponsesTestFixtures() {
	}

	static Response response(String fixtureName) {
		try {
			return ObjectMappers.jsonMapper().readValue(json(fixtureName), Response.class);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Could not parse fixture: " + fixtureName, ex);
		}
	}

	static String json(String fixtureName) {
		try {
			return new ClassPathResource("responses/" + fixtureName).getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Missing fixture: " + fixtureName, ex);
		}
	}

}
