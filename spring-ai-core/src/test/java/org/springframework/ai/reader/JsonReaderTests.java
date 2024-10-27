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

package org.springframework.ai.reader;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class JsonReaderTests {

	@Value("classpath:person.json")
	private Resource ObjectResource;

	@Value("classpath:bikes.json")
	private Resource arrayResource;

	@Value("classpath:events.json")
	private Resource eventsResource;

	@Test
	void loadJsonArray() {
		assertThat(this.arrayResource).isNotNull();
		JsonReader jsonReader = new JsonReader(this.arrayResource, "description");
		List<Document> documents = jsonReader.get();
		assertThat(documents).isNotEmpty();
		for (Document document : documents) {
			assertThat(document.getContent()).isNotEmpty();
		}
	}

	@Test
	void loadJsonObject() {
		assertThat(this.ObjectResource).isNotNull();
		JsonReader jsonReader = new JsonReader(this.ObjectResource, "description");
		List<Document> documents = jsonReader.get();
		assertThat(documents).isNotEmpty();
		for (Document document : documents) {
			assertThat(document.getContent()).isNotEmpty();
		}
	}

	@Test
	void loadJsonArrayFromPointer() {
		assertThat(this.arrayResource).isNotNull();
		JsonReader jsonReader = new JsonReader(this.eventsResource, "description");
		List<Document> documents = jsonReader.get("/0/sessions");
		assertThat(documents).isNotEmpty();
		for (Document document : documents) {
			assertThat(document.getContent()).isNotEmpty();
			assertThat(document.getContent()).contains("Session");
		}
	}

	@Test
	void loadJsonObjectFromPointer() {
		assertThat(this.ObjectResource).isNotNull();
		JsonReader jsonReader = new JsonReader(this.ObjectResource, "name");
		List<Document> documents = jsonReader.get("/store");
		assertThat(documents).isNotEmpty();
		assertThat(documents.size()).isEqualTo(1);
		assertThat(documents.get(0).getContent()).contains("name: Bike Shop");
	}

}
