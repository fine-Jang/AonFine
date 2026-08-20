#!/usr/bin/env bash
set -euo pipefail

injected_dir="$1"
driver_jar="$(find "${injected_dir}/.." -name cubrid-jdbc.jar -print -quit)"

if [[ -z "${driver_jar}" ]]; then
  echo "cubrid-jdbc.jar was not found in the S2I source tree" >&2
  exit 1
fi

module_dir="${JBOSS_HOME}/modules/system/layers/base/com/cubrid/main"
mkdir -p "${module_dir}" "${JBOSS_HOME}/extensions"
cp "${driver_jar}" "${module_dir}/cubrid-jdbc.jar"
cp "${injected_dir}/module.xml" "${module_dir}/module.xml"
cp "${injected_dir}/postconfigure.sh" "${JBOSS_HOME}/extensions/postconfigure.sh"
cp "${injected_dir}/datasource.cli" "${JBOSS_HOME}/extensions/datasource.cli"
chmod 0755 "${JBOSS_HOME}/extensions/postconfigure.sh"
