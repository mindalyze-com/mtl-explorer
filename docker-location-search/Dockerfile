FROM python:3.14.5-slim-trixie

ARG REQUIRE_GEONAMES_DB=false
ARG MTL_IMAGE_VERSION=""
ARG MTL_IMAGE_BUILD_TIME=""

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV MTL_LOCATION_SEARCH_DB=/data/geonames-search.sqlite
ENV PORT=8083

WORKDIR /app

COPY docker-location-search/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

COPY docker-location-search/location_search_server.py /app/location_search_server.py

RUN mkdir -p /data
COPY docker-location-search/geonames-search/build/ /data/
RUN if [ -f /data/geonames-search.sqlite ]; then \
      true; \
    elif [ "$REQUIRE_GEONAMES_DB" = "true" ]; then \
      echo "docker-location-search/geonames-search/build/geonames-search.sqlite is required for this image build." >&2; \
      exit 1; \
    fi

RUN set -eux; \
    mkdir -p /opt/mtl; \
    build_time="${MTL_IMAGE_BUILD_TIME:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"; \
    printf '%s\n' "${MTL_IMAGE_VERSION}" > /opt/mtl/image-version; \
    printf '%s\n' "${build_time}" > /opt/mtl/image-build-time

ENV MTL_IMAGE_VERSION=${MTL_IMAGE_VERSION}
ENV MTL_IMAGE_BUILD_TIME=${MTL_IMAGE_BUILD_TIME}

EXPOSE 8083

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8083/health', timeout=3).read()"

CMD ["python", "/app/location_search_server.py"]
