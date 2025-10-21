# OpenAI Java API Library

This is the official OpenAI Java SDK from OpenAI, which provides integration with OpenAI's services, including Microsoft Foundry.

[OpenAI Java API Library GitHub repository](https://github.com/openai/openai-java)

## Authentication

This module will try to automatically detect if you're using OpenAI,
Microsoft Foundry, or GitHub Models based on the provided base URL.

Generic authentication is done using a URL and an API Key, such as:

```java
OpenAiSdkChatOptions options = OpenAiSdkChatOptions.builder()
				.baseUrl("https://<my-deployment-url>.openai.microsoftFoundry.com/")
				.apiKey("<my-api-key>")
				.build();
```

Instead of providing the URL and API Key programmatically, you can also set them
using environment variables, using the keys below:

```properties
OPENAI_BASE_URL=https://<my-deployment-url>.openai.microsoftFoundry.com/
OPENAI_API_KEY=<my-api-key>
```

### Using OpenAI

If you are using OpenAI, the base URL doesn't need to be set, as it's the default
`https://api.openai.com/v1`  :

```properties
OPENAI_BASE_URL=https://api.openai.com/v1 # Default value, can be omitted
OPENAI_API_KEY=<my-api-key>
```

### Using Microsoft Foundry

Microsoft Foundry will be automatically detected when using a Microsoft Foundry URL.
It can be forced if necessary by setting the `microsoftFoundry` configuration property to `true`.

Here's an example using environment variables:

```properties
OPENAI_BASE_URL=https://<my-deployment-url>.openai.microsoftFoundry.com/
OPENAI_API_KEY=<my-api-key>
```

With Microsoft Foundry, you can also choose to use passwordless authentication,
without providing an API key. This is more secure, and is recommended approach
when running on Azure.

To do so, you need to add the optional `com.azure:azure-identity` 
dependency to your project. For example with Maven:

```xml
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-identity</artifactId>
</dependency>
```

### Using GitHub Models

GitHub Models will be automatically detected when using the GitHub Models base URL.
It can be forced if necessary by setting the `gitHubModels` configuration property to `true`.

To authenticate, you'll need to create a GitHub Personal Access Token (PAT) with the `models:read` scope.

Here's an example using environment variables:

```properties
OPENAI_BASE_URL=https://models.github.ai/inference;
OPENAI_API_KEY=github_pat_XXXXXXXXXXX
```

## Logging

As this module is built on top of the OpenAI Java SDK, you can enable logging
by setting the following environment variable:

```properties
OPENAI_LOG=debug
```
