#!/usr/bin/env bash
mvn clean install -DskipTests
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 \
    -server -Xms128m -Xmx1276m -Xss10m \
    -cp src/main/resources/*:target/dependency/* \
    com.sellerworx.Application
