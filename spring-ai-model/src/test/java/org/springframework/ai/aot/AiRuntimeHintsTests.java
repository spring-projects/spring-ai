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

package org.springframework.ai.aot;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;

import org.springframework.aot.hint.TypeReference;
import org.springframework.util.Assert;

class AiRuntimeHintsTests {

	@Test
	void discoverRelevantClasses() throws Exception {
		var classes = AiRuntimeHints.findJsonAnnotatedClassesInPackage(TestApi.class);
		var included = Set.of(TestApi.Bar.class, TestApi.Foo.class)
			.stream()
			.map(t -> TypeReference.of(t.getName()))
			.collect(Collectors.toSet());
		LogFactory.getLog(getClass()).info(classes);
		Assert.state(classes.containsAll(included), "there should be all of the enumerated classes. ");
	}

	@JsonInclude
	static class TestApi {

		@JsonInclude
		enum Bar {

			A, B

		}

		static class FooBar {

		}

		record Foo(@JsonProperty("name") String name) {

		}

	}

}
