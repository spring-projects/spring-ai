/*
 * Copyright 2025-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.skill.fixtures;

import java.util.List;

import org.springframework.ai.skill.annotation.Skill;
import org.springframework.ai.skill.annotation.SkillContent;
import org.springframework.ai.skill.annotation.SkillInit;
import org.springframework.ai.skill.annotation.SkillTools;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;

/**
 * Test Weather Skill POJO
 *
 * <p>
 * Weather skill using fixed data for testing
 *
 * @author Semir
 */
@Skill(name = "weather", description = "Provides weather information for cities around the world", source = "example",
		extensions = { "version=1.0.0", "author=Semir", "category=information" })
public class WeatherSkill {

	private WeatherSkill() {
	}

	@SkillInit
	public static WeatherSkill create() {
		return new WeatherSkill();
	}

	@SkillContent
	public String content() {
		return """
				# Weather Skill

				Provides weather information for cities around the world.

				## Features

				- Get current weather for any city
				- Temperature in Celsius
				- Weather conditions (Sunny, Rainy, Cloudy, etc.)

				## Available Tools

				- `getWeather(city)` - Get current weather for a specific city

				## Usage

				Ask me "What's the weather in Beijing?" or "Tell me the weather in New York".
				""";
	}

	@SkillTools
	public List<ToolCallback> tools() {
		return List.of(ToolCallbacks.from(this));
	}

	/**
	 * Get weather information for a specific city (uses fixed data for testing)
	 * @param city city name
	 * @return JSON string containing weather information
	 */
	@Tool(description = "Get current weather information for a specific city. Returns temperature in Celsius, weather condition, humidity percentage, and wind speed in km/h.")
	public String getWeather(String city) {
		if (city == null || city.trim().isEmpty()) {
			return "{\"error\": \"City name is required\"}";
		}

		// Use fixed data for test verification
		int temperature;
		String condition;
		int humidity;
		int windSpeed;

		switch (city.toLowerCase()) {
			case "beijing":
				temperature = 25;
				condition = "Sunny";
				humidity = 60;
				windSpeed = 15;
				break;
			case "new york":
				temperature = 20;
				condition = "Cloudy";
				humidity = 70;
				windSpeed = 20;
				break;
			case "london":
				temperature = 15;
				condition = "Rainy";
				humidity = 85;
				windSpeed = 25;
				break;
			case "tokyo":
				temperature = 22;
				condition = "Partly Cloudy";
				humidity = 65;
				windSpeed = 10;
				break;
			case "paris":
				temperature = 18;
				condition = "Foggy";
				humidity = 75;
				windSpeed = 12;
				break;
			default:
				temperature = 20;
				condition = "Clear";
				humidity = 50;
				windSpeed = 10;
				break;
		}

		return String.format(
				"{\"city\": \"%s\", \"temperature\": %d, \"unit\": \"Celsius\", \"condition\": \"%s\", "
						+ "\"humidity\": %d, \"windSpeed\": %d, \"windUnit\": \"km/h\", "
						+ "\"description\": \"The weather in %s is %s with a temperature of %d°C, "
						+ "humidity at %d%%, and wind speed of %d km/h.\"}",
				city, temperature, condition, humidity, windSpeed, city, condition, temperature, humidity, windSpeed);
	}

}
