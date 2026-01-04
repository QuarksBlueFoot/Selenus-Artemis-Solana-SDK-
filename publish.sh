#!/bin/bash
export OSSRH_USERNAME="HeKK1r"
export OSSRH_PASSWORD="MMAf1gv7BiKiYRTMAHAh2hFZCc0aQdxsW"
export SIGNING_KEY="$(cat secret.asc)"
export SIGNING_PASSWORD="Grimmly23!"
./gradlew publish --no-daemon
