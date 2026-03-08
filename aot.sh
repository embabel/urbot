#!/usr/bin/env bash
rm -rf extracted
rm -rf target
mvn -DskipTests package
cp target/urbot*jar application.jar
java -Djarmode=tools -jar application.jar extract --destination extracted
java -Dspring.context.exit=onRefresh -jar extracted/application.jar
java -XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh -jar extracted/application.jar
rm -rf application.jar
mv app.aot extracted
#java -XX:AOTCache=extracted/app.aot -jar extracted/application.jar
