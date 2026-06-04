# Spring AI Test

The Spring AI Test module provides reusable base classes and utilities for testing AI
applications and Spring AI provider integrations (chat models, vector stores, tool calling,
chat options, observability).

It is published as a `test`-scoped artifact and is consumed by the Spring AI modules
themselves, so the same conformance tests that validate the built-in providers are available
when you implement your own.

> For the public, end-user evaluation guide (writing assertions over generative responses with
> `RelevancyEvaluator` / `FactCheckingEvaluator`, or your own `Evaluator`), see the reference
> documentation:
> [Model Evaluation](https://docs.spring.io/spring-ai/reference/api/testing.html).

## What's inside

| Class | Purpose |
|-------|---------|
| `org.springframework.ai.test.vectorstore.BaseVectorStoreTests` | Base class that contributes ready-made delete tests (by id, by string filter, by `Filter.Expression`) to any `VectorStore` implementation. |
| `org.springframework.ai.test.chat.client.advisor.AbstractToolCallAdvisorIT` | Conformance suite for tool/function calling through `ChatClient`, for both `call` and `stream`, with and without external chat memory. |
| `org.springframework.ai.test.chat.client.advisor.AbstractToolCallAdvisorAutoRegistrationIT` | Variant that exercises automatic tool registration. |
| `org.springframework.ai.test.chat.client.advisor.MockWeatherService` | The canonical mock tool used by the tool-calling suites (San Francisco = 30°C, Tokyo = 10°C, Paris = 15°C). |
| `org.springframework.ai.test.options.AbstractChatOptionsTests` | Generic tests that verify a `ChatOptions` implementation builds fresh instances and mutates correctly. |
| `org.springframework.ai.test.vectorstore.ObservationTestUtil` | Asserts that a vector store operation produced the expected Micrometer observation. |
| `org.springframework.ai.test.CurlyBracketEscaper` | Null-safe helpers to escape/unescape `{` and `}` so user content does not collide with template placeholders. |
| `org.springframework.ai.utils.AudioPlayer` | Small helper for audio-related tests. |

## Testing a custom VectorStore

Extend `BaseVectorStoreTests` and implement `executeTest`, which hands a live `VectorStore`
to the inherited test logic. A typical implementation stands up an application context (here
with Spring Boot's `ApplicationContextRunner`) and passes the bean through:

```java
class MyVectorStoreIT extends BaseVectorStoreTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestApplication.class);

    @Override
    protected void executeTest(Consumer<VectorStore> testFunction) {
        this.contextRunner.run(context -> {
            VectorStore vectorStore = context.getBean(VectorStore.class);
            testFunction.accept(vectorStore);
        });
    }
}
```

The base class then runs `deleteById`, `deleteWithStringFilterExpression` and
`deleteByFilter` against your store. Override `createDocument(String country, Integer year)`
if your store needs documents shaped a particular way.

## Testing tool/function calling

Extend `AbstractToolCallAdvisorIT` and return your `ChatModel` from `getChatModel()`. The
suite uses the shared `MockWeatherService` and runs the full matrix of `call`/`stream`
scenarios (nested `CallTests` and `StreamTests`):

```java
class MyToolCallAdvisorIT extends AbstractToolCallAdvisorIT {

    @Override
    protected ChatModel getChatModel() {
        return MyChatModel.builder()
            .options(MyChatOptions.builder()
                .apiKey(System.getenv("MY_API_KEY"))
                .model("my-default-model")
                .build())
            .build();
    }
}
```

## Testing ChatOptions

Extend `AbstractChatOptionsTests<O, B>` to verify that your options type returns new
instances from its builder and supports `mutate()`:

```java
class MyChatOptionsTests extends AbstractChatOptionsTests<MyChatOptions, MyChatOptions.Builder> {

    @Override
    protected Class<MyChatOptions> getConcreteOptionsClass() {
        return MyChatOptions.class;
    }

    @Override
    protected MyChatOptions.Builder readyToBuildBuilder() {
        return MyChatOptions.builder().model("my-default-model").maxTokens(500);
    }
}
```

## Other utilities

- **`ObservationTestUtil`** — after running a vector store operation against a
  `TestObservationRegistry`, assert the observation was recorded:

  ```java
  ObservationTestUtil.assertObservationRegistry(observationRegistry,
      VectorStoreProvider.PG_VECTOR, VectorStoreObservationContext.Operation.ADD);
  ```

- **`CurlyBracketEscaper`** — escape user-provided text before it reaches a prompt template
  so literal braces are not treated as placeholders:

  ```java
  String safe = CurlyBracketEscaper.escapeCurlyBrackets(userInput);
  ```
