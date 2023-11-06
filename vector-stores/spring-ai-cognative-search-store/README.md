# Azure Cognitive Search VectorStore

This README will walk you through setting up the Cognitive VectorStore to store document embeddings and perform similarity searches.

[Azure Cognitive Search](https://azure.microsoft.com/en-us/products/ai-services/cognitive-search) is a versatile cloud hosted cloud information retrieval system
that is part of Microsoft's larger AI platform.  Among other features, it allows users to query information using vector based storage
and retrieval.

## Prerequisites

1. Azure Subscription: You will need an [Azure subscription](https://azure.microsoft.com/en-us/free/) to use any Azure service. 
2. Azure OpenAI Service: Create an an Azure [OpenAI service](https://portal.azure.com/#create/Microsoft.CognitiveServicesOpenAI).  **NOTE:** You may have
to fill out a separate form to gain access to Azure Open AI services.  Once the service is created, obtain the endpoint and apiKey from the 
`Keys and Endpoint` section under `Resource Management`
3. Azure Cognitive Search Service: Create an an Cognitive [Search service](https://portal.azure.com/#create/Microsoft.Search).  Once the service is created, 
obtain the admin apiKey from the `Keys` section under `Settings` and retrieve the endpoint from the `Url` field under the `Overview` section.

## Configuration


You will need to generate a new index within your Cognitive Search service instance.  The easiest way to do this is to create one from a JSON document.  This can be
done by clicking on the `Indexes` link under the `Search management` section.  From the Indexes page, click `+ Add index` and select `Add index (JSON)`.  In the 
`Add index (JSON)` window of the right side of your screen, enter the following JSON replacing `<INDEX NAME>` with the name you would like to give your index and click
`save`.

```
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
      "name": "contentVector",
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
      "dimensions": 1536,
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

To set up an AzureCognitiveSearchVectorStore, you will need the settings retrieved from the prerequisites above along with your index name:

* Azure OpenAI API Endpoint
* Azure OpenAI API Key
* Azure Cognitive Search Endpoint
* Azure Cognitive Search Key
* Azure Cognitive Index Name

You can provide these values to an application using any valid Spring configuration method.  For example, you could provide an `application.yaml` file with the
following settings:

```
spring:
  ai:
    azure:
      openai:
        api-key: <My Azure AI API Key>
        endpoint: <My Azure AI Endpoint>
      cognitive-search: 
        endpoint: <My Cognitive Search Endpoint>
        api-key: <My Cognitive Search API Key>
        index: <My Cognitive Search Index> 
```

You could also provide these as OS environment variables.  

```bash
export 'SPRING_AI_AZURE_OPENAI_API_KEY=<My Azure AI API Key>'
export 'SPRING_AI_AZURE_OPENAI_ENDPOINT=<My Azure AI Endpoint>'
export 'SPRING_AI_AZURE_COGNITIVE_SEARCH_API_KEY=<My Cognitive Search API Key>'
export 'SPRING_AI_AZURE_COGNITIVE_SEARCH_ENDPOINT=<My Cognitive Search Index>'
export 'SPRING_AI_AZURE_COGNITIVE_SEARCH_INDEX=<My Cognitive Search Index>'
```

**NOTE** You can replace Azure Open AI implementation with any valid OpenAI implementation that supports the Embeddings interface.  For example, you could use Spring AIs
Open AI implementation for embeddings instead of the Azure implementation.

## Dependencies

Add these dependencies to your project:

1. Select an Embeddings interface implementation.

**Open AI implementation**

```xml
    <dependency>
        <groupId>org.springframework.experimental.ai</groupId>
        <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
        <version>0.7.0-SNAPSHOT</version>
    </dependency>
```

**Azure AI implementation**

```xml
	<dependency>
		<groupId>org.springframework.experimental.ai</groupId>
		<artifactId>spring-ai-azure-openai-spring-boot-starter</artifactId>
		<version>${parent.version}</version>
		<scope>test</scope>
	</dependency>
```

2. Cognitive Search Vector Store

```xml
    <dependency>
        <groupId>org.springframework.experimental.ai</groupId>
        <artifactId>spring-ai-cognitive-search-store</artifactId>
        <version>0.7.0-SNAPSHOT</version>
    </dependency>
```

## Sample Code

To configure an Azure `SearchClient` in your application, you can use the following code.  Notice that the library contains an `AzureSearchClientProperties` class
to help facilitate obtaining configuration properties from Spring config.  You can use one of the methods in the `Configuration` section of this document to set
the necessary Azure configuration properties or any other valid Spring configuration method.

```java
@Bean
public SearchClient searchClient(AzureSearchClientProperties props) {

	return new SearchClientBuilder().endpoint(props.getEndpoint())
		.credential(new AzureKeyCredential(props.getApiKey()))
		.indexName(props.getIndex())
		.buildClient();
}
```

To create a vector store, you can use the following code by injecting the SearchClient bean created in the above sample along with an
`EmbeddingClient` provided by Spring AI library that's implements the desired Embeddings interface.


```java
@Bean
public VectorStore vectorStore(SearchClient searchClient, EmbeddingClient embeddingClient) {
	return new AzureCognitiveSearchVectorStore(searchClient, embeddingClient);
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
List<Document> results = vectorStore.similaritySearch("Spring", 5);
```

If all goes well, you should retrieve the document containing the text "Spring AI rocks!!".

## Integration With Azure OpenAI Studio Data Ingestion

Azure Open AI services provides a convienint method to upload documents into an Index as described in this Microsoft 
[learning document](https://learn.microsoft.com/en-us/azure/ai-services/openai/use-your-data-quickstart?tabs=command-line&pivots=programming-language-csharp).  The
`AzureCognitiveSearchVectorStore` implementation is compatible with indexes that use this methodology facilitating an *easier* way to integrate with your existing documents
for the purpose of searching and integrating with the AI system.


