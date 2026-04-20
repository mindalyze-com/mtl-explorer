# syntax=docker/dockerfile:1.6
#
# docker-brouter — BRouter (https://brouter.de) packaged as a MyTrailLog sidecar.
#
# Two processes run in parallel under a small Python orchestrator:
#   1. BRouter HTTP router on :17777 (Java)           — answers routing requests
#   2. Admin HTTP API on     :17778 (Python stdlib)   — /status + /prewarm endpoints
#
# BRouter profiles (trekking.brf, fastbike.brf, hiking-mountain.brf, car-eco.brf)
# and the SRTM elevation data ship with the release. Rd5 routing segments are
# NOT shipped — they are downloaded on first use from https://brouter.de/brouter/segments4/
# Either lazy (on first routing request touching a 5°×5° cell) or eagerly via the
# /prewarm endpoint. Tiles persist in the named volume "brouter-segments".

FROM eclipse-temurin:21-jre-jammy

ARG BROUTER_VERSION=1.7.5
ARG BROUTER_ZIP_URL=https://github.com/abrensch/brouter/releases/download/v${BROUTER_VERSION}/brouter-${BROUTER_VERSION}.zip

ENV BROUTER_HOME=/opt/brouter \
    BROUTER_SEGMENTS_DIR=/segments4 \
    BROUTER_PORT=17777 \
    BROUTER_ADMIN_PORT=17778 \
    PYTHONUNBUFFERED=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip ca-certificates python3 \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p "${BROUTER_HOME}" "${BROUTER_SEGMENTS_DIR}" \
    && curl -fsSL "${BROUTER_ZIP_URL}" -o /tmp/brouter.zip \
    && unzip -q /tmp/brouter.zip -d /tmp/brouter-extract \
    && cp -R /tmp/brouter-extract/brouter-*/* "${BROUTER_HOME}/" \
    && rm -rf /tmp/brouter.zip /tmp/brouter-extract

COPY scripts/ /opt/brouter-orchestrator/
RUN chmod +x /opt/brouter-orchestrator/*.py /opt/brouter-orchestrator/*.sh

EXPOSE 17777 17778
VOLUME ["/segments4"]

ENTRYPOINT ["/opt/brouter-orchestrator/entrypoint.sh"]
