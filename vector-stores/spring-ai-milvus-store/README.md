# Introduction to Milvus

[Milvus](https://milvus.io/) is an open-source vector database that has garnered significant attention in the fields of data science and machine learning.
One of its standout features lies in its robust support for vector indexing and querying.
Milvus employs cutting-edge algorithms to accelerate the search process, making it exceptionally efficient at retrieving similar vectors, even when handling extensive datasets.

Milvus's popularity also comes from its ease of integration with popular Python based frameworks such as PyTorch and TensorFlow, allowing for seamless inclusion in existing machine learning workflows.

is yet another open source vector database; and this one has gained popularity in the data science and machine learning fields. One of Milvus’ main advantages is its robust support for vector indexing and querying.
It uses state-of-the-art algorithms to speed up the search process, resulting in fast retrieval of similar vectors even when dealing with large-scale datasets.

Its popularity also stems from the fact that Milvus can be easily integrated with other popular frameworks, including `PyTorch` and `TensorFlow`, enabling seamless integration into existing machine learning workflows.

In the e-commerce industry, Milvus is used in recommendation systems, which suggest products based on user preferences.
In image and video analysis, it excels in tasks like object recognition, image similarity search, and content-based image retrieval.
Additionally, it is commonly used in natural language processing for document clustering, semantic search, and question-answering systems.

## Starting Milvus Store

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

If docker complains about resources:

```
docker system prune --all --force --volumes
```