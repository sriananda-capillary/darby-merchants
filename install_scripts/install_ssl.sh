#!/bin/bash
FILES=./install_certs/*
for f in $FILES
do

echo "installing cert file $f" 
name=$(basename "$f") 
if keytool -importcert -noprompt -file $f -keystore $JAVA_HOME/jre/lib/security/cacerts -storepass changeit -alias "${name%.*}"; then
echo "certificate imported successfully"
else
echo "certificate importe failed"
fi

done
