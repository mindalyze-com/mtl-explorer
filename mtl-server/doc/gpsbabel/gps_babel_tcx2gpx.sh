#!/bin/bash

# script in: /mnt/c/Users/patri/IdeaProjects/mytraillog/mtl-server/doc/gpsbabel
# files in: /mnt/c/Users/patri/Downloads/GPS_Dropbox_Export/tcx/

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <directory>"
    exit 1
fi

directory="$1"

if [ ! -d "$directory" ]; then
    echo "The given path is not a directory. Please provide a valid directory."
    exit 1
fi

for input_tcx_file in "$directory"/*.tcx; do
    if [ ! -e "$input_tcx_file" ]; then
        echo "No TCX files found in the given directory."
        exit 1
    fi
    output_gpx_file="${input_tcx_file%.tcx}.gpx"
    echo -e "Convert $input_tcx_file into $output_gpx_file"
    # gpx file will be created in the same folder as the tcx one
    gpsbabel -i gtrnctr -f "$input_tcx_file" -o gpx -F "$output_gpx_file"

    if [ $? -eq 0 ]; then
        echo "Conversion successful: $input_tcx_file -> $output_gpx_file"
    else
        echo "An error occurred during conversion. Please check your input files and try again."
    fi
done
