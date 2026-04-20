

First start OrbStack, then run command below... Critical part: "platform"


### Run the DB for local dev
```shell
docker run -d --platform linux/amd64 --name postgis-db -e POSTGRES_DB=mtl -e POSTGRES_USER=mtluser -e POSTGRES_PASSWORD=MtlSuper123 -p 5432:5432 postgis/postgis:18-3.6
```

### Stop DB, recreate and run
```shell
docker rm -f postgis-db || true && docker run -d --platform linux/amd64 --name postgis-db -e POSTGRES_DB=mtl -e POSTGRES_USER=mtluser -e POSTGRES_PASSWORD=MtlSuper123 -p 5432:5432 postgis/postgis:18-3.6
````


### Create a docker image
Use the docker-build-deploy.sh to build

### Run the build image locally
```shell
docker run -d --platform linux/amd64 -p 8080:8080 -d wauwau0977/mytraillog:0.6
```

---

## Map Server (docker-maps)

```shell
docker docker stop map-server
docker rm map-server
docker run -d \
  --name map-server \
  --restart unless-stopped \
  -p 18081:8081 \
  -e MAP_DOWNLOAD_URL="latest" \
  -e PORT="8081" \
  -e LOWZOOM_MAXZOOM="6" \
  -v mtl-maps-data:/data \
  wauwau0977/mytraillog-maps:1.28
```

### Delete the volume (will re-download on next run)
```shell
docker volume rm mtl-map-data
```