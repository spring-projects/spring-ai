# Ollama

Ollama lets you get up and running with large language models locally.

Refer to the official [README](https://github.com/jmorganca/ollama) to get started.

Note, installing `ollama run llama2` will download a 4GB docker image.

You can run the disabled test in `OllamaClientTests.java` to kick the tires.

## How to use

Add the `spring-ai-ollama` dependency to your project's pom:

```xml
<dependency>
   <groupId>org.springframework.experimental.ai</groupId>
   <artifactId>spring-ai-ollama</artifactId>
   <version>0.7.1-SNAPSHOT</version>
</dependency>
```

then create an client and use generate response:

```java
var ollamaClient = new OllamaClient("http://127.0.0.1:11434", "llama2",
      ollamaResult -> {
         if (ollamaResult.getDone()) {
            ....
         }
      });

AiResponse aiResponse = ollamaClient.generate(new Prompt("Hello"));
```

### Spring Boot Starter

For convenience you can opt for the Ollama Boot starter.
For this add the following dependency:

```xml
<dependency>
   <groupId>org.springframework.experimental.ai</groupId>
   <artifactId>spring-ai-ollama-spring-boot-starter</artifactId>
   <version>0.7.1-SNAPSHOT</version>
</dependency>
```

and use the `spring.ai.ollama.*` properties to configure it if you want to use something other than the default values.

The complete list of supported properties are:

| Property    | Description | Default |
| -------- | ----- | ----- |
| spring.ai.ollama.base-url | Base URL where Ollama API server is running. | `http://localhost:11434` |
| spring.ai.ollama.model | Language model to use. | `llama2` |
