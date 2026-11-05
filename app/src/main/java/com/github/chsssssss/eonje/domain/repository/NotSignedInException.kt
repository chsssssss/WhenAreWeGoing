package com.github.chsssssss.eonje.domain.repository

/**
 * 저장소가 아직 쓸 수 없는 상태(익명 로그인 전)일 때 던진다. 설치 직후 오프라인이면 로그인이 안 돼서 생길 수 있다.
 * 호출한 쪽은 유실 없이 나중에 다시 시도하거나(워커 재시도), 사용자에게 알려야 한다.
 */
class NotSignedInException : IllegalStateException("아직 로그인되지 않아 저장소를 쓸 수 없다")
