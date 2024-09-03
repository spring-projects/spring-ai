# Structured Output

* [Documentation](https://docs.spring.io/spring-ai/reference/concepts.html#_output_parsing)
* [Usage examples](https://github.com/spring-projects/spring-ai/blob/main/models/spring-ai-openai/src/test/java/org/springframework/ai/openai/chat/OpenAiChatModelIT.java)

The output of AI models traditionally arrives as a text, even if you ask for the reply to be in JSON.
It may be a correct JSON, but it isn’t a JSON data structure.
It is just a string.
Also, asking "for JSON" as part of the prompt isn’t 100% accurate.

This intricacy has led to the emergence of a specialized field involving the creation of prompts to yield the intended output, followed by converting the resulting simple string into a usable data structure for application integration.

Structure output conversion employs meticulously crafted prompts, often necessitating multiple interactions with the model to achieve the desired formatting.
