FROM azul/zulu-openjdk-centos:8-latest

COPY shr/build/distributions/shr-*.noarch.rpm /tmp/shr.rpm
RUN yum install -y /tmp/shr.rpm && rm -f /tmp/shr.rpm && yum clean all
COPY env/docker_shr /etc/default/bdshr
ENTRYPOINT . /etc/default/bdshr && java -jar /opt/bdshr/lib/shr-schema-*.jar && java -Dserver.port=$BDSHR_PORT -DSHR_LOG_LEVEL=$SHR_LOG_LEVEL -jar /opt/bdshr/lib/shr.war

