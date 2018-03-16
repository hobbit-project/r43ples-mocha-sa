#!/bin/bash

JAR=/r43ples/target/r43ples-console-client-jar-with-dependencies.jar
CONFIG=/r43ples/conf/r43ples.tdb.conf

VERSION_NUMBER=$2
GRAPH=http://r43ples.data
ADD_FILE=$1/datagen.addset.$VERSION_NUMBER.nt
DEL_FILE=$1/datagen.deleteset.$VERSION_NUMBER.nt

# Create initial graph
if [ $VERSION_NUMBER = 0 ]; then
  echo $(date +%H:%M:%S.%N | cut -b1-12)" : creating initial graph '"$GRAPH"'..."
  java -jar $JAR --config $CONFIG --new --graph $GRAPH
  java -jar $JAR --config $CONFIG -g $GRAPH -a $ADD_FILE -m 'apply changeset '$VERSION_NUMBER' on '$GRAPH
else
  java -jar $JAR --config $CONFIG -g $GRAPH -a $ADD_FILE -d $DEL_FILE -m 'apply changeset '$VERSION_NUMBER' on '$GRAPH
fi
