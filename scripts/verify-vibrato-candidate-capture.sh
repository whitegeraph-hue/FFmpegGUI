#!/usr/bin/env bash
# Runs inside the emulator session. Gradle uninstalls test apps after each run,
# so collect durable logcat and XML, not deleted application external files.
set -u
mkdir -p ci-shots
rc=0
run_test() {
  local method=$1 label=$2 result collector
  adb shell am force-stop com.cuscus.ffmpeggui.debug || return 1
  adb logcat -c || return 1
  adb logcat -s VIBRATO-CANDIDATE > "ci-shots/$label.txt" &
  collector=$!
  ./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.cuscus.ffmpeggui.VibratoCandidateRuntimeTest#$method" -Pandroid.testInstrumentationRunnerArguments.candidateDspValidation=true --stacktrace
  result=$?
  sleep 1
  kill "$collector" || return 1
  wait "$collector" || true
  cp -r app/build/outputs/androidTest-results "ci-shots/test-results-$label" || return 1
  return "$result"
}
run_test unpaddedSilenceNegativeControl negative-control || rc=1
[ "$(grep -c 'UNPADDED f' ci-shots/negative-control.txt)" -eq 3 ] || rc=1
grep -q 'NEGATIVE-COMMAND' ci-shots/negative-control.txt || rc=1
grep -q 'NEGATIVE-RUNTIME.*ffmpeg version' ci-shots/negative-control.txt || rc=1
for trial in 1 2 3; do
  label="vibrato-candidate-process-$trial"
  run_test fullPeriodInitializationRealBuilderScopesAndKotlinParity "$label" || rc=1
  file="ci-shots/$label.txt"
  grep -q 'SUMMARY cases=228 failures=0' "$file" || rc=1
  [ "$(grep -c 'COMMAND id=' "$file")" -ge 456 ] || rc=1
  grep -q 'RUNTIME.*ffmpeg version' "$file" || rc=1
  grep -q 'REWRITE count=1' "$file" || rc=1
done
exit "$rc"
