#!/bin/sh
set -eu
demo_script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
demo_action=${1:-up}
MAP_REPORTS_DEMO_DATA_DIR=${MAP_REPORTS_DEMO_DATA_DIR:-/Volumes/EXT_HD_1TB/mytraillog/map-reports-demo}
export MAP_REPORTS_DEMO_DATA_DIR
case "$demo_action" in
    up)
        case "$MAP_REPORTS_DEMO_DATA_DIR" in
            /Volumes/EXT_HD_1TB|/Volumes/EXT_HD_1TB/*)
                python3 -c 'import os,sys; sys.exit(0 if os.path.ismount("/Volumes/EXT_HD_1TB") else "External drive /Volumes/EXT_HD_1TB is not mounted; set MAP_REPORTS_DEMO_DATA_DIR to another explicitly chosen storage location.")'
                ;;
        esac
        docker compose -p mtl-map-report-demo -f "$demo_script_dir/compose.yml" up --build -d
        ;;
    down) docker compose -p mtl-map-report-demo -f "$demo_script_dir/compose.yml" down ;;
    *) echo "Usage: sh run-demo.sh [up|down]" >&2; exit 2 ;;
esac
