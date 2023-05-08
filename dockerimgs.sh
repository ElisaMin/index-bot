docker build . -t index-bot:latest
cd package/config
docker build . -t es:latest
cd ../..
docker compose build