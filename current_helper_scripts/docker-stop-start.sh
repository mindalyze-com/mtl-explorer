#!/bin/bash
set -euo pipefail

SHARED_DIR="/opt/mtl/shared"
SHARED_PROJ="mtl-shared"

cd "$SHARED_DIR"
docker compose -p "$SHARED_PROJ" pull
docker compose -p "$SHARED_PROJ" up -d

cd /opt/mtl/mtl-demo.mindalyze.com
docker compose -p mtl-demo-mindalyze-com down
docker compose -p mtl-demo-mindalyze-com pull
docker compose -p mtl-demo-mindalyze-com up -d

cd /opt/mtl/mtl-demo-large.mindalyze.com
docker compose -p mtl-demo-large-mindalyze-com down
docker compose -p mtl-demo-large-mindalyze-com pull
docker compose -p mtl-demo-large-mindalyze-com up -d

cd /opt/mtl/mtl-demo-beta.mindalyze.com
docker compose -p mtl-demo-beta-mindalyze-com down
docker compose -p mtl-demo-beta-mindalyze-com pull
docker compose -p mtl-demo-beta-mindalyze-com up -d
