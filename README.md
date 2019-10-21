# Dev setup

See ```contrib/docker``` for docker compose file with preconfigured elastic search and minio.

## Run docker compose
```
cd contrib/docker
docker-compose up
```

## Minio Configuration

Minio needs some more configuration. See: 

https://docs.minio.io/docs/minio-docker-quickstart-guide.html

> The mc admin config API will evolve soon to be able to configure specific fields using get/set commands.
 
Until then we need to extract the config from the running instance, update the file and reload the service.  

```
# Set the needed environment variables (only used for updating the configuration):

export ZM_CONFIG_DIR=/Users/espen/development/minio/zm-dev/config
export ZM_ACCESS_KEY=test
export ZM_ACCESS_SECRET=testtest
export ZM_DOCKER_NAME=zm-minio
export ZM_DOCKER_HOST=http://127.0.0.1:9000
export ZM_WEBHOOK_ENDPOINT=http://host.docker.internal:8080/s3/webhook
export ZM_NAME=zm-dev

# configure the client (see ~/.mc/config.json)
mc config host add $ZM_NAME $ZM_DOCKER_HOST $ZM_ACCESS_KEY $ZM_ACCESS_SECRET

# https://stackoverflow.com/questions/40027395/passing-bash-variable-to-jq-select
mc admin config get $ZM_NAME | jq '.notify.webhook."1".enable = true' | jq --arg ZM_WEBHOOK_ENDPOINT "$ZM_WEBHOOK_ENDPOINT" '.notify.webhook."1".endpoint=$ZM_WEBHOOK_ENDPOINT' > $ZM_CONFIG_DIR/config.json
mc admin config set $ZM_NAME < $ZM_CONFIG_DIR/config.json

docker restart $ZM_DOCKER_NAME

``` 

Other interesting commands (not needed):
```
# create bucket media
mc mb $ZM_NAME/media

# This is done on applicaiton start
mc event add $ZM_NAME/media arn:minio:sqs::1:webhook --event put,delete --suffix .jpg # enable webhook for bucket, see options for suffix and prefix an methods!

mc admin config set $ZM_NAME < $ZM_CONFIG_DIR/config.json # reload config (needs restart)
docker restart $ZM_DOCKER_NAME
docker logs $ZM_DOCKER_NAME

# Copy one file to 
mc cp /Users/espen/temp/pictures/Camera/IMG_20170801_221920.jpg zm-dev/media

```
