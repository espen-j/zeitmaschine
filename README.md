## Minio dev setup

### Docker

### Configuration

See: https://docs.minio.io/docs/minio-docker-quickstart-guide.html

> The mc admin config API will evolve soon to be able to configure specific fields using get/set commands.
 
Until then we need to extract the config from the running instance, update the file and reload the service. 

#### Prerequisite
Set the needed environment variables:
```
export ZM_DATA=/Users/espen/development/minio/zm-dev/data
export ZM_CONFIG_DIR=/Users/espen/development/minio/zm-dev/config
export ZM_ACCESS_KEY=test
export ZM_ACCESS_SECRET=testtest
export ZM_DOCKER_NAME=zm-dev
export ZM_DOCKER_HOST=http://127.0.0.1:9000
export ZM_WEBHOOK_ENDPOINT=http://host.docker.internal:8080/s3/webhook
export ZM_NAME=zm-dev
```

#### Run docker with persistent config mapped
```
docker run -d -p 9000:9000 --name $ZM_DOCKER_NAME \
    -e "MINIO_ACCESS_KEY=$ZM_ACCESS_KEY" \
    -e "MINIO_SECRET_KEY=$ZM_ACCESS_SECRET" \
    -v $ZM_DATA:/data \
    -v $ZM_CONFIG_DIR:/root/.minio \
    minio/minio server /data

# configure the client (see ~/.mc/config.json)
mc config host add $ZM_NAME $ZM_DOCKER_HOST $ZM_ACCESS_KEY $ZM_ACCESS_SECRET

# https://stackoverflow.com/questions/40027395/passing-bash-variable-to-jq-select
mc admin config get $ZM_DOCKER_NAME | jq '.notify.webhook."1".enable = true' | jq --arg ZM_WEBHOOK_ENDPOINT "$ZM_WEBHOOK_ENDPOINT" '.notify.webhook."1".endpoint=$ZM_WEBHOOK_ENDPOINT' > $ZM_CONFIG_DIR/config.json
mc admin config set $ZM_NAME < $ZM_CONFIG_DIR/config.json

docker restart $ZM_DOCKER_NAME

``` 

```
mc mb $ZM_NAME/media # create bucket media
mc event add $ZM_NAME/media arn:minio:sqs::1:webhook --event put,delete --suffix .jpg # enable webhook for bucket, see options for suffix and prefix an methods!
mc admin config set $ZM_NAME < $ZM_CONFIG_DIR/config.json # reload config (needs restart)
docker restart $ZM_DOCKER_NAME
docker logs $ZM_DOCKER_NAME
mc cp /Users/espen/temp/pictures/Camera/IMG_20170801_221920.jpg zm-dev/media

```

## Elasticsearch Setup

Passing env variables working:
``` 
$ docker run -d -p 9200:9200 -p 9300:9300 -e "discovery.type=single-node" -e "http.cors.enabled=true" -e "http.cors.allow-origin=*" docker.elastic.co/elasticsearch/elasticsearch:6.5.3
```

Reading config not working:
```
$ docker run -d -p 9200:9200 -p 9300:9300 -v /Users/espen/development/git/zeitmaschine/contrib/elasticsearch/elasticsearch.yml:/usr/share/elasticsearch/config/elasticsearch.yml -e "discovery.type=single-node" docker.elastic.co/elasticsearch/elasticsearch:6.5.3
```

### Create index with geopoint property

Other fields are auto mapped, but:
``` 
$ curl -X DELETE http://localhost:9200/zeitmaschine
$ curl -X PUT -H 'Content-Type: application/json' -d '{}' http://localhost:9200/zeitmaschine
$ curl -X PUT -H 'Content-Type: application/json' -d '{ "properties": {"location": {"type": "geo_point"}}}' http://localhost:9200/zeitmaschine/_mapping/image
$ curl http://localhost:9200/zeitmaschine/_mapping/image | jq .
```
