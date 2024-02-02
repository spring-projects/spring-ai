package org.springframework.ai.openai.metadata.support;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class contains all functions that can be potentially passed to the LLM for
 * supporting the function calling feature. It also contains the metadata for each
 * function. It also contains the method to extract argument values suggested by the
 * model.
 *
 */
public class MyFunctionService {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public String getWeatherForLocation(String location) {
		logger.debug("Calling getWeatherForLocation with location " + location);
		if (location.startsWith("Austin")) {
			return "{\"temperature\": \"95\" \"disposition\": \"humid\"}";
		}
		else if (location.startsWith("Scottsdale")) {
			return "{\"temperature\": \"72\" \"disposition\": \"sunny\"}";
		}
		else if (location.startsWith("Pebble")) {
			return "{\"temperature\": \"70\" \"disposition\": \"sunny\"}";
		}
		else if (location.startsWith("Orlando")) {
			return "{\"temperature\": \"75\" \"disposition\": \"sunny\"}";
		}
		else if (location.startsWith("Phoenix")) {
			return "{\"temperature\": \"78\" \"disposition\": \"sunny\"}";
		}
		else if (location.startsWith("Pittsburgh")) {
			return "{\"temperature\": \"60\" \"disposition\": \"cloudy\"}";
		}
		else {
			return "{\"temperature\": \"40\" \"disposition\": \"gloomy\"}";
		}
	}

	public String getAirfareForLocation(String destination) {
		logger.debug("Calling getAirfareForLocation with destination " + destination);
		if (destination.startsWith("Austin")) {
			return "{\"airfare\": \"230\" \"currency\": \"USD\"}";
		}
		else if (destination.startsWith("Scottsdale")) {
			return "{\"airfare\": \"290\" \"currency\": \"USD\"}";
		}
		else if (destination.startsWith("Pebble")) {
			return "{\"airfare\": \"295\" \"currency\": \"USD\"}";
		}
		else if (destination.startsWith("Orlando")) {
			return "{\"airfare\": \"300\" \"currency\": \"USD\"}";
		}
		else if (destination.startsWith("Phoenix")) {
			return "{\"airfare\": \"305\" \"currency\": \"USD\"}";
		}
		else {
			return "{\"airfare\": \"150\" \"currency\": \"USD\"}";
		}
	}

	public String argumentValueExtractor(String functionName, String jsonResponseFromModel) {
		String value;
		try {
			ObjectMapper objectMapper = new ObjectMapper();
			JsonNode node = objectMapper.readTree(jsonResponseFromModel);
			if (functionName.equals("getWeatherForLocation")) {
				value = node.get("location").asText();
			}
			else if (functionName.equals("getAirfareForLocation")) {
				value = node.get("destination").asText();
			}
			else {
				throw new IllegalArgumentException(String.format("FunctionName %s is not supported!", functionName));
			}
		}
		catch (Exception e) {
			throw new RuntimeException(
					"Error extracting response value from model's JSON response: " + jsonResponseFromModel);
		}
		return value;
	}

	public static String getWeatherParametersMetaData() {
		WeatherParameters weatherParameters;
		try {
			weatherParameters = new MyFunctionService.WeatherParameters();
			ObjectMapper om = new ObjectMapper();
			return om.writerWithDefaultPrettyPrinter().writeValueAsString(weatherParameters);
		}
		catch (Exception e) {
			throw new RuntimeException("Exception occurred", e);
		}
	}

	public static String getAirfareParametersMetaData() {
		AirfareParameters airfareParameters;
		try {
			airfareParameters = new MyFunctionService.AirfareParameters();
			ObjectMapper om = new ObjectMapper();
			return om.writerWithDefaultPrettyPrinter().writeValueAsString(airfareParameters);
		}
		catch (Exception e) {
			throw new RuntimeException("Exception occurred", e);
		}
	}

	public record WeatherParameters(@JsonProperty("type") String type,
			@JsonProperty("properties") WeatherProperties properties, @JsonProperty("required") String[] required) {
		public WeatherParameters() {
			this("object",
					new WeatherProperties(new WeatherLocation("string", "The city and state, e.g. San Mateo, CA"),
							new WeatherUnit("string", new String[] { "celsius", "fahrenheit" })),
					new String[] { "location" });
		}
	}

	public record WeatherLocation(@JsonProperty("type") String type, @JsonProperty("description") String description) {
	}

	public record WeatherUnit(@JsonProperty("type") String type, @JsonProperty("enum") String[] unit) {
	}

	public record WeatherProperties(@JsonProperty("location") WeatherLocation location,
			@JsonProperty("unit") WeatherUnit unit) {
	}

	public record RequiredParameters(@JsonProperty("required") String[] required) {
	}

	// -----

	public record AirfareParameters(@JsonProperty("type") String type,
			@JsonProperty("properties") AirfareProperties properties, @JsonProperty("required") String[] required) {
		public AirfareParameters() {
			this("object",
					new AirfareProperties(new AirfareLocation("string",
							"The destination you would like to fly to, from Pittsburgh, PA, e.g. San Deigo, CA"),
							new CurrencyUnit("string", new String[] { "USD", "CAD" })),
					new String[] { "destination" });
		}
	}

	public record AirfareLocation(@JsonProperty("type") String type, @JsonProperty("description") String description) {
	}

	public record CurrencyUnit(@JsonProperty("type") String type, @JsonProperty("enum") String[] unit) {
	}

	public record AirfareProperties(@JsonProperty("destination") AirfareLocation destination,
			@JsonProperty("unit") CurrencyUnit unit) {
	}

	public record AirfareRequiredParameters(@JsonProperty("required") String[] required) {
	}

}