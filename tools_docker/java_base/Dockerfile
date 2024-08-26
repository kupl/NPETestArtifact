FROM maven:3.8.1-openjdk-8 as java8
FROM maven:3.8.1-openjdk-11 as java11
FROM maven:3.8.1-openjdk-15 as java15
COPY --from=java8 /usr/local/openjdk-8 /usr/java/openjdk-8
COPY --from=java11 /usr/local/openjdk-11 /usr/java/openjdk-11

RUN microdnf install jq tmux vim ctags python3-pip wget procps

### Java experiment environment
# Setup JDKs
RUN echo "alias setJava8='export JAVA_HOME=/usr/java/openjdk-8 && export PATH=\${JAVA_HOME}/bin:\${PATH}'" >> /root/.bashrc
RUN echo "alias setJava11='export JAVA_HOME=/usr/java/openjdk-11 && export PATH=\${JAVA_HOME}/bin:\${PATH}'" >> /root/.bashrc
RUN echo "alias setJava15='export JAVA_HOME=/usr/java/openjdk-15 && export PATH=\${JAVA_HOME}/bin:\${PATH}'" >> /root/.bashrc

# junit test driver
WORKDIR /tools/junit
RUN wget https://repo1.maven.org/maven2/junit/junit/4.12/junit-4.12.jar -O junit.jar
RUN wget https://repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar -O hamcrest-core.jar

# pandas
RUN pip3 install pandas


###  Miscellaneous setup
WORKDIR /tmp
COPY configs /tmp/configs

# vim
RUN git clone https://github.com/VundleVim/Vundle.vim.git /root/.vim/bundle/Vundle.vim
RUN cat configs/vimrc >> /root/.vimrc
RUN vim +PluginInstall +qall >/dev/null 2>&1

# tmux
RUN cat configs/tmux.conf >> /root/.tmux.conf

# bashrc
RUN cat configs/bashrc >> /root/.bashrc
RUN rm -rf /tmp/configs

# disable maven-default-http-blocker
WORKDIR /tmp/scripts
COPY disable_maven_blocker.sh .
RUN ./disable_maven_blocker.sh

WORKDIR /root

ENTRYPOINT [ "/bin/bash" ]
