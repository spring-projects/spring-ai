# Azure AI Search VectorStore

This README will walk you through setting up the `AzureVectorStore`` to store document embeddings and perform similarity searches using the Azure AI Search Service.

[Azure AI Search](https://azure.microsoft.com/en-us/products/ai-services/cognitive-search) is a versatile cloud hosted cloud information retrieval system that is part of Microsoft's larger AI platform.
Among other features, it allows users to query information using vector based storage and retrieval.

## Prerequisites

1. Azure Subscription: You will need an [Azure subscription](https://azure.microsoft.com/en-us/free/) to use any Azure service.
2. Azure AI Search Service: Create an [AI Search service](https://portal.azure.com/#create/Microsoft.Search).  Once the service is created,
obtain the admin apiKey from the `Keys` section under `Settings` and retrieve the endpoint from the `Url` field under the `Overview` section.
3. (Optional) Azure OpenAI Service: Create an an Azure [OpenAI service](https://portal.azure.com/#create/Microsoft.AIServicesOpenAI).
**NOTE:** You may have to fill out a separate form to gain access to Azure Open AI services.
Once the service is created, obtain the endpoint and apiKey from the `Keys and Endpoint` section under `Resource Management`

## Configuration

On startup the `AzureVectorStore` will attempt to create a new index within your AI Search service instance.
Alternatively you create the index, manually as explained in [Appendix A](appendix_a).

To set up an AzureVectorStore, you will need the settings retrieved from the prerequisites above along with your index name:

* Azure AI Search Endpoint
* Azure AI Search Key
* (optional) Azure OpenAI API Endpoint
* (optional) Azure OpenAI API Key

You can provide these values as OS environment variables.

```bash
export 'AZURE_AI_SEARCH_API_KEY=<My AI Search API Key>'
export 'AZURE_AI_SEARCH_ENDPOINT=<My AI Search Index>'
export 'OPENAI_API_KEY=<My Azure AI API Key>' (Optional)
```

**NOTE** You can replace Azure Open AI implementation with any valid OpenAI implementation that supports the Embeddings interface.  For example, you could use Spring AIs Open AI or TransformersEmbedding implementations for embeddings instead of the Azure implementation.

## Dependencies

Add these dependencies to your project:

1. Select an Embeddings interface implementation.
You can choose between:

* OpenAI Embedding:

   ```xml
   <dependency>
       <groupId>org.springframework.experimental.ai</groupId>
       <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
       <version>0.7.1-SNAPSHOT</version>
   </dependency>
   ```

* Or Azure AI Embedding:

   ```xml
   <dependency>
     <groupId>org.springframework.experimental.ai</groupId>
     <artifactId>spring-ai-azure-openai-spring-boot-starter</artifactId>
     <version>0.7.1-SNAPSHOT</version>
   </dependency>
   ```

* Or Local Sentence Transformers Embedding:

   ```xml
   <dependency>
     <groupId>org.springframework.experimental.ai</groupId>
     <artifactId>spring-ai-transformers-embedding-spring-boot-starter</artifactId>
     <version>0.7.1-SNAPSHOT</version>
   </dependency>
   ```

2. Azure (AI Search) Vector Store

    ```xml
    <dependency>
        <groupId>org.springframework.experimental.ai</groupId>
        <artifactId>spring-ai-azure-vector-store</artifactId>
        <version>0.7.1-SNAPSHOT</version>
    </dependency>
    ```

## Sample Code

To configure an Azure `SearchIndexClient` in your application, you can use the following code:

```java
@Bean
public SearchIndexClient searchIndexClient() {
  return new SearchIndexClientBuilder().endpoint(System.getenv("AZURE_AI_SEARCH_ENDPOINT"))
    .credential(new AzureKeyCredential(System.getenv("AZURE_AI_SEARCH_API_KEY")))
    .buildClient();
}
```

To create a vector store, you can use the following code by injecting the `SearchIndexClient` bean created in the above sample along with and `EmbeddingClient` provided by Spring AI library that's implements the desired Embeddings interface.


```java
@Bean
public VectorStore vectorStore(SearchIndexClient searchIndexClient, EmbeddingClient embeddingClient) {
  return new AzureVectorStore(searchIndexClient, embeddingClient);
}
```

In your main code, create some documents

```java
List<Document> documents = List.of(
	new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!", Map.of("meta1", "meta1")),
	new Document("The World is Big and Salvation Lurks Around the Corner"),
	new Document("You walk forward facing the past and you turn back toward the future.", Map.of("meta2", "meta2")));
