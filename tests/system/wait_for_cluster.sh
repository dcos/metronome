#!/bin/bash

while [[ true ]];
do
    HTTP_CODE=$((`curl -s -o /dev/null -w ''%{http_code}'' ''$DCOS_URL''`))
    if [[ $((HTTP_CODE)) -lt 400 && $((HTTP_CODE)) -ne 0 ]]; then
        echo "$DCOS_URL returned HTTP $HTTP_CODE. We're ready!"
        exit 0
    fi
    echo "Waiting for $DCOS_URL to become active, got HTTP $HTTP_CODE. Retrying..."
    sleep 5;
done

while [[ true ]];
do
    HTTP_CODE=$((`curl -s -o /dev/null -w ''%{http_code}'' ''$DCOS_URL/capabilities''`))
    echo "$DCOS_URL/capabilities returned HTTP $HTTP_CODE. We're ready!"
    if [[ $((HTTP_CODE)) -lt 400 && $((HTTP_CODE)) -ne 0 ]]; then
        exit 0
    fi
    echo "Waiting for $DCOS_URL/capabilities to become active, got HTTP $HTTP_CODE. Retrying..."
    sleep 5;
done