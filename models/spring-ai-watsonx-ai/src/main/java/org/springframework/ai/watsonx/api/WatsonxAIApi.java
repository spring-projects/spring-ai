package org.springframework.ai.watsonx.api;

import com.ibm.cloud.sdk.core.security.IamAuthenticator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Java Client for the watsonx.ai API. https://www.ibm.com/products/watsonx-ai
 *
 * @author John Jairo Moreno Rojas
 * @author Pablo Sanchidrian Herrera
 * @since 0.8.0
 */
// @formatter:off
public class WatsonxAIApi {

    private static final Log logger = LogFactory.getLog(WatsonxAIApi.class);
    public static final String WATSONX_REQUEST_CANNOT_BE_NULL = "Watsonx Request cannot be null";
    private final RestClient restClient;
    private final WebClient webClient;
    private final IamAuthenticator iamAuthenticator;
    private final String baseUrl;
    private final String streamEndpoint;
    private final String textEndpoint;
    private final String projectId;
    private final ResponseErrorHandler responseErrorHandler;

    /**
     * Create a new chat api.
     * @param baseUrl api base URL.
     * @param streamEndpoint streaming generation.
     * @param textEndpoint text generation.
     * @param projectId watsonx.ai project identifier.
     * @param IAMToken IBM Cloud IAM token.
     * @param restClientBuilder rest client builder.
     */
    public WatsonxAIApi(
            String baseUrl,
            String streamEndpoint,
            String textEndpoint,
            String projectId,
            String IAMToken,
            RestClient.Builder restClientBuilder
    ) {
        this.baseUrl = baseUrl;
        this.streamEndpoint = streamEndpoint;
        this.textEndpoint = textEndpoint;
        this.projectId = projectId;
        this.iamAuthenticator = new IamAuthenticator(IAMToken);

        this.responseErrorHandler = new WatsonxResponseErrorHandler();

        Consumer<HttpHeaders> defaultHeaders = headers -> {
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        };

        this.restClient = restClientBuilder.baseUrl(baseUrl).defaultHeaders(defaultHeaders).build();
        this.webClient = WebClient.builder().baseUrl(baseUrl).defaultHeaders(defaultHeaders).build();
    }

    private static class WatsonxResponseErrorHandler implements ResponseErrorHandler {

        @Override
        public boolean hasError(ClientHttpResponse response) throws IOException {
            return response.getStatusCode().isError();
        }

        @Override
        public void handleError(ClientHttpResponse response) throws IOException {
            if (response.getStatusCode().isError()) {
                int statusCode = response.getStatusCode().value();
                String statusText = response.getStatusText();
                String message = StreamUtils.copyToString(response.getBody(), java.nio.charset.StandardCharsets.UTF_8);
                logger.warn(String.format("[%s] %s - %s", statusCode, statusText, message));
                throw new RuntimeException(String.format("[%s] %s - %s", statusCode, statusText, message));
            }
        }

    }

    public WatsonxAIResponse generate(WatsonxAIRequest watsonxAIRequest) {
        Assert.notNull(watsonxAIRequest, WATSONX_REQUEST_CANNOT_BE_NULL);

        String bearer = this.iamAuthenticator.requestToken().getAccessToken();

        return this.restClient.post()
                .uri(this.textEndpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                .body(watsonxAIRequest.withProjectId(projectId))
                .retrieve()
                .onStatus(this.responseErrorHandler)
                .body(WatsonxAIResponse.class);
    }

}
