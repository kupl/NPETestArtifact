FROM artifact/java_base as build

ADD ./tool /tmp/npetest

WORKDIR /tmp
RUN  cd npetest && \
    JAVA_HOME=/usr/java/openjdk-11 mvn package -DskipTests -DskipITs -B

WORKDIR /tools/npetest
RUN mv /tmp/npetest/master/target/evosuite-master-*-tests.jar evosuite-tests.jar && \
    mv /tmp/npetest/master/target/evosuite-master-*.jar evosuite.jar && \
    mv /tmp/npetest/standalone_runtime/target/evosuite-standalone-runtime-*-tests.jar evosuite-rt-tests.jar && \
    mv /tmp/npetest/standalone_runtime/target/evosuite-standalone-runtime-*.jar evosuite-rt.jar && \
    rm -rf /tmp/npetest

WORKDIR /tools/npetest

WORKDIR /

ENTRYPOINT [ "/bin/bash", "-l", "-c" ]
CMD [ "/bin/bash" ]
