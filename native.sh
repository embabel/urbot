#!/usr/bin/env bash
export VAADIN_DEVMODE_ENABLED=false
#mvn -DskipTests -Pnative native:compile
rm -rf target
mvn -DskipTests clean -Pnative,production package native:compile
./target/urbot