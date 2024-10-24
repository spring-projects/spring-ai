/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.model.function;

import java.lang.reflect.Type;
import java.util.function.Function;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.function.context.config.FunctionContextUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TypeResolverHelperIT {

	@Autowired
	GenericApplicationContext applicationContext;

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "weatherClassDefinition", "weatherFunctionDefinition", "standaloneWeatherFunction" })
	void beanInputTypeResolutionTest(String beanName) {
		assertThat(this.applicationContext).isNotNull();
		Type beanType = FunctionContextUtils.findType(this.applicationContext.getBeanFactory(), beanName);
		assertThat(beanType).isNotNull();
		Type functionInputType = TypeResolverHelper.getFunctionArgumentType(beanType, 0);
		assertThat(functionInputType).isNotNull();
		assertThat(functionInputType.getTypeName()).isEqualTo(WeatherRequest.class.getName());

	}

	public record WeatherRequest(String city) {

	}

	public record WeatherResponse(float temperatureInCelsius) {

	}

	public static class Outer {

		public static class InnerWeatherFunction implements Function<WeatherRequest, WeatherResponse> {

			@Override
			public WeatherResponse apply(WeatherRequest weatherRequest) {
				return new WeatherResponse(42.0f);
			}

		}

	}

	@SpringBootConfiguration
	public static class TypeResolverHelperConfiguration {

		@Bean
		Outer.InnerWeatherFunction weatherClassDefinition() {
			return new Outer.InnerWeatherFunction();
		}

		@Bean
		Function<WeatherRequest, WeatherResponse> weatherFunctionDefinition() {
			return new Outer.InnerWeatherFunction();
		}

		@Bean
		StandaloneWeatherFunction standaloneWeatherFunction() {
			return new StandaloneWeatherFunction();
		}

	}

}
