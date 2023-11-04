package org.springframework.ai.vectorstore;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.aggregation.Aggregation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;



class VectorSearchAggregationTest {


    @Test
    void toDocument() {
        var aggregation = new VectorSearchAggregation(10, List.of(1.0,2.0,3.0));
        var doc = aggregation.toDocument(Aggregation.DEFAULT_CONTEXT);
        assertEquals(1, doc.size());
        var search = (Document) doc.get("$search");
        assertEquals(5, search.size());
        assertEquals("default", search.get("index"));
        assertEquals("embedding", search.get("path"));
        assertEquals(100, search.get("numCandidates"));
        assertEquals(10, search.get("limit"));
        assertEquals(List.of(1.0,2.0,3.0), search.get("queryVector"));

    }
}