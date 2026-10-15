#!/bin/bash
set -euo pipefail

readonly MICROMAMBA_VERSION=2.9.0-0
readonly MEDIA_PREFIX=/opt/mtl/media
readonly PACKAGE_ROOT=/tmp/mtl-mamba
script_directory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

case "${TARGETARCH:?Docker target architecture is required}" in
  amd64)
    platform=linux-64
    micromamba_sha256=366cd9cd8be14df1ab8ed50352a82111082a36686b2d389fdb79a92c3fafb3e3
    ;;
  arm64)
    platform=linux-aarch64
    micromamba_sha256=9f93b974adcb4d166996af969b6cd371287d1a3e52733704727884d9b74cb7a7
    ;;
  *) printf 'Unsupported media package architecture: %s\n' "${TARGETARCH}" >&2; exit 1 ;;
esac

lock_file="${script_directory}/conda-${platform}.lock"
specification_hash="$(sha256sum "${script_directory}/media-specs.txt" | cut -d ' ' -f 1)"
grep -Fx "# media-specs-sha256: ${specification_hash}" "${lock_file}"

# Download the standalone binary; no source archive extraction or compilation.
curl --fail --location --retry 3 \
  "https://github.com/mamba-org/micromamba-releases/releases/download/${MICROMAMBA_VERSION}/micromamba-${platform}" \
  -o /tmp/micromamba
printf '%s  %s\n' "${micromamba_sha256}" /tmp/micromamba | sha256sum --check -
chmod +x /tmp/micromamba
/tmp/micromamba create --yes --no-rc --root-prefix "${PACKAGE_ROOT}" \
  --prefix "${MEDIA_PREFIX}" --platform "${platform}" --file "${lock_file}"

# Keep package notices and source metadata when discarding the package cache.
notice_directory="${MEDIA_PREFIX}/share/mtl/package-notices"
mkdir -p "${notice_directory}"
while IFS= read -r -d '' info_directory; do
  [ -f "${info_directory}/index.json" ] || continue
  package_directory="$(dirname -- "${info_directory}")"
  destination="${notice_directory}/$(basename -- "${package_directory}")"
  mkdir -p "${destination}"
  for notice in licenses about.json; do
    if [ -e "${info_directory}/${notice}" ]; then
      cp -a "${info_directory}/${notice}" "${destination}/"
    fi
  done
done < <(find "${PACKAGE_ROOT}/pkgs" -type d -name info -print0)
for tool in libvips imagemagick ffmpeg libheif; do
  if ! compgen -G "${notice_directory}/${tool}-*/licenses/*" > /dev/null; then
    printf 'Missing package license notices for %s\n' "${tool}" >&2
    exit 1
  fi
done
cp "${lock_file}" "${MEDIA_PREFIX}/share/mtl/conda-explicit.lock"
rm -rf "${PACKAGE_ROOT}" /tmp/micromamba
