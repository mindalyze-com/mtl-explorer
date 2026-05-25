# GeoNames Search Builder

Builds the local SQLite FTS5 gazetteer used by MTL Explorer location search.

## Inputs

- `allCountries.zip` from <https://download.geonames.org/export/dump/>
- `admin1CodesASCII.txt` from the same dump directory
- optional `countryInfo.txt` for country display names

## Build

```bash
python3 docker-location-search/geonames-search/build_geonames_search.py \
  --all-countries-zip /path/to/allCountries.zip \
  --admin1-codes /path/to/admin1CodesASCII.txt \
  --country-info /path/to/countryInfo.txt \
  --output docker-location-search/geonames-search/build/geonames-search.sqlite
```

The `docker-location-search` image copies
`docker-location-search/geonames-search/build/geonames-search.sqlite` into
`/data/geonames-search.sqlite` when that file exists before image build. Runtime
startup never downloads GeoNames data.

## License

GeoNames data is licensed under CC-BY 4.0 and requires attribution.
