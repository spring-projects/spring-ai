# Local Transformers Embedding Client

The `TransformersEmbeddingClient` is a `EmbeddingClient` implementation that computes, locally, [sentence embeddings](https://www.sbert.net/examples/applications/computing-embeddings/README.html#sentence-embeddings-with-transformers) using a selected [sentence transformer](https://www.sbert.net/).

It uses [pre-trained](https://www.sbert.net/docs/pretrained_models.html) transformer models, serialized into the [Open Neural Network Exchange (ONNX)](https://onnx.ai/) format.

The [Deep Java Library](https://djl.ai/) and the Microsoft [ONNX Java Runtime](https://onnxruntime.ai/docs/get-started/with-java.html) libraries are applied to run the ONNX models and compute the embeddings in Java.

## Serialize the Tokenizer and the Transformer Model

To run things in Java, we need to serialize the Tokenizer and the Transformer Model into ONNX format.

### Serialize with optimum-cli

One, quick, way to achieve this, is to use the [optimum-cli](https://huggingface.co/docs/optimum/exporters/onnx/usage_guides/export_a_model#exporting-a-model-to-onnx-using-the-cli) command line tool.

Following snippet prepares a python virtual environment, installs the required packages and serializes (e.g. exports) specified model using `optimum-cli` :

```bash
python3 -m venv venv
source ./venv/bin/activate
(venv) pip install --upgrade pip
(venv) pip install optimum onnx onnxruntime
(venv) optimum-cli export onnx --model sentence-transformers/all-MiniLM-L6-v2 onnx-output-folder
```

The snippet exports the [sentence-transformers/all-MiniLM-L6-v2](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2) transformer into the `onnx-output-folder` folder. Later includes the `tokenizer.json` and `model.onnx` files used by the embedding client.

In place of the all-MiniLM-L6-v2 you can pick any huggingface transformer identifier or provide direct file path.

## Using the ONNX models

Add the `transformers-embedding` project to your maven dependencies:

```xml
<dependency>
  <groupId>org.springframework.experimental.ai</groupId>
  <artifactId>transformers-embedding</artifactId>
  <version>0.7.1-SNAPSHOT</version>
</dependency>
```

then create a new `TransformersEmbeddingClient` instance and use the `setTokenizerResource(tokenizerJsonUri)` and `setModelResource(modelOnnxUri)` methods to set the URIs  of the exported `tokenizer.json` and `model.onnx` files. (`classpath:`, `file:` or `https:` URI schemas are supported).

If the model is not explicitly set, `TransformersEmbeddingClient` defaults to [sentence-transformers/all-MiniLM-L6-v2](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2):

|     |  |
| -------- | ------- |
| Dimensions  |384    |
| Avg. performance | 58.80     |
| Speed    | 14200 sentences/sec    |
| Size    | 80MB    |

Following snippet illustrates how to use the `TransformersEmbeddingClient` manually:

```java
TransformersEmbeddingClient embeddingClient = new TransformersEmbeddingClient();

// (optional) defaults to classpath:/onnx/all-MiniLM-L6-v2/tokenizer.json
embeddingClient.setTokenizerResource("classpath:/onnx/all-MiniLM-L6-v2/tokenizer.json");

// (optional) defaults to classpath:/onnx/all-MiniLM-L6-v2/model.onnx
embeddingClient.setModelResource("classpath:/onnx/all-MiniLM-L6-v2/model.onnx");

// (optional) defaults to ${java.io.tmpdir}/spring-ai-onnx-model
// Only the http/https resources are cached by default.
embeddingClient.setResourceCacheDirectory("/tmp/onnx-zoo");

embeddingClient.afterPropertiesSet();

List<List<Double>> embeddings = embeddingClient.embed(List.of("Hello world", "World is big"));

```

Note that when created manually you have to call the `afterPropertiesSet()` after setting the properties and before using the client.

The first `embed()` call downloads the the large ONNX model and caches it on the local file system.
Therefore the first call might take longer than usual.
Use the `#setResourceCacheDirectory(<path>)` to set the local folder where the ONNX models as stored.
The default cache folder is `${java.io.tmpdir}/spring-ai-onnx-model`.

It is more convenient (and preferred) to create the TransformersEmbeddingClient as a `Bean`.
Then you don't have to call the `afterPropertiesSet()` manually.

```java
@Bean
public EmbeddingClient embeddingClient() {
   return new TransformersEmbeddingClient();
}
```

## Transformers Embedding Spring Boot Starter.

You can bootstrap and auto-wire the `TransformersEmbeddingClient` with following boot starer:

```xml
<dependency>
   <groupId>org.springframework.experimental.ai</groupId>
   <artifactId>spring-ai-transformers-embedding-spring-boot-starter</artifactId>
   <version>0.7.1-SNAPSHOT</version>
</dependency>
```

and use the `spring.ai.embedding.transformer.*` properties to configure it.

For example add this to your application.properties to configure with the [intfloat/e5-small-v2](https://huggingface.co/intfloat/e5-small-v2) text embedding model:

```
spring.ai.embedding.transformer.onnx.modelUri=https://huggingface.co/intfloat/e5-small-v2/resolve/main/model.onnx
spring.ai.embedding.transformer.tokenizer.uri=https://huggingface.co/intfloat/e5-small-v2/raw/main/tokenizer.json
```

The complete list of supported properties are:

| Property    | Description | Default |
| -------- | ------- | ------- |
| spring.ai.embedding.transformer.tokenizer.uri  | URI of a pre-trained HuggingFaceTokenizer created by the ONNX engine (e.g. tokenizer.json).   | onnx/all-MiniLM-L6-v2/tokenizer.json |
| spring.ai.embedding.transformer.tokenizer.options  | HuggingFaceTokenizer options such as '`addSpecialTokens`', '`modelMaxLength`', '`truncation`', '`padding`', '`maxLength`', '`stride`' and '`padToMultipleOf`'. Leave empty to fallback to the defaults. | empty |
| spring.ai.embedding.transformer.cache.enabled  | Enable remote Resource caching.  | true |
| spring.ai.embedding.transformer.cache.directory  | Directory path to cache remote resources, such as the ONNX models   | ${java.io.tmpdir}/spring-ai-onnx-model  |
| spring.ai.embedding.transformer.onnx.modelUri  | Existing, pre-trained ONNX model.  | onnx/all-MiniLM-L6-v2/model.onnx |
| spring.ai.embedding.transformer.onnx.gpuDeviceId  |  The GPU device ID to execute on. Only applicable if >= 0. Ignored otherwise. |  -1 |
| spring.ai.embedding.transformer.metadataMode  |  Specifies what parts of the Documents content and metadata will be used for computing the embeddings.  |  NONE |

