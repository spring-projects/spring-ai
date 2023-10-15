package org.springframework.ai.vectorstore;

import org.bson.Document;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperationContext;

import java.util.List;

class VectorSearchAggregation implements AggregationOperation {

    private final int count;
    private final List<Double> embeddings;



    public VectorSearchAggregation(int count, List<Double> embeddings){
        this.count = count;
        this.embeddings = embeddings;
    }
    @Override
    public org.bson.Document toDocument(AggregationOperationContext context) {
       var doc =  new Document("$search",
                new Document("index", "default")
                        .append("path", "embedding")
                        .append("numCandidates", 100)
                        .append("limit",count)
                        .append("queryVector", embeddings));
        return context.getMappedObject(doc);
    }
}