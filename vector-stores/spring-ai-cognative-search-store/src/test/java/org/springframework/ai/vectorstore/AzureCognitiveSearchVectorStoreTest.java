package org.springframework.ai.vectorstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.azure.json.JsonProviders;
import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.models.SearchField;
import com.azure.search.documents.indexes.models.SearchFieldDataType;
import com.azure.search.documents.indexes.models.SearchIndex;
import com.azure.search.documents.indexes.models.VectorSearch;
import com.azure.search.documents.indexes.models.VectorSearchAlgorithmConfiguration;
import com.azure.search.documents.indexes.models.VectorSearchProfile;

@EnabledIfEnvironmentVariable(named = "SPRING_AI_AZURE_OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "SPRING_AI_AZURE_OPENAI_ENDPOINT", matches = ".+")
@EnabledIfEnvironmentVariable(named = "SPRING_AI_AZURE_COGNITIVE_SEARCH_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "SPRING_AI_AZURE_COGNITIVE_SEARCH_ENDPOINT", matches = ".+")
@EnabledIfEnvironmentVariable(named = "SPRING_AI_AZURE_COGNITIVE_SEARCH_INDEX", matches = ".+")
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = TestApplication.class)
public class AzureCognitiveSearchVectorStoreTest {

	List<Document> documents = List.of(
			new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!",
					Collections.singletonMap("meta1", "meta1")),
			new Document("Hello World Hello World Hello World Hello World Hello World Hello World Hello World"),
			new Document(
					"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression",
					Collections.singletonMap("meta2", "meta2")));

	@Value("classpath:searchAlgo.json")
	protected Resource searchAlgoDef;

	@Autowired
	protected AzureSearchClientProperties serachProps;

	@Autowired
	protected VectorStore vectorStore;

	@Autowired
	protected SearchIndexClient indexClient;

	protected SearchIndex idx;

	@BeforeEach
	public void buildIndex() throws Exception {

		/*
		 * Build the test index using the index name from the
		 * SPRING_AI_AZURE_COGNITIVE_SEARCH_INDEX env variable
		 */

		final var searchFields = Arrays.asList(new SearchField("id", SearchFieldDataType.STRING).setKey(true),
				new SearchField("content", SearchFieldDataType.STRING).setSearchable(true),
				new SearchField("metadata", SearchFieldDataType.STRING),
				new SearchField("contentVector", SearchFieldDataType.collection(SearchFieldDataType.SINGLE))
					.setSearchable(true)
					.setVectorSearchDimensions(1536)
					.setVectorSearchProfile("default"));

		final var reader = JsonProviders.createReader(searchAlgoDef.getInputStream());

		final var searchAlgConfig = VectorSearchAlgorithmConfiguration.fromJson(reader);
		final var vecSearchProfile = new VectorSearchProfile("default", "default");
		final var vecSearch = new VectorSearch();

		vecSearch.setAlgorithms(List.of(searchAlgConfig));
		vecSearch.setProfiles(List.of(vecSearchProfile));

		idx = new SearchIndex(serachProps.getIndex(), searchFields);
		idx.setVectorSearch(vecSearch);

		indexClient.createOrUpdateIndex(idx);
	}

	@AfterEach
	public void destroyIndex() {

		/*
		 * Clean up the test index
		 */

		if (idx != null)
			indexClient.deleteIndex(idx.getName());
	}

	@Test
	public void addAndSearchTest() {

		vectorStore.add(documents);

		Awaitility.await().until(() -> {
			return vectorStore.similaritySearch("Great", 1);
		}, hasSize(1));

		List<Document> results = vectorStore.similaritySearch("Great", 1);

		assertThat(results).hasSize(1);
		Document resultDoc = results.get(0);
		assertThat(resultDoc.getId()).isEqualTo(documents.get(2).getId());
		assertThat(resultDoc.getContent()).isEqualTo(
				"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression");
		assertThat(resultDoc.getMetadata()).hasSize(2);
		assertThat(resultDoc.getMetadata()).containsKey("meta2");
		assertThat(resultDoc.getMetadata()).containsKey("distance");

		vectorStore.delete(documents.stream().map(doc -> doc.getId()).toList());

		Awaitility.await().until(() -> {
			return vectorStore.similaritySearch("Hello", 1);
		}, hasSize(0));

	}

	@Test
	public void documentUpdateTest() {

		Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
				Collections.singletonMap("meta1", "meta1"));

		vectorStore.add(List.of(document));

		Awaitility.await().until(() -> {
			return vectorStore.similaritySearch("Spring", 5);
		}, hasSize(1));

		List<Document> results = vectorStore.similaritySearch("Spring", 5);

		assertThat(results).hasSize(1);
		Document resultDoc = results.get(0);
		assertThat(resultDoc.getId()).isEqualTo(document.getId());
		assertThat(resultDoc.getContent()).isEqualTo("Spring AI rocks!!");
		assertThat(resultDoc.getMetadata()).containsKey("meta1");
		assertThat(resultDoc.getMetadata()).containsKey("distance");

		Document sameIdDocument = new Document(document.getId(),
				"The World is Big and Salvation Lurks Around the Corner", Collections.singletonMap("meta2", "meta2"));

		vectorStore.add(List.of(sameIdDocument));

		Awaitility.await().until(() -> {
			return vectorStore.similaritySearch("FooBar", 5).get(0).getContent();
		}, equalTo("The World is Big and Salvation Lurks Around the Corner"));

		results = vectorStore.similaritySearch("FooBar", 5);

		assertThat(results).hasSize(1);
		resultDoc = results.get(0);
		assertThat(resultDoc.getId()).isEqualTo(document.getId());
		assertThat(resultDoc.getContent()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
		assertThat(resultDoc.getMetadata()).containsKey("meta2");
		assertThat(resultDoc.getMetadata()).containsKey("distance");

		vectorStore.delete(List.of(document.getId()));
		Awaitility.await().until(() -> {
			return vectorStore.similaritySearch("FooBar", 1);
		}, hasSize(0));

	}

	@Test
	public void searchThresholdTest() {

		vectorStore.add(documents);

		Awaitility.await().until(() -> {
			return vectorStore.similaritySearch("Great", 5);
		}, hasSize(3));

		List<Document> fullResult = vectorStore.similaritySearch("Great", 5, 0.0);

		List<Float> distances = fullResult.stream().map(doc -> (Float) doc.getMetadata().get("distance")).toList();

		assertThat(distances).hasSize(3);

		float threshold = (distances.get(0) + distances.get(1)) / 2;

		List<Document> results = vectorStore.similaritySearch("Great", 5, (1 - threshold));

		assertThat(results).hasSize(1);
		Document resultDoc = results.get(0);
		assertThat(resultDoc.getId()).isEqualTo(documents.get(2).getId());
		assertThat(resultDoc.getContent()).isEqualTo(
				"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression");
		assertThat(resultDoc.getMetadata()).containsKey("meta2");
		assertThat(resultDoc.getMetadata()).containsKey("distance");

		vectorStore.delete(documents.stream().map(doc -> doc.getId()).toList());
		Awaitility.await().until(() -> {
			return vectorStore.similaritySearch("Hello", 1);
		}, hasSize(0));

	}

}
