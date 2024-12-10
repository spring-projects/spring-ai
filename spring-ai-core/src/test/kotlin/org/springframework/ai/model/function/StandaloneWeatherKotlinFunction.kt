package org.springframework.ai.model.function

class StandaloneWeatherKotlinFunction : Function1<WeatherRequest, WeatherResponse> {

	override fun invoke(weatherRequest: WeatherRequest): WeatherResponse {
		return WeatherResponse(42.0f)
	}
}
