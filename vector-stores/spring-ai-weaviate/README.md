# Weaviate VectorStore

This readme will walk you through setting up the Weaviate VectorStore to store document embeddings and perform similarity searches.

## What is Weaviate?

[Weaviate](https://weaviate.io/) is an open-source vector database.
It allows you to store data objects and vector embeddings from your favorite ML-models, and scale seamlessly into billions of data objects.
It gives you the tools to store document embeddings, content and metadata and to search through those embeddings including metadata filtering.

## Prerequisites

1. `EmbeddingClient` instance to compute the document embeddings. Several options are available:

   - `Transformers Embedding` - computes the embedding in your, local environment. Follow the [Transformers Embedding](../../embedding-clients/transformers-embedding/) instructions.
   - `OpenAI Embedding` - uses the OpenAI embedding endpoint. You need to create an account at [OpenAI Signup](https://platform.openai.com/signup) and generate the api-key token at [API Keys](https://platform.openai.com/account/api-keys).
   - You can also use the `Azure OpenAI Embedding` or the `PostgresML Embedding Client`.

2. `Weaviate cluster`. You can a cluster, locally, in a Docker container ([Local Weaviate](#appendix_a)) or create a [Weaviate Cloud Service](https://console.weaviate.cloud/). For later you need to create an weaviate account spin a cluster and get your access api-key from the [dashboard details](https://console.weaviate.cloud/dashboard).

On startup the `WeaviateVectorStore` creates the required `SpringAiWeaviate` object schema (if such is not already provisioned).

## Dependencies

Add these dependencies to your project:

1. Embedding Client boot starter, required for calculating embeddings.

   - Transformers Embedding (Local)

      ```xml
      <dependency>
         <groupId>org.springframework.experimental.ai</groupId>
         <artifactId>spring-ai-transformers-embedding-spring-boot-starter</artifactId>
         <version>0.7.1-SNAPSHOT</version>
      </dependency>
      ```

      follow the [transformers-embedding](../../embedding-clients/transformers-embedding/README.md) instructions.

   - or OpenAI (Cloud)

      ```xml
      <dependency>
         <groupId>org.springframework.experimental.ai</groupId>
         <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
         <version>0.7.1-SNAPSHOT</version>
      </dependency>
      ```

      you'll need to provide your OpenAI API Key. Set it as an environment variable like so:

      ```bash
      export SPRING_AI_OPENAI_API_KEY='Your_OpenAI_API_Key'
      ```

2. Weaviate VectorStore.

   ```xml
   <dependency>
      <groupId>org.springframework.experimental.ai</groupId>
      <artifactId>spring-ai-weaviate-store</artifactId>
      <version>0.7.1-SNAPSHOT</version>
   </dependency>
   ```

## <a name="usage"/> Usage </a>

Create a WeaviateVectorStore instance connected to local Weaviate cluster:

```java
   @Bean
   public VectorStore vectorStore(EmbeddingClient embeddingClient) {
      WeaviateVectorStoreConfig config = WeaviateVectorStoreConfig.builder()
         .withScheme("http")
         .withHost("localhost:8080")
         // Define the metadata fields to be used
         // in the similarity search filters.
         .withFilterableMetadataFields(List.of(
            MetadataField.text("country"),
            MetadataField.number("year"),
            MetadataField.bool("active")))
         // Consistency level can be: ONE, QUORUM or ALL.
         .withConsistencyLevel(ConsistentLevel.ONE)
         .build();

      return new WeaviateVectorStore(config, embeddingClient);
   }
```

> [!NOTE]
> You must list explicitly all metadata field names and types (`BOOLEAN`, `TEXT` or `NUMBER`) for any metadata key used in filter expression.
>The `withFilterableMetadataKeys` above registers filterable metadata fields: `country` of type `TEXT`, `year` of type `NUMBER` and `active` of type `BOOLEAN`.
>
> If the filterable metadata fields is expanded with new entires, you have to (re)upload/update the documents with this metadata.
>
> You can use the following, Weaviate [system metadata](https://weaviate.io/developers/weaviate/api/graphql/filters#special-cases) fields without explicit definition: `id`, `_creationTimeUnix` and `_lastUpdateTimeUnix`.

Then yn your main code, create some documents

```java
List<Document> documents = List.of(
   new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!", Map.of("country", "UK", "active", true, "year", 2020)),
   new Document("The World is Big and Salvation Lurks Around the Corner", Map.of()),
   new Document("You walk forward facing the past and you turn back toward the future.", Map.of("country", "NL", "active", false, "year", 2023)));
```

Add the documents to your vector store:

```java
vectorStore.add(List.of(document));
```

And finally, retrieve documents similar to a query:

```java
List<Document> results = vectorStore.similaritySearch(
      SearchRequest
         .query("Spring")
         .withTopK(5));
```

If all goes well, you should retrieve the document containing the text "Spring AI rocks!!".

### Metadata filtering

You can leverage the generic, portable [metadata filters](https://docs.spring.io/spring-ai/reference/api/vectordbs.html#_metadata_filters) with WeaviateVectorStore as well.

For example you can use either the text expression language:

```java
vectorStore.similaritySearch(
   SearchRequest
      .query("The World")
      .withTopK(TOP_K)
      .withSimilarityThreshold(SIMILARITY_THRESHOLD)
      .withFilterExpression("country in ['UK', 'NL'] && year >= 2020"));
```

or programmatically using the expression DSL:

```java
FilterExpressionBuilder b = Filter.builder();

vectorStore.similaritySearch(
    SearchRequest
      .query("The World")
      .withTopK(TOP_K)
      .withSimilarityThreshold(SIMILARITY_THRESHOLD)
      .withFilterExpression(b.and(
         b.in("country", "UK", "NL"),
         b.gte("year", 2020)).build()));
```

The, portable, filter expressions get automatically converted into the proprietary Weaviate [where filters](https://weaviate.io/developers/weaviate/api/graphql/filters).
For example the following, portable, filter expression

```sql
country in ['UK', 'NL'] && year >= 2020
```

is converted into Weaviate, GraphQL, [where filter expression](https://weaviate.io/developers/weaviate/api/graphql/filters):

```graphQL
operator:And
   operands:
      [{
         operator:Or
         operands:
            [{
               path:["meta_country"]
               operator:Equal
               valueText:"UK"
            },
            {
               path:["meta_country"]
               operator:Equal
               valueText:"NL"
            }]
      },
      {
         path:["meta_year"]
         operator:GreaterThanEqual
         valueNumber:2020
      }]
```

## <a name="appendix_a"/> Appendix A: Run Weaviate cluster in docker container </a>

Start Weaviate in a docker container:

```bash
docker run -it --rm --name weaviate -e AUTHENTICATION_ANONYMOUS_ACCESS_ENABLED=true -e PERSISTENCE_DATA_PATH=/var/lib/weaviate -e QUERY_DEFAULTS_LIMIT=25 -e DEFAULT_VECTORIZER_MODULE=none -e CLUSTER_HOSTNAME=node1 -p 8080:8080 semitechnologies/weaviate:1.22.4
```

Starts a Weaviate cluster at http://localhost:8080/v1 with scheme=`http`, host=`localhost:8080` and apiKey=`""`. Then follow the [usage instructions](#usage).
