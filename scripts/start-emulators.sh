#!/usr/bin/env bash
# Firebase Emulator Suite(Auth + Firestore)를 앱과 같은 프로젝트 ID로 띄운다.
#
# - firebase-tools 15는 Java 21 이상이 필요하다(기본 JAVA_HOME이 17이어도 이 스크립트가 21을 찾아 쓴다).
# - --project를 주지 않으면 .firebaserc의 default 프로젝트(= 앱의 FIREBASE_PROJECT_ID와 같은 wherewegoing)를 쓴다.
#   그래서 Auth와 Firestore가 같은 프로젝트로 모이고, 에뮬레이터 REST 조회도 앱과 같은 ID로 하면 된다.
# - 에뮬레이터만 띄운다. 운영 Firestore·Auth에는 아무것도 쓰지 않는다.
set -euo pipefail
cd "$(dirname "$0")/.."

if JAVA21="$(/usr/libexec/java_home -v 21 2>/dev/null)"; then
  export JAVA_HOME="$JAVA21"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

exec firebase emulators:start --only auth,firestore "$@"
