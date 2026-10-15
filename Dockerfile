# syntax=docker/dockerfile:1.24.0
#
# Dockerfile.app
#
# Build-time defaults — override with: docker build --build-arg GCEXPORT_DEFAULT_VERSION=v4.6.2 .
# These ARGs are promoted to ENVs so the running container (and Spring Boot) can read them.
ARG GCEXPORT_DEFAULT_VERSION=v4.6.2
ARG GCEXPORT_GARMINCONNECT_REQUIREMENT=garminconnect==0.3.11
ARG FIT_EXPORT_DEFAULT_PROFILE=default
ARG FIT_EXPORT_DEFAULT_PACKAGES="garth fitparse gpxpy"
ARG GPSBABEL_VERSION=1.10.0
ARG GPSBABEL_GIT_REF=gpsbabel_1_10_0
ARG MTL_IMAGE_VERSION=""
ARG MTL_IMAGE_REVISION=""
ARG MTL_IMAGE_BUILD_TIME=""

FROM node:24.20.0-trixie-slim AS node-toolchain

FROM ubuntu:26.04 AS media-tools-builder

ARG TARGETARCH
# Both architectures install the complete, checksum-pinned conda-forge environment.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*
COPY docker/media/install-media-tools.sh docker/media/media-specs.txt docker/media/conda-*.lock /tmp/mtl-media/
RUN /bin/bash /tmp/mtl-media/install-media-tools.sh

FROM ubuntu:26.04 AS gpsbabel-builder

ARG GPSBABEL_VERSION
ARG GPSBABEL_GIT_REF

RUN <<'EOF'
set -eux
apt-get update
apt-get install -y --no-install-recommends \
  ca-certificates \
  git \
  build-essential \
  cmake \
  ninja-build \
  qt6-base-dev \
  qt6-serialport-dev \
  qt6-5compat-dev \
  libgl-dev \
  pkg-config \
  zlib1g-dev \
  libusb-1.0-0-dev \
  libshp-dev
work_dir="$(mktemp -d)"
git clone --depth 1 --branch "${GPSBABEL_GIT_REF}" https://github.com/GPSBabel/gpsbabel.git "${work_dir}/gpsbabel"
cd "${work_dir}/gpsbabel"
cmake -S . -B build -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DGPSBABEL_MAPPREVIEW=OFF \
  -DGPSBABEL_WITH_LIBUSB=pkgconfig \
  -DGPSBABEL_WITH_SHAPELIB=pkgconfig \
  -DGPSBABEL_WITH_ZLIB=pkgconfig
