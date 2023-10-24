FROM python:latest

RUN pip install --upgrade pip && \
    pip install optimum onnx onnxruntime

ENV SBERT_MODEL sentence-transformers/all-MiniLM-L6-v2

ENV OUTPUT_FOLDER all-MiniLM-L6-v2-onnx

COPY src/main/resources/entrypoint.sh /opt/app/entrypoint.sh

ENTRYPOINT /opt/app/entrypoint.sh