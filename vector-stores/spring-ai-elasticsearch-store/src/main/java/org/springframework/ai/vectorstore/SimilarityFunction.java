package org.springframework.ai.vectorstore;

/**
 * https://www.elastic.co/guide/en/elasticsearch/reference/master/dense-vector.html
 * max_inner_product is currently not supported because the distance value is not
 * normalized and would not comply with the requirement of being between 0 and 1
 *
 * @author Laura Trotta
 * @since 1.0.0
 */
public enum SimilarityFunction {

	l2_norm, dot_product, cosine

}
