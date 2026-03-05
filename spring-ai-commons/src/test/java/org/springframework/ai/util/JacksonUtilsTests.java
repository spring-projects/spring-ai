/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.util;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonUtilsTests {

	/*
	 * Make sure that JacksonUtils use the correct classloader to load modules. See
	 * https://github.com/spring-projects/spring-ai/issues/2921
	 */
	@Test
	void usesCorrectClassLoader() throws ClassNotFoundException {
		ClassLoader previousLoader = Thread.currentThread().getContextClassLoader();
		try {
			// This parent CL cannot see the clazz class below. But this shouldn't matter.
			Thread.currentThread().setContextClassLoader(getClass().getClassLoader().getParent());
			// Should work whatever the current Thread context CL is
			var jsonMapper = JsonMapper.builder().addModules(JacksonUtils.instantiateAvailableModules()).build();
			Class<?> clazz = getClass().getClassLoader().loadClass(getClass().getName() + "$Cell");
			var output = jsonMapper.readValue("{\"name\":\"Amoeba\",\"lifespan\":\"PT42S\"}", clazz);
			assertThat(output).isEqualTo(new Cell("Amoeba", Duration.of(42L, ChronoUnit.SECONDS)));

		}
		finally {
			Thread.currentThread().setContextClassLoader(previousLoader);
		}

	}

	record Cell(String name, Duration lifespan) {
	}

}
