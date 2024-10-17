/*
 * Copyright 2023 - 2024 the original author or authors.
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
package org.springframework.ai.wordlift.metadata.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.wordlift.metadata.WordLiftRateLimit;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;

import static org.springframework.ai.wordlift.metadata.support.WordLiftApiResponseHeaders.*;

/**
 * Utility used to extract known HTTP response headers for the {@literal WordLift} API.
 * <p>
 * Original code by John Blum and Christian Tzolov.
 *
 * @author David Riccitelli
 */
public class WordLiftResponseHeaderExtractor {

  private static final Logger logger = LoggerFactory.getLogger(
    WordLiftResponseHeaderExtractor.class
  );

  public static RateLimit extractAiResponseHeaders(ResponseEntity<?> response) {
    Long requestsLimit = getHeaderAsLong(
      response,
      REQUESTS_LIMIT_HEADER.getName()
    );
    Long requestsRemaining = getHeaderAsLong(
      response,
      REQUESTS_REMAINING_HEADER.getName()
    );
    Duration requestsReset = Duration.ofSeconds(
      Optional
        .ofNullable(getHeaderAsLong(response, REQUESTS_RESET_HEADER.getName()))
        .orElse(0L)
    );

    return new WordLiftRateLimit(
      requestsLimit,
      requestsRemaining,
      requestsReset
    );
  }

  private static Long getHeaderAsLong(
    ResponseEntity<?> response,
    String headerName
  ) {
    var headers = response.getHeaders();
    if (headers.containsKey(headerName)) {
      var values = headers.get(headerName);
      if (!CollectionUtils.isEmpty(values)) {
        return parseLong(headerName, values.get(0));
      }
    }
    return null;
  }

  private static Long parseLong(String headerName, String headerValue) {
    if (StringUtils.hasText(headerValue)) {
      try {
        return Long.parseLong(headerValue.trim());
      } catch (NumberFormatException e) {
        logger.warn(
          "Value [{}] for HTTP header [{}] is not valid: {}",
          headerName,
          headerValue,
          e.getMessage()
        );
      }
    }

    return null;
  }
}