```

Add the documents to your vector store:

```java
vectorStore.add(List.of(document));
```

And finally, retrieve documents similar to a query:

```java
List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(5));
```

If all goes well, you should retrieve the document containing the text "Spring AI rocks!!".

## Integration With Azure OpenAI Studio Data Ingestion

Azure Open AI services provides a convenient method to upload documents into an Index as described in this Microsoft
[learning document](https://learn.microsoft.com/en-us/azure/ai-services/openai/use-your-data-quickstart?tabs=command-line&pivots=programming-language-csharp).
The `AzureVectorStore` implementation is compatible with indexes that use this methodology facilitating an *easier* way to integrate with your existing documents for the purpose of searching and integrating with the AI system.

## <a name="appendix_a" /> Appendix A: Create Vector Store Search Index

The easiest way to crate a search index manually, is to create one from a JSON document.
This can be done by clicking on the `Indexes` link under the `Search management` section.
From the Indexes page, click `+ Add index` and select `Add index (JSON)`.  In the
`Add index (JSON)` window of the right side of your screen, enter the following JSON replacing `<INDEX NAME>` with the name you would like to give your index and click
`save`.

```json
{
  "name": "<INDEX NAME>",
  "defaultScoringProfile": null,
  "fields": [
    {
      "name": "id",
      "type": "Edm.String",
      "searchable": false,
      "filterable": false,
      "retrievable": true,
      "sortable": false,
      "facetable": false,
      "key": true,
      "indexAnalyzer": null,
      "searchAnalyzer": null,
      "analyzer": null,
      "normalizer": null,
      "dimensions": null,
      "vectorSearchConfiguration": null,
      "synonymMaps": []
    },
    {
      "name": "embedding",
      "type": "Collection(Edm.Single)",
      "searchable": true,
      "filterable": false,
      "retrievable": true,
      "sortable": false,
      "facetable": false,
      "key": false,
      "indexAnalyzer": null,
      "searchAnalyzer": null,
      "analyzer": null,
      "normalizer": null,
      "dimensions": 1536, // set the dimensions for the configured Embedding Client. It defaults to to OpenAI's 1536 size.
      "vectorSearchConfiguration": "default",
      "synonymMaps": []
    },
    {
      "name": "content",
      "type": "Edm.String",
      "searchable": true,
      "filterable": false,
      "retrievable": true,
      "sortable": false,
      "facetable": false,
      "key": false,
      "indexAnalyzer": null,
      "searchAnalyzer": null,
      "analyzer": null,
      "normalizer": null,
      "dimensions": null,
      "vectorSearchConfiguration": null,
      "synonymMaps": []
    },
    {
      "name": "metadata",
      "type": "Edm.String",
      "searchable": true,
      "filterable": true,
      "retrievable": true,
      "sortable": true,
      "facetable": true,
      "key": false,
      "indexAnalyzer": null,
      "searchAnalyzer": null,
      "analyzer": null,
      "normalizer": null,
      "dimensions": null,
      "vectorSearchConfiguration": null,
      "synonymMaps": []
    }
  ],
  "scoringProfiles": [],
  "corsOptions": null,
  "suggesters": [],
  "analyzers": [],
  "normalizers": [],
  "tokenizers": [],
  "tokenFilters": [],
  "charFilters": [],
  "encryptionKey": null,
  "semantic": null,
  "vectorSearch": {
    "algorithmConfigurations": [
      {
        "name": "default",
        "kind": "hnsw",
        "hnswParameters": {
          "metric": "cosine",
          "m": 4,
          "efConstruction": 400,
          "efSearch": 1000
        },
        "exhaustiveKnnParameters": null
      }
    ]
  }
}

```
