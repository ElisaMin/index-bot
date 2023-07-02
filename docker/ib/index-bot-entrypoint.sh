#!/bin/bash

set -e
APP_CONFIG=${APP_CONFIG:-/opt/index-bot/application.yaml}
exec java $JVM_OPTS -jar index-bot.jar --spring.config.location=$APP_CONFIG
exit 500