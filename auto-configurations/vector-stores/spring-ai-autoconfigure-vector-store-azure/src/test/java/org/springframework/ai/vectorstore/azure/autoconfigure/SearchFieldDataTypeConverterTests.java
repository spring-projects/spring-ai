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

package org.springframework.ai.vectorstore.azure.autoconfigure;

import com.azure.search.documents.indexes.models.SearchFieldDataType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Aditya Vijay
 */
public class SearchFieldDataTypeConverterTests {

	private final SearchFieldDataTypeConverter converter = new SearchFieldDataTypeConverter();

	@Test
	public void convertsShortNames() {
		assertThat(this.converter.convert("string")).isEqualTo(SearchFieldDataType.STRING);
		assertThat(this.converter.convert("int32")).isEqualTo(SearchFieldDataType.INT32);
		assertThat(this.converter.convert("int64")).isEqualTo(SearchFieldDataType.INT64);
		assertThat(this.converter.convert("double")).isEqualTo(SearchFieldDataType.DOUBLE);
		assertThat(this.converter.convert("boolean")).isEqualTo(SearchFieldDataType.BOOLEAN);
	}

	@Test
	public void convertsCanonicalNames() {
		assertThat(this.converter.convert("Edm.String")).isEqualTo(SearchFieldDataType.STRING);
		assertThat(this.converter.convert("Edm.Int32")).isEqualTo(SearchFieldDataType.INT32);
		assertThat(this.converter.convert("Edm.DateTimeOffset")).isEqualTo(SearchFieldDataType.DATE_TIME_OFFSET);
	}

	@Test
	public void convertsCaseInsensitively() {
		assertThat(this.converter.convert("STRING")).isEqualTo(SearchFieldDataType.STRING);
		assertThat(this.converter.convert("Edm.STRING")).isEqualTo(SearchFieldDataType.STRING);
	}

	@Test
	public void convertsCollectionTypes() {
		assertThat(this.converter.convert("Collection(Edm.String)"))
			.isEqualTo(SearchFieldDataType.collection(SearchFieldDataType.STRING));
		assertThat(this.converter.convert("Collection(string)"))
			.isEqualTo(SearchFieldDataType.collection(SearchFieldDataType.STRING));
	}

	@Test
	public void passesUnknownValuesThrough() {
		assertThat(this.converter.convert("Edm.SomeFutureType").toString()).isEqualTo("Edm.SomeFutureType");
	}

}
