# Telegram 索引机器人
在线示例：https://t.me/zh_secretary_bot

### 运行环境 
docker compose

### run
```bash
# build
docker compose build
# deply files
cp -rf ./index-bot/src/main/resources/application.yaml ./compose-root/
cp -rf ./docker/lang ./compose-root/
cp -rf ./docker/es/elasticsearch.yml ./compose-root/
# test
docker compose up es -d --wait && docker compose up index-bot 
# runtime 
docker compose up es -d --wait && docker compose up index-bot -d

```