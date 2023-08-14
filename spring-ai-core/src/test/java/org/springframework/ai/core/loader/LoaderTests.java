package org.springframework.ai.core.loader;

import org.junit.jupiter.api.Test;
import org.springframework.ai.core.document.Document;
import org.springframework.ai.core.loader.impl.JsonLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class LoaderTests {

	@Value("classpath:bikes.json")
	private Resource resource;

	@Test
	void loadJson() {
		assertThat(resource).isNotNull();
		JsonLoader jsonLoader = new JsonLoader("description", resource);
		List<Document> documents = jsonLoader.load();
		assertThat(documents).isNotEmpty();
		for (Document document : documents) {
			assertThat(document.getText()).isNotEmpty();
		}
	}

}