cmake --build build --target gpsbabel --parallel "$(nproc)"
mkdir -p /opt/gpsbabel/bin
./build/gpsbabel -V | tee /opt/gpsbabel/version-output
grep -Fx "GPSBabel Version ${GPSBABEL_VERSION}" /opt/gpsbabel/version-output
cp ./build/gpsbabel /opt/gpsbabel/bin/gpsbabel
printf 'source:%s\n' "${GPSBABEL_GIT_REF}" > /opt/gpsbabel/provider
chmod +x /opt/gpsbabel/bin/gpsbabel
/opt/gpsbabel/bin/gpsbabel -V
rm -rf "${work_dir}" /var/lib/apt/lists/* /root/.cache
EOF

FROM maven:3.9.16-eclipse-temurin-25 AS app-builder

ARG MTL_IMAGE_VERSION
ARG MTL_IMAGE_REVISION
ARG MTL_IMAGE_BUILD_TIME
ENV VITE_APP_VERSION=${MTL_IMAGE_VERSION}
ENV VITE_APP_REVISION=${MTL_IMAGE_REVISION}
ENV VITE_APP_BUILD=${MTL_IMAGE_BUILD_TIME}

COPY --from=node-toolchain /usr/local/ /usr/local/

WORKDIR /workspace
COPY . .

RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=cache,target=/root/.npm <<'EOF'
set -eu
echo "==> Starting Maven reactor build"
echo "    This includes OpenAPI TypeScript client generation and the mtl-client npm build."
mvn --batch-mode clean install -DskipTests=true
echo "==> Finished Maven reactor build"
EOF

FROM eclipse-temurin:25-jre-resolute AS media-runtime

# Promote build ARGs to runtime ENVs (Spring Boot reads these via ${GCEXPORT_DEFAULT_VERSION:v4.6.2})
ARG GCEXPORT_DEFAULT_VERSION
ARG GCEXPORT_GARMINCONNECT_REQUIREMENT
ARG FIT_EXPORT_DEFAULT_PROFILE
ARG FIT_EXPORT_DEFAULT_PACKAGES
ARG GPSBABEL_VERSION
ENV GCEXPORT_DEFAULT_VERSION=${GCEXPORT_DEFAULT_VERSION}
ENV FIT_EXPORT_DEFAULT_PROFILE=${FIT_EXPORT_DEFAULT_PROFILE}
ENV FIT_EXPORT_DEFAULT_PACKAGES=${FIT_EXPORT_DEFAULT_PACKAGES}

# Set locale to UTF-8 so the JVM uses UTF-8 for file-system path encoding (sun.jnu.encoding)
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8
ENV PATH="/usr/local/bin:${PATH}"
ENV VIRTUAL_ENV=/opt/mtl/python
ENV PATH="${VIRTUAL_ENV}/bin:/opt/mtl/media/bin:${PATH}"
ENV MALLOC_ARENA_MAX=2
ENV MAGICK_THREAD_LIMIT=1
ENV MAGICK_MEMORY_LIMIT=64MiB
ENV MAGICK_MAP_LIMIT=128MiB

# Install required runtime packages.
# eclipse-temurin:25-jre-resolute includes the jdk.jfr module. jattach keeps
# runtime JFR attach/start/dump/stop available without shipping a full JDK.
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
      ca-certificates \
      curl \
      jattach \
      procps \
      vim \
      wget \
      less \
      unzip && \
    rm -rf /var/lib/apt/lists/*


# Install python3-pip and python3-venv (needed for garmin export setup)
# Media codecs come from conda-forge; retain system fonts for demo photos.
RUN apt-get update && apt-get install -y --no-install-recommends \
      python3-pip \
      python3-venv \
      fonts-dejavu-core \
      libqt6core6t64 \
      libqt6core5compat6 \
      libshp4 \
      libusb-1.0-0 \
    && python3 -m venv "${VIRTUAL_ENV}" \
    && pip3 install piexif==1.1.3 Pillow==12.3.0 \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* /root/.cache/pip

# Keep the install prefix unchanged so package RPATHs and plugins resolve locally.
COPY --from=media-tools-builder /opt/mtl/media /opt/mtl/media
COPY --chmod=755 docker/media/vips-preview.py /opt/mtl/media/bin/mtl-vips-preview
# Codec behavior is covered by the runtime tests below.
RUN magick -version \
    && vips --version \
    && ffmpeg -version \
    && ffprobe -version

# Require native libvips for the iOS 18 HEIC regression on each architecture.
# ImageMagick is exercised only by the small synthetic BMP fallback test.
RUN --mount=type=bind,source=docker/media,target=/tmp/mtl-media-tests \
    python3 -m unittest discover -s /tmp/mtl-media-tests -p 'test_*.py' -v

FROM media-runtime AS runtime

COPY --from=gpsbabel-builder /opt/gpsbabel/bin/gpsbabel /usr/local/bin/gpsbabel
COPY --from=gpsbabel-builder /opt/gpsbabel/provider /opt/mtl/gpsbabel-provider
COPY --from=gpsbabel-builder /opt/gpsbabel/version-output /opt/mtl/gpsbabel-version
RUN set -eux; \
    test "$(command -v gpsbabel)" = "/usr/local/bin/gpsbabel"; \
    gpsbabel -V | tee /opt/mtl/gpsbabel-runtime-version; \
    grep -Fx "GPSBabel Version ${GPSBABEL_VERSION}" /opt/mtl/gpsbabel-runtime-version

# Copy garmin_export folder and pre-install the default gcexport version
# install_gcexport.sh is idempotent: skips if venv_gcexport_<version>/ already exists
COPY docker/garmin_export/ /app/garmin_export/
RUN chmod +x /app/garmin_export/install_gcexport.sh /app/garmin_export/run_export.sh \
    && /bin/bash /app/garmin_export/install_gcexport.sh "${GCEXPORT_DEFAULT_VERSION}" \
    && rm -rf /root/.cache/pip

# Temporary patch for garmin-connect-export (remove when project delivers official fix)
# filterdiff applies only the .py hunks; requirements.txt is written directly (version mismatch in upstream)
# Keep the patched exporter on the current garminconnect release.
RUN apt-get update && apt-get install -y --no-install-recommends patch patchutils \
    && cd /app/garmin_export/gcexport_src_${GCEXPORT_DEFAULT_VERSION} \
    && filterdiff -i '*.py' /app/garmin_export/patch_2026_04_13.txt | patch -p1 \
    && printf '%s\n' "${GCEXPORT_GARMINCONNECT_REQUIREMENT}" > requirements.txt \
    && /app/garmin_export/venv_gcexport_${GCEXPORT_DEFAULT_VERSION}/bin/pip uninstall -y garth \
    && /app/garmin_export/venv_gcexport_${GCEXPORT_DEFAULT_VERSION}/bin/pip install --upgrade --upgrade-strategy eager -r requirements.txt \
    && GCEXPORT_SOURCE_DIR="$PWD" /app/garmin_export/venv_gcexport_${GCEXPORT_DEFAULT_VERSION}/bin/python3 -B /app/garmin_export/test_gcexport_requests.py \
    && apt-get clean && rm -rf /var/lib/apt/lists/* /root/.cache/pip


# Copy garmin_fit_export folder and pre-install the default fit-export profile
# install_fit_export.sh is idempotent: skips if venv_fit_<profile>/ already exists
COPY docker/garmin_fit_export/ /app/garmin_fit_export/
RUN chmod +x /app/garmin_fit_export/install_fit_export.sh /app/garmin_fit_export/run_fit_export.sh \
    && /bin/bash /app/garmin_fit_export/install_fit_export.sh "${FIT_EXPORT_DEFAULT_PROFILE}" "${FIT_EXPORT_DEFAULT_PACKAGES}" \
    && rm -rf /root/.cache/pip

# Copy demo-mode assets and the shared demo/regression photo generators
COPY docker/gpx_porto_taxi_dataset/porto_taxi_service_gpx_extract.zip /app/demo/porto_taxi_service_gpx_extract.zip
COPY docker/gpx_porto_taxi_dataset/generate_demo_photos.py /app/demo/generate_demo_photos.py
COPY docker/gpx_porto_taxi_dataset/generate_regression_photos.py /app/demo/generate_regression_photos.py
COPY docker/gpx_porto_taxi_dataset/photo_placeholder.py /app/demo/photo_placeholder.py
COPY docker/gpx_porto_taxi_dataset/demo_track_catalog.py /app/demo/demo_track_catalog.py
COPY docker/gpx_porto_taxi_dataset/demo_logging.py /app/demo/demo_logging.py
COPY docker/gpx_porto_taxi_dataset/elevation/__init__.py \
    docker/gpx_porto_taxi_dataset/elevation/demo_elevation.py \
    docker/gpx_porto_taxi_dataset/elevation/numeric_elevation.py \
    docker/gpx_porto_taxi_dataset/elevation/create_demo_tracks.py /app/demo/elevation/
# Terrain preparation is on demand; image builds use the checked-in raster.
COPY docker/gpx_porto_taxi_dataset/elevation/data/porto-elevation-120m.mtldem \
    docker/gpx_porto_taxi_dataset/elevation/data/porto-elevation-120m.json \
    docker/gpx_porto_taxi_dataset/elevation/data/elevation-attribution.json /app/demo/elevation/data/
RUN python3 /app/demo/demo_track_catalog.py /app/demo/porto_taxi_service_gpx_extract.zip /app/demo/demo-track-catalog.json.gz
COPY docker/gpx_porto_taxi_dataset/DATASOURCE.md /app/demo/DATASOURCE.md

# Copy the Spring Boot application JAR
COPY --from=app-builder /workspace/mtl-server/target/mtl-server-1.0.0-SNAPSHOT.jar /app/mtl-server-1.0.0-SNAPSHOT.jar
COPY --from=app-builder /workspace/mtl-server/target/gpsquality/ /app/gpsquality/

WORKDIR /app

# Create directories that will be used as volumes
RUN mkdir -p /app/gpx /app/media /app/config \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists /var/cache/apt/archives /var/log/apt \
    && mkdir -p /var/lib/apt/lists/partial

ARG MTL_IMAGE_VERSION
ARG MTL_IMAGE_REVISION
ARG MTL_IMAGE_BUILD_TIME
RUN set -eux; \
    mkdir -p /opt/mtl; \
    build_time="${MTL_IMAGE_BUILD_TIME:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"; \
    printf '%s\n' "${MTL_IMAGE_VERSION}" > /opt/mtl/image-version; \
    printf '%s\n' "${MTL_IMAGE_REVISION}" > /opt/mtl/image-revision; \
    printf '%s\n' "${build_time}" > /opt/mtl/image-build-time
ENV MTL_IMAGE_VERSION=${MTL_IMAGE_VERSION}
ENV MTL_IMAGE_REVISION=${MTL_IMAGE_REVISION}
ENV MTL_IMAGE_BUILD_TIME=${MTL_IMAGE_BUILD_TIME}

# Expose the port for the Spring Boot application
EXPOSE 8080

# Copy your custom entrypoint script (see next section)
COPY docker/my-entrypoint.sh /my-entrypoint.sh
RUN chmod +x /my-entrypoint.sh

# Set the entrypoint; CMD is used to start the Java application
ENTRYPOINT ["/my-entrypoint.sh"]

# Demo preparation finishes before Java starts. Retain headroom for JVM native
# memory and one media helper, including 4K-to-1080p FFmpeg conversion.
# The 1248 MiB Compose limit gives G1 a 500 MiB maximum heap.
CMD ["java", "-XX:InitialRAMPercentage=6.25", "-XX:MaxRAMPercentage=40.0", "-XX:+UseG1GC", "-XX:ActiveProcessorCount=2", "-Xss512k", "-XX:+ExitOnOutOfMemoryError", "-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8", "--module-path", "/app/gpsquality", "--add-modules", "com.x8ing.mtl.gpsquality.diagnostics", "-jar", "mtl-server-1.0.0-SNAPSHOT.jar"]
