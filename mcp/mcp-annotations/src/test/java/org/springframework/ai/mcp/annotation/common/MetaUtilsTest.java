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

package org.springframework.ai.mcp.annotation.common;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.annotation.context.DefaultMetaProvider;
import org.springframework.ai.mcp.annotation.context.MetaProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

final class MetaUtilsTest {

	@Test
	void testGetMetaNonNull() {

		Map<String, Object> actual = MetaUtils.getMeta(MetaProviderWithDefaultConstructor.class);

		assertThat(actual).containsExactlyInAnyOrderEntriesOf(new MetaProviderWithDefaultConstructor().getMeta());
	}

	@Test
	void testGetMetaWithPublicConstructor() {

		Map<String, Object> actual = MetaUtils.getMeta(MetaProviderWithAvailableConstructor.class);

		assertThat(actual).containsExactlyInAnyOrderEntriesOf(new MetaProviderWithAvailableConstructor().getMeta());
	}

	@Test
	void testGetMetaWithUnavailableConstructor() {

		assertThatIllegalArgumentException()
			.isThrownBy(() -> MetaUtils.getMeta(MetaProviderWithUnavailableConstructor.class))
			.withMessage(
					"org.springframework.ai.mcp.annotation.common.MetaUtilsTest$MetaProviderWithUnavailableConstructor instantiation failed");
	}

	@Test
	void testGetMetaWithConstructorWithWrongSignature() {

		assertThatIllegalArgumentException()
			.isThrownBy(() -> MetaUtils.getMeta(MetaProviderWithConstructorWithWrongSignature.class))
			.withMessage(
					"Required no-arg constructor not found in org.springframework.ai.mcp.annotation.common.MetaUtilsTest$MetaProviderWithConstructorWithWrongSignature");
	}

	@Test
	void testGetMetaNull() {

		Map<String, Object> actual = MetaUtils.getMeta(DefaultMetaProvider.class);

		assertThat(actual).isNull();
	}

	@Test
	void testMetaProviderClassIsNullReturnsNull() {

		Map<String, Object> actual = MetaUtils.getMeta(null);

		assertThat(actual).isNull();
	}

	static class MetaProviderWithDefaultConstructor implements MetaProvider {

		@Override
		public Map<String, Object> getMeta() {
			return Map.of("a", "1", "b", "2");
		}

	}

	@SuppressWarnings("unused")
	static final class MetaProviderWithAvailableConstructor extends MetaProviderWithDefaultConstructor {

		MetaProviderWithAvailableConstructor() {
			// Nothing to do here
		}

	}

	@SuppressWarnings("unused")
	static final class MetaProviderWithUnavailableConstructor extends MetaProviderWithDefaultConstructor {

		private MetaProviderWithUnavailableConstructor() {
			// Nothing to do here
		}

	}

	@SuppressWarnings("unused")
	static final class MetaProviderWithConstructorWithWrongSignature extends MetaProviderWithDefaultConstructor {

		private MetaProviderWithConstructorWithWrongSignature(int invalid) {
			// Nothing to do here
		}

	}

}
