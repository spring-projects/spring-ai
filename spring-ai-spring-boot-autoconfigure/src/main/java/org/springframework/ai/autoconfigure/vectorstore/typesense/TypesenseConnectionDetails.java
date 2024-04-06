package org.springframework.ai.autoconfigure.vectorstore.typesense;

import org.springframework.boot.autoconfigure.service.connection.ConnectionDetails;

public interface TypesenseConnectionDetails extends ConnectionDetails {

	String getHost();

	String getProtocol();

	String getPort();

}
