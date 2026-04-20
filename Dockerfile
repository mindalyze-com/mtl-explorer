# Dockerfile.app
#
# Build-time defaults — override with: docker build --build-arg GCEXPORT_DEFAULT_VERSION=v4.7.0 .
# These ARGs are promoted to ENVs so the running container (and Spring Boot) can read them.
ARG GCEXPORT_DEFAULT_VERSION=v4.6.2
ARG FIT_EXPORT_DEFAULT_PROFILE=default
ARG FIT_EXPORT_DEFAULT_PACKAGES="garth fitparse gpxpy"

FROM debian:bookworm

# Promote build ARGs to runtime ENVs (Spring Boot reads these via ${GCEXPORT_DEFAULT_VERSION:v4.6.2})
ARG GCEXPORT_DEFAULT_VERSION
ARG FIT_EXPORT_DEFAULT_PROFILE
ARG FIT_EXPORT_DEFAULT_PACKAGES
ENV GCEXPORT_DEFAULT_VERSION=${GCEXPORT_DEFAULT_VERSION}
ENV FIT_EXPORT_DEFAULT_PROFILE=${FIT_EXPORT_DEFAULT_PROFILE}
ENV FIT_EXPORT_DEFAULT_PACKAGES=${FIT_EXPORT_DEFAULT_PACKAGES}

# Set locale to UTF-8 so the JVM uses UTF-8 for file-system path encoding (sun.jnu.encoding)
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

# Install required packages and msopenjdk-21
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
      ca-certificates \
      curl \
      procps \
      vim \
      wget \
      less \
      unzip \
      lsb-release && \
    apt-get install --reinstall -y ca-certificates && \
    wget https://packages.microsoft.com/config/debian/$(lsb_release -rs)/packages-microsoft-prod.deb -O packages-microsoft-prod.deb && \
    dpkg -i packages-microsoft-prod.deb && \
    apt-get update && \
    apt-get install -y --no-install-recommends msopenjdk-21 && \
    rm -rf /var/lib/apt/lists/*


# Install python3-pip and python3-venv (needed for garmin export setup)
# Install imagemagick + libheif-dev for HEIC-to-JPEG conversion in MediaController
# Install python3-pillow + fonts for demo-photo generation
RUN apt-get update && apt-get install -y --no-install-recommends python3-pip python3-venv imagemagick libheif-dev python3-pillow fonts-dejavu-core gpsbabel \
    && pip3 install --break-system-packages piexif \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* /root/.cache/pip

# Copy garmin_export folder and pre-install the default gcexport version
# install_gcexport.sh is idempotent: skips if venv_gcexport_<version>/ already exists
COPY docker/garmin_export/ /app/garmin_export/
RUN chmod +x /app/garmin_export/install_gcexport.sh /app/garmin_export/run_export.sh \
    && /bin/bash /app/garmin_export/install_gcexport.sh "${GCEXPORT_DEFAULT_VERSION}"

# Temporary patch for garmin-connect-export (remove when project delivers official fix)
# filterdiff applies only the .py hunks; requirements.txt is written directly (version mismatch in upstream)
RUN apt-get update && apt-get install -y --no-install-recommends patch patchutils \
    && cd /app/garmin_export/gcexport_src_${GCEXPORT_DEFAULT_VERSION} \
    && filterdiff -i '*.py' /app/garmin_export/patch_2026_04_13.txt | patch -p1 \
    && printf 'garminconnect>=0.3.2,<0.4.0\n' > requirements.txt \
    && /app/garmin_export/venv_gcexport_${GCEXPORT_DEFAULT_VERSION}/bin/pip install -r requirements.txt \
    && apt-get clean && rm -rf /var/lib/apt/lists/*


# Copy garmin_fit_export folder and pre-install the default fit-export profile
# install_fit_export.sh is idempotent: skips if venv_fit_<profile>/ already exists
COPY docker/garmin_fit_export/ /app/garmin_fit_export/
RUN chmod +x /app/garmin_fit_export/install_fit_export.sh /app/garmin_fit_export/run_fit_export.sh \
    && /bin/bash /app/garmin_fit_export/install_fit_export.sh "${FIT_EXPORT_DEFAULT_PROFILE}" "${FIT_EXPORT_DEFAULT_PACKAGES}"

# Copy demo-mode assets (GPX zip + photo generator script) — always packaged, only used at runtime when DEMO_MODE is set
COPY docker/gpx_porto_taxi_dataset/porto_taxi_service_gpx_extract.zip /app/demo/porto_taxi_service_gpx_extract.zip
COPY docker/gpx_porto_taxi_dataset/generate_demo_photos.py /app/demo/generate_demo_photos.py
COPY docker/gpx_porto_taxi_dataset/DATASOURCE.md /app/demo/DATASOURCE.md

# Copy the Spring Boot application JAR
COPY mtl-server/target/mtl-server-0.0.1-SNAPSHOT.jar /app/mtl-server-0.0.1-SNAPSHOT.jar

WORKDIR /app

# Create directories that will be used as volumes
RUN mkdir -p /app/gpx /app/media /app/config

# Expose the port for the Spring Boot application
EXPOSE 8080

# Copy your custom entrypoint script (see next section)
COPY docker/my-entrypoint.sh /my-entrypoint.sh
RUN chmod +x /my-entrypoint.sh

# Set the entrypoint; CMD is used to start the Java application
ENTRYPOINT ["/my-entrypoint.sh"]

# settings to motivate java to release memory if it can..
# MaxRAMPercentage: percentage for HEAP... leave room for others, still allow to control from container
CMD ["java", "-XX:MaxRAMPercentage=60.0", "-XX:InitialRAMPercentage=25.0", "-XX:+UseZGC", "-XX:ZUncommitDelay=10", "-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8", "-jar", "mtl-server-0.0.1-SNAPSHOT.jar"]
