package org.springframework.ai.wenxin.api.tool;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class MockWeatherService implements Function<MockWeatherService.Request, MockWeatherService.Response> {

	@Override
	public Response apply(Request request) {
		return new Response(
				Map.of("San Francisco", new CityResp(20, 18, 15, 25, 1013, 50, request.cityInfos.get(0).unit()),
						"Tokyo", new CityResp(25, 23, 20, 30, 1013, 50, request.cityInfos.get(0).unit()), "Paris",
						new CityResp(15, 13, 10, 20, 1013, 50, request.cityInfos.get(0).unit())));
	}

	public enum Unit {

		C("metric"), F("imperial");

		public final String unitName;

		Unit(String text) {
			this.unitName = text;
		}

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonClassDescription("Weather API request")
	public record Request(@JsonProperty(required = true, value = "city_infos") List<CityInfo> cityInfos) {

		public record CityInfo(@JsonProperty(required = true,
				value = "location") @JsonPropertyDescription("The city and state e.g. San Francisco, CA") String location,
				@JsonProperty(required = true, value = "lat") @JsonPropertyDescription("The city latitude") double lat,
				@JsonProperty(required = true, value = "lon") @JsonPropertyDescription("The city longitude") double lon,
				@JsonProperty(required = true, value = "unit") @JsonPropertyDescription("Temperature unit") Unit unit) {

		}
	}

	public record CityResp(double temp, double feels_like, double temp_min, double temp_max, int pressure, int humidity,
			Unit unit) {

	}

	public record Response(Map<String, CityResp> cityRests) {
	}

}
