# Spring AI Test

The Spring AI Test module provides utilities and base classes for testing AI applications built with Spring AI.

## Features

- **BasicEvaluationTest**: A base test class for evaluating question-answer quality using AI models
- **Vector Store Testing**: Utilities for testing vector store implementations
- **Audio Testing**: Utilities for testing audio-related functionality

## BasicEvaluationTest

The `BasicEvaluationTest` class provides a framework for evaluating the quality and relevance of AI-generated answers to questions.

### Usage

Extend the `BasicEvaluationTest` class in your test classes:

```java
@SpringBootTest
public class MyAiEvaluationTest extends BasicEvaluationTest {

    @Test
    public void testQuestionAnswerAccuracy() {
        String question = "What is the capital of France?";
        String answer = "The capital of France is Paris.";
        
        // Evaluate if the answer is accurate and related to the question
        evaluateQuestionAndAnswer(question, answer, true);
    }
}
```

### Configuration

The test requires:
- A `ChatModel` bean (typically OpenAI)
- Evaluation prompt templates located in `classpath:/prompts/spring/test/evaluation/`

### Evaluation Types

- **Fact-based evaluation**: Use `factBased = true` for questions requiring factual accuracy
- **General evaluation**: Use `factBased = false` for more subjective questions

The evaluation process:
1. Checks if the answer is related to the question
2. Evaluates the accuracy/appropriateness of the answer
3. Fails the test with detailed feedback if the answer is inadequate