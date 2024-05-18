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
package org.springframework.ai.watsonx.api;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.ibm.cloud.sdk.core.security.IamAuthenticator;
import com.ibm.cloud.sdk.core.security.IamToken;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import reactor.core.publisher.Flux;

import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Java Client for the watsonx.ai API. https://www.ibm.com/products/watsonx-ai
 *
 * @author John Jairo Moreno Rojas
 * @author Pablo Sanchidrian Herrera
 * @since 1.0.0
 */
// @formatter:off
public class WatsonxAiApi {

    private static final Log logger = LogFactory.getLog(WatsonxAiApi.class);
    public static final String WATSONX_REQUEST_CANNOT_BE_NULL = "Watsonx Request cannot be null";
    private final RestClient restClient;
    private final WebClient webClient;
    private final IamAuthenticator iamAuthenticator;
    private final String streamEndpoint;
    private final String textEndpoint;
    private final String projectId;
    private IamToken token;

    /**
     * Create a new chat api.
     * @param baseUrl api base URL.
     * @param streamEndpoint streaming generation.
     * @param textEndpoint text generation.
     * @param projectId watsonx.ai project identifier.
     * @param IAMToken IBM Cloud IAM token.
     * @param restClientBuilder rest client builder.
     */
    public WatsonxAiApi(
            String baseUrl,
            String streamEndpoint,
            String textEndpoint,
            String projectId,
            String IAMToken,
            RestClient.Builder restClientBuilder
    ) {
        this.streamEndpoint = streamEndpoint;
        this.textEndpoint = textEndpoint;
        this.projectId = projectId;
        this.iamAuthenticator = IamAuthenticator.fromConfiguration(Map.of("APIKEY", IAMToken));
        this.token = this.iamAuthenticator.requestToken();

        Consumer<HttpHeaders> defaultHeaders = headers -> {
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        };

        this.restClient = restClientBuilder.baseUrl(baseUrl)
            .defaultStatusHandler(RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER)
            .defaultHeaders(defaultHeaders)
            .build();

        this.webClient = WebClient.builder().baseUrl(baseUrl)
            .defaultHeaders(defaultHeaders)
            .build();
    }

    @Retryable(retryFor = Exception.class, maxAttempts = 3, backoff = @Backoff(random = true, delay = 1200, maxDelay = 7000, multiplier = 2.5))
    public ResponseEntity<WatsonxAiResponse> generate(WatsonxAiRequest watsonxAiRequest) {
        Assert.notNull(watsonxAiRequest, WATSONX_REQUEST_CANNOT_BE_NULL);

        if(this.token.needsRefresh()) {
            this.token = this.iamAuthenticator.requestToken();
        }

        return this.restClient.post()
                .uri(this.textEndpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token.getAccessToken())
                .body(watsonxAiRequest.withProjectId(projectId))
                .retrieve()
                .toEntity(WatsonxAiResponse.class);
    }

    @Retryable(retryFor = Exception.class, maxAttempts = 3, backoff = @Backoff(random = true, delay = 1200, maxDelay = 7000, multiplier = 2.5))
    public Flux<WatsonxAiResponse> generateStreaming(WatsonxAiRequest watsonxAiRequest) {
        Assert.notNull(watsonxAiRequest, WATSONX_REQUEST_CANNOT_BE_NULL);

        if(this.token.needsRefresh()) {
            this.token = this.iamAuthenticator.requestToken();
        }

        return this.webClient.post()
                .uri(this.streamEndpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token.getAccessToken())
                .bodyValue(watsonxAiRequest.withProjectId(this.projectId))
                .retrieve()
                .bodyToFlux(WatsonxAiResponse.class)
                .handle((data, sink) -> {
                    if (logger.isTraceEnabled()) {
                        logger.trace(data);
                    }
                    sink.next(data);
                });
    }

}
