FROM openjdk:8-jdk
EXPOSE 8081

#Create persistent dir for heapdump and storage
RUN mkdir -p main_jars dependency heapdump storage install_scripts install_certs
COPY target/*jar ./main_jars/
#Find more efficient way to not copy in the first place
RUN rm main_jars/*tests.jar

COPY target/dependency/*jar ./dependency/

COPY install_scripts/* ./install_scripts/

COPY install_certs/* ./install_certs/

RUN chmod +x ./install_scripts/*.sh

#RUN apt-get update && apt-get -y install sudo

RUN ./install_scripts/install_ssl.sh

CMD java -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9340 -Dcom.sun.management.jmxremote.ssl=false \
		-Dcom.sun.management.jmxremote.authenticate=false -Djava.rmi.server.hostname=0.0.0.0 \
		-server -Xms128m -Xmx5632m -Xss10m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/log/capillary/darby/storage \
		-Dspring.datasource.url=${DB_URL} -Dspring.datasource.username=${DB_USER} -Dspring.datasource.password=${DB_PASS} \
		-Drabbitmq.esb.instance.host=${RMQ_URL} -Drabbitmq.esb.instance.username=${RMQ_USER} -Drabbitmq.esb.instance.password=${RMQ_PASS} -Drabbitmq.esb.instance.port=5672 \
		-Desb.env.type=${POD_NAME} \
		-Dazure.namespace=${AZURE_NAMESPACE} -Dazure.resourceGroupName=${AZURE_RESOURCE_GROUP_NAME} -Dazure.directoryId=${AZURE_DIRECTORY_ID} -Dazure.applicationId=${AZURE_APPLICATION_ID} -Dazure.applicationKey=${AZURE_APPLICATION_KEY} -Dazure.sharedAccessKeyName=${AZURE_SHARED_ACCESS_KEY_NAME} -Dazure.timeToLive=${TIME_TO_LIVE} \
		-Dalert.mail.smtp.username=${SMTP_ALERT_USER} -Dalert.mail.smtp.password=${SMTP_ALERT_PASS} \
		-Ddata.mongodb.host=${MONGO_URL} -Ddata.mongodb.username=${MONGO_USER} -Ddata.mongodb.password=${MONGO_PASS} \
		-DdataImportSource.url=${DATA_IMPORT_DB_URL} -DdataImportSource.username=${DATA_IMPORT_DB_USER} -DdataImportSource.password=${DATA_IMPORT_DB_PASS} -DscheduledTask.dataImportTable.ttl=${DATA_IMPORT_TABLE_TTL} \
		-Desb.env.region=${ENV_REGION} \
		-Dgraphite.host=carbon.ecom.capillary.in \
		-cp "main_jars/*:resources/*:dependency/*" com.sellerworx.Application

#DB_URL reused for dataImportSource also
