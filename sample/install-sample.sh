#!/usr/bin/env bash

wget https://raw.githubusercontent.com/KBNLresearch/oai-pmh-bulk-downloader/master/dist/oai-pmh-bulk-downloader-latest.jar
wget https://raw.githubusercontent.com/KBNLresearch/oai-pmh-bulk-downloader/master/sample/example.yaml
wget https://raw.githubusercontent.com/KBNLresearch/oai-pmh-bulk-downloader/master/sample/anp.xsl

mkdir -p output/in
mkdir output/rejected
mkdir output/processing

java -jar oai-pmh-bulk-downloader-latest.jar server example.yaml > application.log &
SERVER_PID=$!

echo "--- waiting 5 seconds for server to boot, please wait ---"
sleep 5

echo "--- loading database schema ---"
curl -XPOST http://localhost:18081/tasks/create-database-schema

echo "--- adding anp.xsl stylesheet ---"
curl -XPOST -F 'file=@anp.xsl' http://localhost:18080/stylesheets
echo "--- adding anp oai/pmh repository configuration ---"
curl -XPOST -H 'Accept: application/json' -H 'Content-type: application/json' -d@anp.json  http://localhost:18080/repositories/

echo "--- sample loaded ---"
echo "visit http://localhost:18080 in your browser to visit the dashboard."
echo "run 'kill $SERVER_PID' to stop the application"