#!/bin/bash

MODE=false
ENDPOINT=http://localhost:9998/r43ples/sparql
QUERY=$1

echo $(curl -H "Accept: application/json" --data "query_rewriting=$MODE&query=$QUERY" $ENDPOINT)
