#!/usr/bin/env bash

SCRIPT_DIR="$(cd $(dirname $0) && pwd)"
SUBJECT_GITS="${SCRIPT_DIR}/subject_gits"
METADATA_DIR="${SCRIPT_DIR}/metadata"

source ${SCRIPT_DIR}/commons.sh

if [[ -z $SUBJECT_DIR ]]; then
  echo -e "${ERR} SUBJECT_DIR must be set ${NC}" 
  exit
fi

if [[ -z $LOGGING_DIR ]]; then
  echo -e "${ERR} LOGGING_DIR must be set ${NC}" 
  exit
fi

prepare_benchmark() {
  benchmark_group=$1
  benchmark_group_dir=$2
  git_dir=$3
  if [[ ! -d $git_dir ]]; then
    return
  fi
  project_dir_name=$(basename $git_dir | rev | cut -c 5- | rev)
  project_dir="${benchmark_group_dir}/${project_dir_name}"
  metadata_dir="${METADATA_DIR}/${benchmark_group}/${project_dir_name}"
  
  if [[ ! -d $project_dir ]]; then
    mkdir -p $project_dir
  fi
  cp -rf $git_dir $project_dir/.git
  (
    # Setting up source code with .git
    cd $project_dir
    mkdir -p .git/refs/heads .git/refs/tags >/dev/null 2>&1
    cmd_desc="Creating directory of subject program : $project_dir_name"
    cmd="git reset --hard buggy --quiet"
    log_file=""
    run_command
    
    # Read meta data
    metadata_file="${metadata_dir}/npetest.json"
    java_version="$(jq -r .build_info.java_version $metadata_file)"
    mvn_opt_extra="$(jq -r .build_info.mvn_opt_extra $metadata_file)"
  
    # Setting up build configuration
    project_build_log_dir="${LOGGING_DIR}/${benchmark_group}/${project_dir_name}"
    mkdir -p "${project_build_log_dir}"
    setJava $java_version

    build_result="${project_build_log_dir}/build_result.json"
    echo '{' > $build_result
  
    # Install dependencies for if the subject is multi-module project
    cmd_count=$(jq -r '.commands.build[].cmd' $metadata_file | wc -l)
    for _i in $(seq $cmd_count); do
      i=$((_i-1))
      cwd=$(jq -r ".commands.build[$i].cwd" $metadata_file)
      cmd=$(jq -r ".commands.build[$i].cmd" $metadata_file)
      cmd_desc="Command $i"
      log_file="${project_build_log_dir}/mvn_cmd_$i.log"
      (
        cd "$project_dir/$cwd"
        run_command
      )
      echo "  \"cmd_$i\" : $?," >> $build_result
    done

    if [[ ! -z $(tail -n 1 $build_result | grep ',') ]]; then
      sed -i '$ s/,//g' $build_result
    fi

    echo '}' >> $build_result
    echo -e "${INFO}${NC}"
  )
}


if [[ -z $BENCHMARK_GROUP ]]; then
  echo -e "${INFO} BENCHMARK_GROUP should be set if MODE != all ${NC}"
fi

if [[ -z $SUBJECT_ID ]]; then
  echo -e "${INFO} SUBJECT_ID should be set if MODE != all ${NC}"
fi

benchmark_group=$BENCHMARK_GROUP
benchmark_group_dir="${SUBJECT_DIR}/${benchmark_group}"
git_dir="${SUBJECT_GITS}/${SUBJECT_ID}.git"
prepare_benchmark $benchmark_group $benchmark_group_dir $git_dir

