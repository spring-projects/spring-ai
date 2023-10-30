package org.springframework.experimental.ai.model;

import java.util.Objects;

abstract class AbstractAWSBaseModelParams implements AWSBaseModel {

	private double temperature = 0;

	private double topK = 5;

	private double topP = 1;

	private double maxToken = 512;

	private double lengthPenalty = 0;

	private final String modelId;

	public AbstractAWSBaseModelParams(String modelId) {
		this.modelId = modelId;
	}

	public double getTemperature() {
		return temperature;
	}

	public void setTemperature(double temperature) {
		this.temperature = temperature;
	}

	public double getTopK() {
		return topK;
	}

	public void setTopK(double topK) {
		this.topK = topK;
	}

	public double getTopP() {
		return topP;
	}

	public void setTopP(double topP) {
		this.topP = topP;
	}

	public double getMaxToken() {
		return maxToken;
	}

	public String getModelId() {
		return this.modelId;
	}

	public void setMaxToken(double maxToken) {
		this.maxToken = maxToken;
	}

	public double getLengthPenalty() {
		return lengthPenalty;
	}

	public void setLengthPenalty(double lengthPenalty) {
		this.lengthPenalty = lengthPenalty;
	}

	@Override
	public String toString() {
		return "AbstractAWSBaseModelParams{" + "temperature=" + temperature + ", topK=" + topK + ", topP=" + topP
				+ ", maxToken=" + maxToken + ", lengthPentalty=" + lengthPenalty + ", modelId='" + modelId + '\'' + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		AbstractAWSBaseModelParams that = (AbstractAWSBaseModelParams) o;
		return Double.compare(temperature, that.temperature) == 0 && Double.compare(topK, that.topK) == 0
				&& Double.compare(topP, that.topP) == 0 && Double.compare(maxToken, that.maxToken) == 0
				&& Double.compare(lengthPenalty, that.lengthPenalty) == 0 && Objects.equals(modelId, that.modelId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(temperature, topK, topP, maxToken, lengthPenalty, modelId);
	}

}
