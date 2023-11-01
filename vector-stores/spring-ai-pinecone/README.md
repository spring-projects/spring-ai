# Pinecone VectorStore

[Pinecone](https://www.pinecone.io/) is a popular cloud based Vector database.

You need to register with a valid [pinecone account](https://app.pinecone.io/).
Then create a project, index and optionally a new api-key. You would need those to crate a new `PineconeVectorStore`:

```java
EmbeddingClient embeddingClient = ...;

PineconeVectorStoreConfig pineconeConfig =
	PineconeVectorStoreConfig.builder()
		.withApiKey("PINECONE_API_KEY")
		.withEnvironment("PINECONE_ENVIRONMENT")
		.withProjectId("PINECONE_PROJECT_ID")
		.withIndexName("PINECONE_INDEX_NAME")
		.withNamespace("PINECONE_NAMESPACE")
		.build();

VectorStore vectorStore =
	new PineconeVectorStore(pineconeConfig, embeddingClient);
```

NOTE: You can use the free tier (gcp-starter) account, but as it doesn't support namespaces, use an empty namespace (e.g. "").