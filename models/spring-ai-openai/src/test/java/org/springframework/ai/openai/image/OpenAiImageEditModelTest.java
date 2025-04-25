package org.springframework.ai.openai.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.image.ImageEditMessage;
import org.springframework.ai.image.ImageEditPrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiImageEditModel;
import org.springframework.ai.openai.OpenAiImageEditOptions;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@RestClientTest(OpenAiImageEditModelTest.Config.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiImageEditModelTest {

	@Autowired
	private OpenAiImageEditModel openAiImageEditModel;

	@Autowired
	private MockRestServiceServer server;

	@BeforeEach
	void setup() {
		// Setup code if needed
	}

	@AfterEach
	void resetMockServer() {
		this.server.reset();
	}

	@Test
	void imageEditTest() {
		prepareMock();

		ImageEditPrompt prompt = new ImageEditPrompt(
				new ImageEditMessage(List.of(new ClassPathResource("test.png")), "Add a sunset background."),
				OpenAiImageEditOptions.builder().build());

		ImageResponse response = this.openAiImageEditModel.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);
	}

	private void prepareMock() {
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.set("Some-Header", "SomeValue");

		this.server.expect(requestTo("https://api.openai.com/v1/images/edits"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer OPENAI_API_KEY"))
			.andRespond(withSuccess(getJson(), MediaType.APPLICATION_JSON).headers(httpHeaders));
	}

	private String getJson() {
		return """
				{
				    "created": 1589478378,
				    "data": [
				        {
				            "url": "https://example.com/edited-image.jpg"
				        }
				    ]
				}
				""";
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public OpenAiImageApi imageApi(RestClient.Builder builder) {
			return OpenAiImageApi.builder()
				.apiKey(new SimpleApiKey("OPENAI_API_KEY"))
				.restClientBuilder(builder)
				.build();
		}

		@Bean
		public OpenAiImageEditModel openAiImageEditModel(OpenAiImageApi openAiImageApi) {
			return new OpenAiImageEditModel(openAiImageApi);
		}

	}

}
