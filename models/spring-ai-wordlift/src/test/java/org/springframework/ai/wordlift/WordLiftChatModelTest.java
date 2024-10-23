package org.springframework.ai.wordlift;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.wordlift.api.WordLiftApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest
@AutoConfigureWireMock(port = 0)
class WordLiftChatModelTest {

	@Value("http://localhost:${wiremock.server.port}/ai035921298454/{model}")
	private String url;

	WordLiftChatModel wordLiftChatModel(
			String apiKey,
			String model
	) {
		return new WordLiftChatModel(
				new WordLiftApi(url, apiKey),
				WordLiftChatOptions.builder().withModel(model).build()
		);
	}

	@Test
	void test() {

		stubFor(post(urlPathEqualTo("/ai035921298454/model035921298454/chat/completions"))
				.withHeader(HttpHeaders.AUTHORIZATION, equalTo("Key key035921298454"))
				.withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_JSON_VALUE))
				.withRequestBody(equalToJson(
						"""
									{
								                           "messages": [{ "content": "Hello 035921298454", "role": "user" }],
								                           "model": "model035921298454",
								                           "stream": false
									}
								"""
				))
				.willReturn(aResponse()
						.withStatus(HttpStatus.OK.value())
						.withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
						.withBody("""
								{
								  "choices": [
								    {
								      "finish_reason": "stop",
								      "index": 0,
								      "message": {
								        "content": "Hello World 035921298454",
								        "role": "assistant"
								      }
								    }
								  ],
								  "created": 1722943770,
								  "id": "chatcmpl-9tCfa0QnI6uik16p0s7l9ZNESt3gw",
								  "model": "model035921298454",
								  "object": "chat.completion",
								  "system_fingerprint": "fp_811936bd4f",
								  "usage": {
								    "completion_tokens": 106,
								    "prompt_tokens": 1017,
								    "total_tokens": 1123
								  }
								}
								"""))
		);

		final WordLiftChatModel model = wordLiftChatModel("key035921298454", "model035921298454");
		final String response = model.call("Hello 035921298454");

		Assertions.assertEquals("Hello World 035921298454", response);

		verify(postRequestedFor(urlPathEqualTo("/ai035921298454/model035921298454/chat/completions"))
				.withHeader(HttpHeaders.AUTHORIZATION, equalTo("Key key035921298454"))
				.withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_JSON_VALUE))
				.withRequestBody(equalToJson(
						"""
									{
								                           "messages": [{ "content": "Hello 035921298454", "role": "user" }],
								                           "model": "model035921298454",
								                           "stream": false
									}
								"""
				))
		);

	}

}