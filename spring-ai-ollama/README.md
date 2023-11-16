## Ollama

Ollama lets you get up and running with large language models locally.

Refer to the official [README](https://github.com/jmorganca/ollama) to get started.

Note, installing `ollama run llama2` will download a 4GB docker image.

You can run the disabled test in `OllamaClientTests.java` to kick the tires.

### Ollama Spring Boot Starter

A starter dependency is provided for Spring AI with Ollama:

```xml
<dependency>
   <groupId>org.springframework.experimental.ai</groupId>
   <artifactId>spring-ai-ollama-spring-boot-starter</artifactId>
   <version>0.7.1-SNAPSHOT</version>
</dependency>
```

and use the spring.ai.ollama.* properties to configure it if you want to use something other than the default values.

The complete list of supported properties are:

| Property    | Description | Default |
| -------- | ----- | ----- |
| spring.ai.ollama.base-url | Base URL where Ollama API server is running. | `http://localhost:11434` |
| spring.ai.ollama.model | Language model to use. | `llama2` |
