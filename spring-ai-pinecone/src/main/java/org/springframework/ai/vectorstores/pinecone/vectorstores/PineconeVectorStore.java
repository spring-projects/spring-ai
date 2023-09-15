package org.springframework.ai.vectorstores.pinecone.vectorstores;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.google.common.primitives.Floats;
import com.google.protobuf.Struct;
import java.util.*;
import io.pinecone.PineconeClient;
import io.pinecone.PineconeConnection;
import io.pinecone.proto.DeleteRequest;
import io.pinecone.proto.DeleteResponse;
import io.pinecone.proto.UpsertRequest;
import io.pinecone.proto.UpsertResponse;
import io.pinecone.proto.QueryRequest;
import io.pinecone.proto.QueryResponse;
import io.pinecone.proto.SingleQueryResults;
import io.pinecone.proto.Vector;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan(basePackages = "org.springframework.ai.vectorstores.pinecone")
public class PineconeVectorStore implements VectorStore {

	Logger logger = LoggerFactory.getLogger(PineconeVectorStore.class);

	PineconeClient pineconeClient;

	private EmbeddingClient embeddingClient;

	@Value("${pinecone.index_name}")
	private String pineconeIndexName;

	@Value("${pinecone.namespace}")
	private String pineconeNamespace;

	private PineconeConnection pineconeConnection;

	// Constructor
	@Autowired()
	public PineconeVectorStore(PineconeClient pineconeClient, EmbeddingClient embeddingClient) {
		Objects.requireNonNull(embeddingClient, "EmbeddingClient must not be null");
		this.pineconeClient = pineconeClient;
		this.pineconeConnection = this.pineconeClient.connect(pineconeIndexName);

		Objects.requireNonNull(embeddingClient, "EmbeddingClient must not be null");
		this.embeddingClient = embeddingClient;
	}

	// Methods
	// Add documents to the vector store using the injected PineconeClient
	@Override
	public void add(List<Document> documents) {
		List<Vector> upsertVectors = new ArrayList<>();
		for (int i = 0; i < documents.size(); i++) {
			upsertVectors.add(Vector.newBuilder()
				// The Embeddings
				// TODO: check if this is the correct way to convert from List(Double) to
				// List(Float)
				.addAllValues(Floats.asList(Floats.toArray(documents.get(i).getEmbedding())))
				// The Metadata
				.setMetadata(Struct.newBuilder()
					.putFields("metadata",
							com.google.protobuf.Value.newBuilder()
								.setStringValue(documents.get(i).getMetadata().toString())
								.build())
					.build())
				// The ID
				.setId(documents.get(i).getId())
				.build());
		}
		UpsertRequest request = UpsertRequest.newBuilder()
			.addAllVectors(upsertVectors)
			// TODO: set namespace
			.setNamespace(pineconeNamespace)
			.build();

		UpsertResponse upsertResponse = pineconeConnection.getBlockingStub().upsert(request);
		logger.info("Upserted {} records", upsertResponse.getUpsertedCount());

	}

	// Delete documents from the vector store using the injected PineconeClient
	@Override
	public Optional<Boolean> delete(List<String> idList) {
		DeleteRequest deleteRequest = DeleteRequest.newBuilder()
			.setNamespace(pineconeNamespace)
			.addAllIds(idList)
			.setDeleteAll(false)
			.build();

		DeleteResponse deleteResponse = pineconeConnection.getBlockingStub().delete(deleteRequest);
		logger.info("Deleted {} records", deleteResponse.getSerializedSize());

		// TODO: check if delete was successful, is the deleteResponse.getSerializedSize()
		// > 0 a check we can use to confirm this ?
		return Optional.of(true);
	}

	@Override
	public List<Document> similaritySearch(String query) {
		return similaritySearch(query, 10); // TODO: Discuss this - default k = 10 if not
											// specified
	}

	@Override
	public List<Document> similaritySearch(String query, int k) {
		// use the Embeddings Client to get the query embedding
		List<Double> userQueryEmbedding = this.embeddingClient.embed(query);
		// convert the query embedding from List(Double) to List(Float)
		List<Float> userQueryEmbeddingFloat = new ArrayList<>();

		// use the Pinecone Lib to get the similarity search results
		QueryRequest queryRequest = QueryRequest.newBuilder()
			.addAllVector(userQueryEmbeddingFloat)
			.setNamespace(pineconeNamespace)
			.setTopK(2)
			.setIncludeMetadata(true)
			.setTopK(k)
			.build();

		QueryResponse queryResponse = pineconeConnection.getBlockingStub().query(queryRequest);
		List<SingleQueryResults> resList = queryResponse.getResultsList();
		List<Document> docList = new ArrayList<>();
		for (SingleQueryResults res : resList) {
			res.getMatchesList().forEach(match -> {
				String id = match.getId();
				Float score = match.getScore();

				Map<String, Object> metadata = new HashMap<>();
				// iterate through match.getMetadata().getFieldsMap() and create a
				// Map<String, Object> metadata
				for (Map.Entry<String, com.google.protobuf.Value> entry : match.getMetadata()
					.getFieldsMap()
					.entrySet()) {
					String key = entry.getKey();
					com.google.protobuf.Value value = entry.getValue();
					metadata.put(key, value);
				}
				// TODO: Score is not returned, we need to find a way to get it
				Document doc = new Document(id, metadata);
				docList.add(doc);
			});
		}
		return docList;
	}

	@Override
	public List<Document> similaritySearch(String query, int k, double threshold) {
		// use the Embeddings Client to get the query embedding
		List<Double> userQueryEmbedding = this.embeddingClient.embed(query);
		// convert the query embedding from List(Double) to List(Float)
		List<Float> userQueryEmbeddingFloat = new ArrayList<>();

		// use the Pinecone Lib to get the similarity search results
		QueryRequest queryRequest = QueryRequest.newBuilder()
			.addAllVector(userQueryEmbeddingFloat)
			.setNamespace(pineconeNamespace)
			.setTopK(2)
			.setIncludeMetadata(true)
			.setTopK(k)
			.build();

		QueryResponse queryResponse = pineconeConnection.getBlockingStub().query(queryRequest);
		List<SingleQueryResults> resList = queryResponse.getResultsList();
		List<Document> docList = new ArrayList<>();
		for (SingleQueryResults res : resList) {
			res.getMatchesList().forEach(match -> {
				String id = match.getId();
				Float score = match.getScore();

				Map<String, Object> metadata = new HashMap<>();
				// iterate through match.getMetadata().getFieldsMap() and create a
				// Map<String, Object> metadata
				for (Map.Entry<String, com.google.protobuf.Value> entry : match.getMetadata()
					.getFieldsMap()
					.entrySet()) {
					String key = entry.getKey();
					com.google.protobuf.Value value = entry.getValue();
					metadata.put(key, value);
				}
				// TODO: Score is not returned, we need to find a way to get it
				if (score > threshold) {
					Document doc = new Document(id, metadata);
					docList.add(doc);
				}
			});
		}
		return docList;
	}

}
