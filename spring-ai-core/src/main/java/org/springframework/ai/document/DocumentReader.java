package org.springframework.ai.document;

import java.util.List;

import org.springframework.core.io.Resource;

public interface DocumentReader {

	List<Document> read(String resourceUrl);

	List<Document> read(Resource resource);

}
