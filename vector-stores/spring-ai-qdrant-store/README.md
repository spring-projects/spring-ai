# Qdrant Vector Store

[Reference Documentation](https://docs.spring.io/spring-ai/reference/api/vectordbs/qdrant.html)

## Run locally

### Accessing the Web UI

First, run the Docker container:

```
docker run -p 6333:6333 -p 6334:6334 \
    -v $(pwd)/qdrant_storage:/qdrant/storage:z \
    qdrant/qdrant
```

### Security: Adding API Key to Qdrant Container
To enhance security, you can add an API key to your Qdrant container using the environment variable.

```
-e QDRANT__SERVICE__API_KEY=<your_generated_api_key_here>
```

This ensures that only authorized users with the correct API key can access the Qdrant service.

The GUI is available at http://localhost:6333/dashboard

## Qdrant references

- https://qdrant.tech/documentation/interfaces/
- https://github.com/qdrant/java-client
