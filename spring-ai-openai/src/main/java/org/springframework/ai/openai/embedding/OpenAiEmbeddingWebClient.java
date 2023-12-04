package org.springframework.ai.openai.embedding;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class OpenAiEmbeddingWebClient implements EmbeddingClient {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiEmbeddingWebClient.class);

    private final WebClient webClient;

    private final ObjectMapper objectMapper;

    private final String model;

    private final AtomicInteger embeddingDimensions = new AtomicInteger(-1);

    private final MetadataMode metadataMode;

    public OpenAiEmbeddingWebClient(String openAiApiToken) {
        this("https://api.openai.com/", openAiApiToken);
    }

    public OpenAiEmbeddingWebClient(String openAiEndpoint, String openAiApiToken) {
        this(openAiEndpoint, openAiApiToken, "text-embedding-ada-002");
    }

    public OpenAiEmbeddingWebClient(String openAiEndpoint, String openAiApiToken, String model) {
        this(openAiEndpoint, openAiApiToken, model, MetadataMode.EMBED);
    }

    public OpenAiEmbeddingWebClient(String openAiEndpoint, String openAiApiToken, String model,
            MetadataMode metadataMode) {
        Assert.notNull(openAiEndpoint, "OpenAiEndpoint must not be null");
        Assert.notNull(model, "Model must not be null");
        Assert.notNull(metadataMode, "metadataMode must not be null");
        this.webClient = WebClient.builder().baseUrl(openAiEndpoint)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openAiApiToken).build();
        this.objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        this.model = model;
        this.metadataMode = metadataMode;
    }

    @Override
    public List<Double> embed(String text) {
        return embedWithTexts(List.of(text));
    }

    private List<Double> embedWithTexts(List<String> texts) {
        OpenAiEmbeddingsRequest embeddingRequest =
                new OpenAiEmbeddingsRequest.Builder().input(texts).model(this.model).build();
        return createEmbeddings(embeddingRequest).data().get(0).embedding();
    }

    public OpenAiEmbeddingsResponse createEmbeddings(OpenAiEmbeddingsRequest embeddingsRequest) {
        logger.trace("EmbeddingsInput: {}", embeddingsRequest.getInput());

        OpenAiEmbeddingsResponse openAiEmbeddingsResponse = this.webClient.post().uri("/v1/embeddings")
                .bodyValue(objectMapper.convertValue(embeddingsRequest, JsonNode.class)).retrieve()
                .bodyToMono(OpenAiEmbeddingsResponse.class).block();

        logger.trace("EmbeddingsData: {}", openAiEmbeddingsResponse.data());

        return openAiEmbeddingsResponse;
    }

    public List<Double> embed(Document document) {
        return embedWithTexts(List.of(document.getFormattedContent(this.metadataMode)));
    }

    public List<List<Double>> embed(List<String> texts) {
        EmbeddingResponse embeddingResponse = embedForResponse(texts);
        return embeddingResponse.getData().stream().map(Embedding::getEmbedding).toList();
    }

    @Override
    public EmbeddingResponse embedForResponse(List<String> texts) {
        OpenAiEmbeddingsRequest embeddingsRequest =
                new OpenAiEmbeddingsRequest.Builder().input(texts).model(this.model).build();
        return generateEmbeddingResponse(createEmbeddings(embeddingsRequest));
    }

    private EmbeddingResponse generateEmbeddingResponse(OpenAiEmbeddingsResponse openAiEmbeddingsResponse) {
        List<Embedding> data = generateEmbeddingList(openAiEmbeddingsResponse.data());
        Map<String, Object> metadata =
                generateMetadata(openAiEmbeddingsResponse.model(), openAiEmbeddingsResponse.usage());
        return new EmbeddingResponse(data, metadata);
    }

    private List<Embedding> generateEmbeddingList(List<OpenAiEmbeddingsResponse.Data> nativeData) {
        return nativeData.stream().map(data -> new Embedding(data.embedding(), data.index()))
                .collect(Collectors.toList());
    }

    private Map<String, Object> generateMetadata(String model, OpenAiEmbeddingsResponse.Usage usage) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("model", model);
        metadata.put("prompt-tokens", usage.promptTokens());
        metadata.put("completion-tokens", usage.completionTokens());
        metadata.put("total-tokens", usage.totalTokens());
        return metadata;
    }

    @Override
    public int dimensions() {
        if (this.embeddingDimensions.get() < 0) {
            this.embeddingDimensions.set(EmbeddingUtil.dimensions(this, this.model));
        }
        return this.embeddingDimensions.get();
    }

}
