package org.springframework.ai.autoconfigure.vectorstore.hanadb;

import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.ai.autoconfigure.vectorstore.hanadb.HanaCloudVectorStoreProperties;

/**
 * @author Rahul Mittal
 */
public class HanaCloudVectorStorePropertiesTest {

    @Test
    public void testHanaCloudVectorStoreProperties() {
        var props = new HanaCloudVectorStoreProperties();
        props.setTableName("CRICKET_WORLD_CUP");
        props.setTopK(5);

        Assertions.assertEquals("CRICKET_WORLD_CUP", props.getTableName());
        Assertions.assertEquals(5, props.getTopK());
    }
}
