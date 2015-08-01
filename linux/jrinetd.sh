#!/bin/bash
JRINETD_HOME=${JRINETD_HOME:-/opt/jrinetd}
JRINETD_CONF=${JRINETD_CONF:-jrinetd.conf}
JRINETD_MEM_MB=${JRINETD_MEM_MB:-64}
JRINETD_OPTS_DEF="-verbose:gc -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+PrintGCTimeStamps -showversion -XX:+PrintCommandLineFlags -XX:-PrintFlagsFinal"
JRINETD_OPTS="${JRINETD_OPTS:-${JRINETD_OPTS_DEF}}"
JRINETD_CLASSPATH=$(echo $JRINETD_HOME/lib/*.jar | tr ' ' ':')
#
do_reload () {
  touch ${JRINETD_HOME}/conf/${JRINETD_CONF}
}
do_run () {
  cd ${JRINETD_HOME}
  java -Dprogram.name=jrinetd ${JRINETD_OPTS} -Xmx${JRINETD_MEM_MB}m \
    -cp "${JRINETD_HOME}/conf/:${JRINETD_HOME}/keys/:${JRINETD_CLASSPATH}" \
    org.javastack.jrinetd.Jrinetd ${JRINETD_CONF}
}
do_start () {
  cd ${JRINETD_HOME}
  echo "$(date --iso-8601=seconds) Starting" >> ${JRINETD_HOME}/log/jrinetd.bootstrap
  nohup java -Dprogram.name=jrinetd ${JRINETD_OPTS} -Xmx${JRINETD_MEM_MB}m \
    -cp "${JRINETD_HOME}/conf/:${JRINETD_HOME}/keys/:${JRINETD_CLASSPATH}" \
    -Dlog.stdOutFile=${JRINETD_HOME}/log/jrinetd.out \
    -Dlog.stdErrFile=${JRINETD_HOME}/log/jrinetd.err \
    org.javastack.jrinetd.Jrinetd ${JRINETD_CONF} 1>>${JRINETD_HOME}/log/jrinetd.bootstrap 2>&1 &
  PID="$!"
  echo "jrinetd: STARTED [${PID}]"
}
do_stop () {
  PID="$(ps axwww | grep "program.name=jrinetd" | grep -v grep | while read _pid _r; do echo ${_pid}; done)"
  if [ "${PID}" = "" ]; then
    echo "jrinetd: NOT RUNNING"
  else
    echo "$(date --iso-8601=seconds) Killing: ${PID}" >> ${JRINETD_HOME}/log/jrinetd.bootstrap
    echo -n "jrinetd: KILLING [${PID}]"
    kill -TERM ${PID}
    echo -n "["
    while [ -f "/proc/${PID}/status" ]; do
      echo -n "."
      sleep 1
    done
    echo "]"
  fi
}
do_status () {
  PID="$(ps axwww | grep "program.name=jrinetd" | grep -v grep | while read _pid _r; do echo ${_pid}; done)"
  if [ "${PID}" = "" ]; then
    echo "jrinetd: NOT RUNNING"
  else
    echo "jrinetd: RUNNING [${PID}]"
  fi
}
case "$1" in
  run)
    do_stop
    trap do_stop SIGINT SIGTERM
    do_run
  ;;
  start)
    do_stop
    do_start
  ;;
  stop)
    do_stop
  ;;
  restart)
    do_stop
    do_start
  ;;
  reload)
    do_reload
  ;;
  status)
    do_status
  ;;
  *)
    echo "$0 <run|start|stop|restart|reload|status>"
  ;;
esac
