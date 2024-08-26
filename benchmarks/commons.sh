# console output
GREEN='\033[0;32m'
RED='\033[0;31m'

INFO="${GREEN}[INFO]"
ERR="${RED}[ERR ]"
NC="\033[0m"

# maven
MVN_SKIP_MISC="-Denforcer.skip -Dcheckstyle.skip -Dcobertura.skip -Drat.skip -Dlicense.skip -Dfindbugs.skip -Dgpg.skip -Dskip.npm -Dskip.gulp -Dskip.bower -Drat.numUnapprovedLicenses=100 -Dpmd.skip -Dmaven.javadoc.skip -Danimal.sniffer.skip"
MVN_SKIP_TESTS="-Dmaven.test.skip -DskipITs -DfailIfNoTests=false -Darchetype.test.skip -DskipTests"
MVN_COMPILE_OPTIONS="${MVN_SKIP_MISC} ${MVN_SKIP_TESTS}"

# JDKs
setJava() {
  java_version=$1
  case $java_version in
    7)
      JAVA_HOME=/usr/java/openjdk-7
      ;;
    8)
      JAVA_HOME=/usr/java/openjdk-8
      ;;
    11)
      JAVA_HOME=/usr/java/openjdk-11
      ;;
    15)
      JAVA_HOME=/usr/java/openjdk-15
      ;;
  esac
}

run_command() {
  echo -en "${INFO} $cmd_desc... "
  if [[ ! -z $log_file ]]; then
    echo "Command: ${cmd}" > $log_file
    $cmd >> $log_file 2>&1
  else
    $cmd >> /dev/null 2>&1
  fi
  # print command result
  if [[ $? -eq 0 ]]; then
    echo -e "${GREEN} SUCCESS! ${NC}"
    return 1
  else
    echo -e "${RED} FAILURE! ${NC}"
    return 0
  fi
}

