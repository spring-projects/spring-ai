package org.springframework.ai.vectorstore;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

class Elasticsearch8VectorStoreIT extends BaseElasticsearchVectorStoreIT{

    @Container
    private static final ElasticsearchContainer elasticsearchContainer =
            new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.11.3").withEnv(
                    "xpack.security.enabled", "false");

    @Override
    protected ApplicationContextRunner getContextRunner() {
        return new ApplicationContextRunner().withUserConfiguration(TestApplication.class);
    }


    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
    public static class TestApplication extends BaseElasticsearchVectorStoreIT.TestApplication {

        @Bean
        public ElasticsearchVectorStore vectorStore(EmbeddingClient embeddingClient) {
            return new ElasticsearchVectorStore(
                    RestClient.builder(HttpHost.create(elasticsearchContainer.getHttpHostAddress())).build(),
                    embeddingClient);
        }
    }
}
