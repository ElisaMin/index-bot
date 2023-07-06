#!/bin/bash

set +e

function parse_yaml {
   local prefix=$2
   local s='[[:space:]]*' w='[a-zA-Z0-9_]*' fs=$(echo @|tr @ '\034')
   sed -ne "s|^\($s\):|\1|" \
        -e "s|^\($s\)\($w\)$s:$s[\"']\(.*\)[\"']$s\$|\1$fs\2$fs\3|p" \
        -e "s|^\($s\)\($w\)$s:$s\(.*\)$s\$|\1$fs\2$fs\3|p"  $1 |
   awk -F$fs '{
      indent = length($1)/2;
      vname[indent] = $2;
      for (i in vname) {if (i > indent) {delete vname[i]}}
      if (length($3) > 0) {
         vn=""; for (i=0; i<indent; i++) {vn=(vn)(vname[i])("_")}
         printf("%s%s%s=\"%s\"\n", "'$prefix'",vn, $2, $3);
      }
   }'
}

APP_CONFIG=${APP_CONFIG:-/opt/index-bot/application.yaml}

eval $(parse_yaml $APP_CONFIG)
# Check if bot_token and bot_creator are not null
if [ -z "$bot_token" ] || [ -z "$bot_creator" ]; then
  echo "Error: bot_token or bot_creator is null."
  exit 1
fi
function sendCreator {
   local message_text=$1
   encoded_message_text=${message_text// /%20}
   wget -qO- "https://api.telegram.org/bot${bot_token}/sendMessage?chat_id=${bot_creator}&text=${encoded_message_text}" >>/dev/null

   echo .
}
sendCreator "call_by_entrypoint"
java $JVM_OPTS -jar index-bot.jar --spring.config.location=$APP_CONFIG
sendCreator "exited_$?_trying_to_restart_maybe"
exit 500