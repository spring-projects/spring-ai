/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.chat.client;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatClientAttributesTests {

	@Test
	void nonDeprecatedConstantsHaveUniqueKeys() {
		var nonDeprecated = Arrays.stream(ChatClientAttributes.values()).filter(a -> !isDeprecated(a)).toList();

		var distinctKeys = nonDeprecated.stream().map(ChatClientAttributes::getKey).distinct().toList();

		assertThat(distinctKeys).as("non-deprecated ChatClientAttributes constants must have unique keys")
			.hasSameSizeAs(nonDeprecated);
	}

	private static boolean isDeprecated(ChatClientAttributes attribute) {
		try {
			Field field = ChatClientAttributes.class.getField(attribute.name());
			return field.isAnnotationPresent(Deprecated.class);
		}
		catch (NoSuchFieldException e) {
			return false;
		}
	}

}
