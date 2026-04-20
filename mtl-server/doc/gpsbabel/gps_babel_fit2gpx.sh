#!/bin/bash

# script in: /mnt/c/Users/patri/IdeaProjects/mytraillog/mtl-server/doc/gpsbabel
# files in: /mnt/c/Users/patri/Downloads/Garmin_export_20230415/DI_CONNECT/DI-Connect-Fitness-Uploaded-Files/UploadedFiles_0-_Part1

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <directory>"
    exit 1
fi

directory="$1"

if [ ! -d "$directory" ]; then
    echo "The given path is not a directory. Please provide a valid directory."
    exit 1
fi

for input_file in "$directory"/*.fit; do
    if [ ! -e "$input_file" ]; then
        echo "No FIT files found in the given directory."
        exit 1
    fi
    output_gpx_file="${input_file%.fit}.gpx"
    echo -e "Convert $input_file into $output_gpx_file"
    # gpx file will be created in the same folder as the tcx one
    gpsbabel -i garmin_fit -f "$input_file" -o gpx -F "$output_gpx_file"

    if [ $? -eq 0 ]; then
        echo "Conversion successful: $input_file -> $output_gpx_file"
    else
        echo "An error occurred during conversion. Please check your input files and try again."
    fi
done
