# HuggingFace Inference Endpoints with Spring AI

HuggingFace Inference Endpoints allow you to deploy and serve machine learning models in the cloud, making them accessible via an API. Further details on HuggingFace Inference Endpoints can be found [here](https://huggingface.co/docs/inference-endpoints/index).

## Prerequisites

You should get your HuggingFace API key and set it as an environment variable

```shell
export HUGGINGFACE_API_KEY=your_api_key_here
```

Note, there is not yet a Spring Boot Starter for this client implementation.

Obtain the endpoint URL of the Inference Endpoint.
You can find this on the Inference Endpoint's UI [here](https://ui.endpoints.huggingface.co/).


## Making a call to the model

```java
HuggingfaceAiClient client = new HuggingfaceAiClient(apiKey, basePath);
Prompt prompt = new Prompt("Your text here...");
AiResponse response = client.generate(prompt);
System.out.println(response.getGeneration().getText());
```

## Example

Using the example found [here](https://www.promptingguide.ai/models/mistral-7b)

```java
String mistral7bInstruct = """
        [INST] You are a helpful code assistant. Your task is to generate a valid JSON object based on the given information:
        name: John
        lastname: Smith
        address: #1 Samuel St.
        Just generate the JSON object without explanations:
        [/INST]""";
Prompt prompt = new Prompt(mistral7bInstruct);
AiResponse aiResponse = huggingfaceAiClient.generate(prompt);
System.out.println(response.getGeneration().getText());
```

Will produce the output

````
```json
{
    "name": "John",
    "lastname": "Smith",
    "address": "#1 Samuel St."
}
```
````
Note the response itself is in Markdown format.