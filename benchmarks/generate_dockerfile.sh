#!/usr/bin/env bash

SCRIPT_DIR="$(cd $(dirname $0) && pwd)"

source ${SCRIPT_DIR}/commons.sh

DOCKERS_DIR="${SCRIPT_DIR}/dockers"
SUBJECT_GITS="${SCRIPT_DIR}/subject_gits"

if ! ls $SUBJECT_GITS >/dev/null 2>&1; then
  echo "Download benchmark archive (subject_gits.tar.gz) first!"
  exit
fi

for _benchmark_group in $SUBJECT_GITS/*; do
  benchmark_group=$(basename $_benchmark_group)
  for _git in $_benchmark_group/*; do
    git=$(basename $_git)
    benchmark_name=$(echo $git | cut -d'.' -f1)

    mkdir -p "${DOCKERS_DIR}/${benchmark_group}/${benchmark_name}"
    DF="${DOCKERS_DIR}/${benchmark_group}/${benchmark_name}/Dockerfile"
    
    echo -e "FROM artifact/java_base\n" >> $DF

    echo    "ARG SUBJECT_DIR=/subject" >> $DF
    echo -e "ARG LOGGING_DIR=/tmp/build_logs\n" >> $DF

    echo    "WORKDIR /tmp/scripts" >> $DF
    echo    "COPY commons.sh ." >> $DF
    echo    "COPY helper.sh ." >> $DF
    echo -e "COPY setup.sh .\n" >> $DF

    echo    "WORKDIR /tmp/scripts/metadata/${benchmark_group}/${benchmark_name}/" >> $DF
    echo -e "COPY ./metadata/${benchmark_group}/${benchmark_name}/npetest.json ./npetest.json" >> $DF
    
    echo    "WORKDIR /tmp/scripts/subject_gits/" >> $DF
    echo    "COPY ./subject_gits/${benchmark_group}/${git}/ ./${git}" >> $DF
    echo    "RUN \ " >> $DF
    echo -e "     mkdir -p ./${git}/refs/heads ./${git}/refs/tags\n" >> $DF

    echo    "WORKDIR /tmp/scripts" >> $DF
    echo    "RUN mkdir -p \$LOGGING_DIR \$SUBJECT_DIR" >> $DF
    echo    "RUN BENCHMARK_GROUP=${benchmark_group} SUBJECT_ID=${benchmark_name} \ " >> $DF
    echo    "      LOGGING_DIR=\${LOGGING_DIR} SUBJECT_DIR=\${SUBJECT_DIR} \ " >> $DF
    echo -e "      ./setup.sh\n" >> $DF

    echo    "RUN mv /tmp/scripts/metadata /metadata" >> $DF
    echo    "RUN mv /tmp/scripts/helper.sh /root/.helper" >> $DF
    echo    "RUN rm /tmp/scripts/commons.sh" >> $DF
    echo    "RUN rm /tmp/scripts/setup.sh" >> $DF
    echo -e "RUN rm -rf /tmp/scripts/subject_gits\n" >> $DF

    echo -e "RUN echo '. /root/.helper' >> /root/.bashrc" >> $DF

    echo    "WORKDIR /subject" >> $DF
    echo    "ENTRYPOINT [ \"/bin/bash\", \"-l\", \"-c\" ]" >> $DF
    echo    "CMD [ \"/bin/bash\" ]" >> $DF
  done
done
