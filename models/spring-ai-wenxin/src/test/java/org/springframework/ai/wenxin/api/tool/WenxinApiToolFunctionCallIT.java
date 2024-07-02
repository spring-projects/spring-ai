package org.springframework.ai.wenxin.api.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.wenxin.api.WenxinApi;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "WENXIN_ACCESS_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WENXIN_SECRET_KEY", matches = ".+")
public class WenxinApiToolFunctionCallIT {

	private final Logger logger = LoggerFactory.getLogger(WenxinApiToolFunctionCallIT.class);

	private MockWeatherService weatherService = new MockWeatherService();

	private WenxinApi completionApi = new WenxinApi(System.getenv("WENXIN_ACCESS_KEY"),
			System.getenv("WENXIN_SECRET_KEY"));

	private static <T> T fromJson(String json, Class<T> targetClass) {
		try {
			return new ObjectMapper().readValue(json, targetClass);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	private static @NotNull List<List<WenxinApi.FunctionTool.Example>> getLists() {
		var example1 = new WenxinApi.FunctionTool.Example(WenxinApi.Role.USER,
				"weather list about San Francisco and Tokyo and Paris?", null, null);

		var example2 = new WenxinApi.FunctionTool.Example(WenxinApi.Role.ASSISTANT, null, null,
				new WenxinApi.FunctionCall("getCurrentWeather", """
						{
						    "city_infos": [
						        {
						            "location": "San Francisco",
						            "lat": 37.7749,
						            "lon": -122.4194,
						            "unit": "C"
						        },
						        {
						            "location": "Tokyo",
						            "lat": 35.6895,
						            "lon": 139.6917,
						            "unit": "C"
						        },
						        {
						            "location": "Paris",
						            "lat": 48.8566,
						            "lon": 2.3522,
						            "unit": "C"
						        }
						    ]
						}
						""", null));
		List<WenxinApi.FunctionTool.Example> examples = new ArrayList<>(List.of(example1, example2));
		List<List<WenxinApi.FunctionTool.Example>> exampleList = new ArrayList<>(List.of(examples));
		return exampleList;
	}

	@Test
	public void tooFunctionCall() throws JsonProcessingException {

		var message = new WenxinApi.ChatCompletionMessage("weather list about San Francisco and Tokyo and Paris?",
				WenxinApi.Role.USER);

		List<List<WenxinApi.FunctionTool.Example>> exampleList = getLists();
		var functionTool = new WenxinApi.FunctionTool("getCurrentWeather",
				"Get the weather in location. Return temperature in Celsius.", ModelOptionsUtils.jsonToMap("""
						{
						    "type": "object",
						    "properties": {
						        "city_infos": {
						            "type": "array",
						            "description": "city_info",
						            "items": {
						                "type": "object",
						                "properties": {
						                    "location": {
						                        "type": "string",
						                        "description": "The city name"
						                    },
						                    "lat": {
						                        "type": "number",
						                        "description": "The city latitude"
						                    },
						                    "lon": {
						                        "type": "number",
						                        "description": "The city longitude"
						                    },
						                    "unit": {
						                        "type": "string",
						                        "enum": ["C", "F"]
						                    }
						                },
						                "required": ["location", "lat", "lon", "unit"]
						            }

						        }
						    }
						}
										"""), null, exampleList);

		List<WenxinApi.ChatCompletionMessage> messages = new ArrayList<>(List.of(message));

		WenxinApi.ChatCompletionRequest chatCompletionRequest = new WenxinApi.ChatCompletionRequest(messages,
				// "completions", List.of(functionTool),
				// WenxinApi.ChatCompletionRequest.ToolChoiceBuilder.FUNCTION
				// ("getCurrentWeather"), true);
				"completions", List.of(functionTool), null, true);
		ResponseEntity<WenxinApi.ChatCompletion> chatCompletion = completionApi
			.chatCompletionEntity(chatCompletionRequest);

		assertThat(chatCompletion.getBody()).isNotNull();
		assertThat(chatCompletion.getBody().functionCall()).isNotNull();

		// chatCompletion.getBody().
		WenxinApi.ChatCompletionMessage responseMessage = new WenxinApi.ChatCompletionMessage(
				chatCompletion.getBody().result(), WenxinApi.Role.ASSISTANT, null,
				chatCompletion.getBody().functionCall());

		if (chatCompletion.getBody().finishReason().name().equals("FUNCTION_CALL")) {
			messages.add(responseMessage);

			if ("getCurrentWeather".equals(responseMessage.functionCall().name())) {
				MockWeatherService.Request weatherRequest = fromJson(responseMessage.functionCall().arguments(),
						MockWeatherService.Request.class);

				MockWeatherService.Response weatherResponse = weatherService.apply(weatherRequest);

				messages.add(new WenxinApi.ChatCompletionMessage(new ObjectMapper().writeValueAsString(weatherResponse),
						WenxinApi.Role.FUNCTION, "getCurrentWeather", null));
			}
		}

		var functionResponseRequest = new WenxinApi.ChatCompletionRequest(messages, "completions",
				List.of(functionTool), null, true);

		ResponseEntity<WenxinApi.ChatCompletion> functionResponse = completionApi
			.chatCompletionEntity(functionResponseRequest);

		logger.info("Function response: {}", functionResponse.getBody().result());

		assertThat(functionResponse.getBody().result()).isNotEmpty();

	}

}
