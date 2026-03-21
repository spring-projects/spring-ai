# Spring AI Test

The Spring AI Test module provides utilities and base classes for testing AI applications built with Spring AI.

## Features

- **Evaluation Testing**: Resources for tests using the `Evaluator` API
- **Vector Store Testing**: Utilities for testing vector store implementations
- **Audio Testing**: Utilities for testing audio-related functionality

## Evaluation Testing

The module provides resources for writing evaluation-oriented tests with the `Evaluator` API.

### Usage

Use an `Evaluator` implementation in your test classes:

```java
@SpringBootTest
class MyAiEvaluationTest {

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Test
    void testQuestionAnswerAccuracy() {
        String question = "What is the capital of France?";
        String answer = "The capital of France is Paris.";
        List<Document> documents = List.of(new Document("Paris is the capital of France."));

        Evaluator evaluator = new FactCheckingEvaluator(chatClientBuilder);
        EvaluationRequest evaluationRequest = new EvaluationRequest(question, documents, answer);

        assertThat(evaluator.evaluate(evaluationRequest).isPass()).isTrue();
    }
}
```

### Configuration

The test requires:
- A `ChatModel` bean (typically OpenAI)
- Evaluation prompt templates located in `classpath:/prompts/spring/test/evaluation/`

### Evaluation Types

- **Relevancy evaluation**: Use `RelevancyEvaluator` for answer quality in context-driven flows such as RAG
- **Fact-checking evaluation**: Use `FactCheckingEvaluator` for grounded factuality checks against provided context
- **Custom evaluation**: Implement `Evaluator` when you need your own evaluation strategy

The evaluation process:
1. Prepares the user question, supporting context, and model answer
2. Evaluates the result with an `Evaluator`
3. Asserts on the returned `EvaluationResponse`
