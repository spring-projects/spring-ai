#!/usr/bin/env bash

set -euo pipefail

IMAGE="${DELIVERANCE_TEST_IMAGE:-ecapriolo/deliverance:0.0.11}"
MODEL="${DELIVERANCE_MODEL:-Qwen3-4B-JQ4}"
OWNER="${DELIVERANCE_MODEL_OWNER:-edwardcapriolo}"
PORT="${DELIVERANCE_PORT:-9997}"
CACHE_DIR="${DELIVERANCE_CACHE_DIR:-$HOME/.deliverance}"
PLATFORM_ARG=()

if [[ -n "${DELIVERANCE_DOCKER_PLATFORM:-}" ]]; then
	PLATFORM_ARG=(--platform "${DELIVERANCE_DOCKER_PLATFORM}")
elif [[ "$(uname -m)" == "arm64" || "$(uname -m)" == "aarch64" ]]; then
	PLATFORM_ARG=(--platform linux/amd64)
fi

mkdir -p "${CACHE_DIR}"

CONFIG_FILE="$(mktemp -t deliverance-qwen.XXXXXX.properties)"
trap 'rm -f "${CONFIG_FILE}"' EXIT

cat > "${CONFIG_FILE}" <<EOF
deliverance.tensor.operations.type=jvector

deliverance-model.configs[0].model-owner=${OWNER}
deliverance-model.configs[0].model-name=${MODEL}
deliverance-model.configs[0].inference-type=GENERATION
deliverance-model.configs[0].output-head-quantization=Q4

deliverance.kv.prefix.max-tokens=8192
deliverance.kv.prefix.checkpoint-policy=START_AND_END
deliverance.kv.prefix.max-checkpoints=12
deliverance.kv.prefix.compression=MSE_TURBOQUANT
deliverance.kv.prefix.turboquant.bits=4
deliverance.kv.context-rows-per-page-target=32
EOF

echo "Starting ${IMAGE} with model ${OWNER}/${MODEL} on http://localhost:${PORT}"
echo "Using model cache: ${CACHE_DIR}"

exec docker run --rm "${PLATFORM_ARG[@]}" -p "${PORT}:8080" \
	-v "${CACHE_DIR}:/home/deliverance/.deliverance:rw" \
	-v "${CONFIG_FILE}:/deliverance/test.properties:ro" \
	-e DELIVERANCE_OPTS=" -Ddeliverance.tensor.operations.type=jvector -Dspring.config.location=file:/deliverance/test.properties " \
	"${IMAGE}"
