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

package org.springframework.ai.model.function

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class FunctionCallbackExtensionsTests {

	private val spec = mockk<FunctionCallback.FunctionInvokingSpec<WeatherRequest, WeatherResponse>>()

	@Test
	fun inputType() {
		every { spec.inputType(any<Class<*>>()) } returns spec
		spec.inputType<WeatherRequest, WeatherResponse>()
		verify { spec.inputType(WeatherRequest::class.java) }
	}
}
