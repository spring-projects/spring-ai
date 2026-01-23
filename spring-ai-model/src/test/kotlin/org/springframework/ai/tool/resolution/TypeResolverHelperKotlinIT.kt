/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.tool.resolution

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.GenericApplicationContext

@SpringBootTest
class TypeResolverHelperKotlinIT {

	@Autowired
	lateinit var applicationContext: GenericApplicationContext

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = ["weatherClassDefinition", "weatherFunctionDefinition", "standaloneWeatherFunction", "scannedStandaloneWeatherFunction"])
	fun beanInputTypeResolutionTest(beanName: String) {
		assertThat(this.applicationContext).isNotNull()
		val functionType = TypeResolverHelper.resolveBeanType(this.applicationContext, beanName);
		val functionInputClass = TypeResolverHelper.getFunctionArgumentType(functionType, 0).rawClass;
		assertThat(functionInputClass).isNotNull();
		assertThat(functionInputClass?.typeName).isEqualTo(WeatherRequest::class.java.getName());
	}

	class Outer {

		class InnerWeatherFunction : Function1<WeatherRequest, WeatherResponse> {

			override fun invoke(weatherRequest: WeatherRequest): WeatherResponse {
				return WeatherResponse(42.0f)
			}
		}
	}

	@Configuration
	@ComponentScan("org.springframework.ai.tool.resolution.kotlinconfig")
	open class TypeResolverHelperConfiguration {

		@Bean
		open fun weatherClassDefinition(): Outer.InnerWeatherFunction {
			return Outer.InnerWeatherFunction();
		}

		@Bean
		open fun weatherFunctionDefinition(): Function1<WeatherRequest, WeatherResponse> {
			return Outer.InnerWeatherFunction();
		}

		@Bean
		open fun standaloneWeatherFunction(): StandaloneWeatherKotlinFunction {
			return StandaloneWeatherKotlinFunction();
		}

	}

}

data class WeatherRequest(val city: String)

data class WeatherResponse(val temperatureInCelsius: Float)
