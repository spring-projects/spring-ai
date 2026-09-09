#!/usr/bin/env bash

set -euo pipefail

PORT="${DELIVERANCE_PORT:-9997}"
BASE_URL="${DELIVERANCE_BASE_URL:-http://localhost:${PORT}}"
MODEL="${DELIVERANCE_MODEL:-Qwen3-4B-JQ4}"

if ! (exec 3<>"/dev/tcp/localhost/${PORT}") >/dev/null 2>&1; then
	echo "Deliverance is not listening on localhost:${PORT}."
	echo "Start it first with: ./models/spring-ai-deliverance/run-deliverance-qwen.sh"
	exit 1
fi

DELIVERANCE_BASE_URL="${BASE_URL}" DELIVERANCE_MODEL="${MODEL}" \
	./mvnw -q -pl models/spring-ai-deliverance -am -Dmaven.test.skip=true -DskipTests -DskipITs install

DELIVERANCE_BASE_URL="${BASE_URL}" DELIVERANCE_MODEL="${MODEL}" \
	./mvnw -q -pl models/spring-ai-deliverance -Pintegration-tests \
	-Dit.test=DeliveranceChatModelFunctionCallingIT \
	-Dfailsafe.failIfNoSpecifiedTests=false verify
