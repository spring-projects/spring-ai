
# Milvus VectorStore


[Milvus](https://milvus.io/) is yet another open source vector database; and this one has gained popularity in the data science and machine learning fields. One of Milvus’ main advantages is its robust support for vector indexing and querying. It uses state-of-the-art algorithms to speed up the search process, resulting in fast retrieval of similar vectors even when dealing with large-scale datasets.

Its popularity also stems from the fact that Milvus can be easily integrated with other popular frameworks, including `PyTorch` and `TensorFlow`, enabling seamless integration into existing machine learning workflows.

In the e-commerce industry, it can be used in recommendation systems that suggest products based on user preference. In image and video analysis, it can be used for object recognition, image similarity search, and content-based image retrieval. It is also commonly used in natural language processing for document clustering, semantic search, and question-answering systems.

## Start Milvus Store

from withing the `src/test/resources/` folder run:

```
docker-compose up
```

To clean the environment:

```
docker-compose down; rm -Rf ./volumes
```


Then connect to the vector store on http://localhost:19530 or for management http://localhost:9001 (user: `minioadmin`, pass: `minioadmin`)

## Throubleshooting

If docker complains for resources:

```
docker system prune --all --force --volumes
```