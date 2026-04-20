### Docker command

#### Windows
- In the sample below the postgres DB is created in a Windows Download folder 'C:\Users\patri\Downloads\DOCKER_POSTGIS'. 
- The map server runs on port 8081 on the host
- Postgres (Postgis) server is exposed at port 15432 (optional)

`docker run --volume=C:\Users\patri\Downloads\DOCKER_POSTGIS:/var/lib/postgresql/data:rw --volume=C:\Users\patri\Downloads\GPX:/app/gpx -p 15432:5432 -p 8081:8080 wauwau0977/mytraillog:latest`

- TODO: add media
- TODO: add external config example -v /local-host/application.yml:/app/config/application.yml

### MacOS
- docker run --volume=/Users/pheusser/Documents/GPX_WATCHER_3A:/app/gpx -p 15432:5432 -p 18080:8080 -d wauwau0977/mytraillog:beta


#### Linux / NAS




