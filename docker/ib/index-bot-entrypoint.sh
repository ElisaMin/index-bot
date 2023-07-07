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
  # covert this command to post request by json content
  # curl -s "https://api.telegram.org/bot${bot_token}/sendMessage?chat_id=${bot_creator}&text=${encoded_message_text}" >>/dev/nul
  message_text=$(echo "$message_text" | sed 's/\\/\\\\/g; s/"/\\"/g; s/\n/\\n/g')
  # cut message to 4096 chars
  message_text=$(echo "$message_text" | head -c 4096)
  # warp message by "
  message_text="\"${message_text}\""
  # send message to api
  curl -s -X POST -H "Content-Type: application/json" -d "{\"chat_id\": \"${bot_creator}\", \"text\": ${message_text}}" "https://api.telegram.org/bot${bot_token}/sendMessage" >>/dev/null
}
temp_file=$(mktemp)
refl_dir=/out/refl/$(date +%m%d%a%H%M%S)/
mkdir $refl_dir
sendCreator "call_by_entrypoint"
if java -agentlib:native-image-agent=config-merge-dir=$refl_dir --version 2> >(tee "$temp_file") ; then
   rm -rf $refl_dir/*
   java -agentlib:native-image-agent=config-merge-dir=$refl_dir -jar index-bot.jar --spring.config.location="$APP_CONFIG" 2> >(tee "$temp_file")
fi

exit_msg="exit: status_code=$?"
exit_msg+=$'\n'$(cat "$temp_file")
sendCreator "$exit_msg"
exit 255