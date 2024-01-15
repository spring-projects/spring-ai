package org.springframework.ai.generative;

import java.util.List;

public interface GenerativeResponse<T> {

	T getGeneration();

	List<T> getGenerations();

	GenerativeMetadata getMetadata();

}
