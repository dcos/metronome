#!/bin/bash

while [[ true ]];
do
    HTTP_CODE=$((`curl -s -o /dev/null -w ''%{http_code}'' ''$DCOS_URL''`))
    if [ $((HTTP_CODE)) -lt 400 ]; then
        exit 0
    fi
    echo "Waiting for $DCOS_URL to become active, got HTTP $HTTP_CODE. Retrying..."
    sleep 5;
done