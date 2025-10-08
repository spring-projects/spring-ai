//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.springframework.ai.video.api;

import com.springai.springaivideoextension.enhanced.model.response.VideoResult;
import com.springai.springaivideoextension.enhanced.option.VideoOptions;
import com.springai.springaivideoextension.enhanced.trimer.response.VideoScanResponse;
import org.springframework.ai.model.ApiKey;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

import java.util.Map;

public class VideoApi {
    private final RestClient restClient;
    // 改动点四：将原先的imagesPath改为videoPath，作为后缀请求地址传入
    private final String videoPath;
    private final String videoStatusPath;

    public VideoApi(String baseUrl, ApiKey apiKey, MultiValueMap<String, String> headers, String videoPath, String videoStatusPath, RestClient.Builder restClientBuilder, ResponseErrorHandler responseErrorHandler) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).defaultHeaders((h) -> {
            if (!(apiKey instanceof NoopApiKey)) {
                h.setBearerAuth(apiKey.getValue());
            }

            h.setContentType(MediaType.APPLICATION_JSON);
            h.addAll(headers);
        }).defaultStatusHandler(responseErrorHandler).build();
        this.videoPath = videoPath;
        this.videoStatusPath = videoStatusPath;
    }

    /**
     * 新改动：将返回参数类型改为VideoResult，方便接收返回参数,删除原来的Response的Record类
     * @param videoOptions 自定义的VideoOptions，该接口将会继承ModelOptions
     * @return 返回参数
     */
    public ResponseEntity<VideoResult> createVideo(VideoOptions videoOptions) {
        Assert.notNull(videoOptions, "Video request cannot be null.");
        Assert.hasLength(videoOptions.getPrompt(), "Prompt cannot be empty.");

        return this.restClient.post()
                .uri(this.videoPath)
                .body(videoOptions)
                .retrieve()
                .toEntity(VideoResult.class);
    }

    /**
     * 改动点五：新增一个方法，用于处理视频扫描结果
     * @param requestId 扫描结果标识
     * @return 扫描结果
     */
    public ResponseEntity<VideoScanResponse> createVideo(String requestId) {
        Assert.notNull(requestId, "Video request cannot be null.");

        return this.restClient.post()
                // 注意，这里更换为另一个后缀地址，如果需要，请自行修改，后续会提供完整的压缩包
                .uri(this.videoStatusPath)
                .body(Map.of("requestId", requestId))
                .retrieve()
                .toEntity(VideoScanResponse.class);
    }

    public static Builder builder() {
        return new Builder();
    }


    public static class Builder {
        private String baseUrl = "https://api.openai.com";
        private ApiKey apiKey;
        private MultiValueMap<String, String> headers = new LinkedMultiValueMap();
        private RestClient.Builder restClientBuilder = RestClient.builder();
        private ResponseErrorHandler responseErrorHandler;
        private String videoPath;
        private String videoStatusPath;

        public Builder() {
            this.responseErrorHandler = RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER;
            this.videoPath = "v1/video/submit";
            this.videoStatusPath = "v1/video/status";
        }

        public Builder baseUrl(String baseUrl) {
            Assert.hasText(baseUrl, "baseUrl cannot be null or empty");
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder videoPath(String videoPath) {
            Assert.hasText(videoPath, "videoPath cannot be null or empty");
            this.videoPath = videoPath;
            return this;
        }

        public Builder videoStatusPath(String videoStatusPath) {
            Assert.hasText(videoStatusPath, "videoStatusPath cannot be null or empty");
            this.videoStatusPath = videoStatusPath;
            return this;
        }

        public Builder apiKey(ApiKey apiKey) {
            Assert.notNull(apiKey, "apiKey cannot be null");
            this.apiKey = apiKey;
            return this;
        }

        public Builder apiKey(String simpleApiKey) {
            Assert.notNull(simpleApiKey, "simpleApiKey cannot be null");
            this.apiKey = new SimpleApiKey(simpleApiKey);
            return this;
        }

        public Builder headers(MultiValueMap<String, String> headers) {
            Assert.notNull(headers, "headers cannot be null");
            this.headers = headers;
            return this;
        }

        public Builder restClientBuilder(RestClient.Builder restClientBuilder) {
            Assert.notNull(restClientBuilder, "restClientBuilder cannot be null");
            this.restClientBuilder = restClientBuilder;
            return this;
        }

        public Builder responseErrorHandler(ResponseErrorHandler responseErrorHandler) {
            Assert.notNull(responseErrorHandler, "responseErrorHandler cannot be null");
            this.responseErrorHandler = responseErrorHandler;
            return this;
        }

        public VideoApi build() {
            Assert.notNull(this.apiKey, "apiKey must be set");
            return new VideoApi(this.baseUrl, this.apiKey, this.headers, this.videoPath, this.videoStatusPath, this.restClientBuilder, this.responseErrorHandler);
        }
    }
}
