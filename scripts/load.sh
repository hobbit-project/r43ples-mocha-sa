#!/bin/bash

JAR=/r43ples/target/r43ples-console-client-jar-with-dependencies.jar
CONFIG=/r43ples/conf/r43ples.tdb.conf

VERSION_NUMBER=$2
GRAPH=http://r43ples.data
ADD_FILE=$1/datagen.addset.$VERSION_NUMBER.nt
DEL_FILE=$1/datagen.deleteset.$VERSION_NUMBER.nt

# Create initial graph
if [ $VERSION_NUMBER = 0 ]; then
  java -jar $JAR -c $CONFIG --new -g $GRAPH -a $ADD_FILE -m 'create initial revision' 
else
  java -jar $JAR -c $CONFIG -g $GRAPH -a $ADD_FILE -d $DEL_FILE -m 'apply changeset '$VERSION_NUMBER' on '$GRAPH
fi
