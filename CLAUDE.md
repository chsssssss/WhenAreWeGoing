# 언제가지 (WhenAreWeGoing)

인스타그램에서 본 맛집을 저장하고 나중에 꺼내 쓰는 개인용 안드로이드 앱.

## 앱 개요

- **핵심 흐름**: 인스타에서 공유 버튼 → URL만 즉시 저장(화면 없음) → 나중에 앱에서 가게 확정 → 외식할 때 지도에서 꺼내 봄
- **설계 원칙 1**: 저장은 절대 실패하지 않는다. 가게 이름을 못 찾아도, 네트워크가 끊겨도 URL은 저장된다
- **설계 원칙 2**: 자동 인식(캡션 파싱)은 최적화이지 필수 기능이 아니다. 실패해도 앱은 정상 동작한다

## 프로젝트 정보

- **패키지명**: com.github.chsssssss.eonje
- **GitHub**: chsssssss/WhenAreWeGoing
- **Minimum SDK**: API 26 (Android 8.0)
- **언어**: Kotlin
- **UI**: Jetpack Compose + Material 3

## 기술 스택

| 영역 | 선택 |
|---|---|
| UI | Jetpack Compose, Material 3 |
| 아키텍처 | MVVM + UDF, StateFlow 기반 단일 UiState |
| DI | Hilt |
| 로컬 DB | Room |
| 네트워크 | Retrofit + OkHttp + kotlinx.serialization |
| 비동기 | Coroutines + Flow |
| 백그라운드 | WorkManager |
| 지도 | 카카오맵 SDK (카카오 로컬 API와 좌표계 통일) |
| 네비게이션 | Navigation Compose |
| 이미지 | Coil |

## 패키지 구조

```
com.github.chsssssss.eonje/
├─ di/                 Hilt 모듈
├─ data/
│  ├─ local/           Room (Entity, Dao, Database)
│  ├─ remote/          Retrofit (Instagram, Kakao, LLM)
│  ├─ repository/      Repository 구현체
│  └─ worker/          WorkManager
├─ domain/
│  ├─ model/           도메인 모델
│  ├─ repository/      Repository 인터페이스
│  └─ usecase/
├─ ui/
│  ├─ home/            Screen + ViewModel + State
│  ├─ inbox/
│  ├─ resolve/         정리 화면 (단일/다중 모드)
│  ├─ placedetail/
│  ├─ accounts/
│  └─ theme/
└─ share/              ShareReceiverActivity
```

## 데이터 모델

게시물(SavedPost)과 장소(Place)는 다대다 관계다.
게시물 하나에 장소가 여럿일 수 있고(리스트형 게시물),
같은 가게가 여러 게시물에 나올 수도 있다.

```kotlin
@Entity(tableName = "saved_posts")
data class SavedPostEntity(
    @PrimaryKey val id: String,
    val shortcode: String?,
    val instagramUrl: String,
    val caption: String?,
    val thumbnailUrl: String?,
    val status: ResolveStatus,
    val extractedCount: Int,
    val createdAt: Long
)

@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey val id: String,
    val name: String?,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val kakaoPlaceId: String?,
    val category: String?,
    val memo: String?,
    val status: ResolveStatus,
    val createdAt: Long,
    val resolvedAt: Long?
)

@Entity(primaryKeys = ["postId", "placeId"])
data class PostPlaceCrossRef(
    val postId: String,
    val placeId: String
)

enum class ResolveStatus {
    PENDING,        // 저장 직후, 매칭 대기
    RESOLVED,       // 장소 확정
    NEEDS_REVIEW,   // 후보 여러 개 또는 다중 장소 — 사용자 선택 필요
    UNRESOLVED      // 캐시 미스, 파싱 실패, 미등록 계정
}

@Entity(tableName = "watched_accounts")
data class WatchedAccountEntity(
    @PrimaryKey val username: String,
    val igUserId: String,
    val profileImageUrl: String?,
    val lastSyncedAt: Long?
)

@Entity(tableName = "cached_media")
data class CachedMediaEntity(
    @PrimaryKey val shortcode: String,
    val username: String,
    val caption: String?,
    val permalink: String,
    val mediaType: String,           // IMAGE, VIDEO, CAROUSEL_ALBUM
    val mediaUrls: List<String>,     // TypeConverter로 직렬화, 캐리셀은 순서대로
    val timestamp: Long,
    val cachedAt: Long
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isPreset: Boolean
)

@Entity(primaryKeys = ["placeId", "tagId"])
data class PlaceTagCrossRef(
    val placeId: String,
    val tagId: String
)
```

인덱스: `SavedPostEntity.shortcode`, `CachedMediaEntity.shortcode`, `PlaceEntity.kakaoPlaceId`

## 화면 구성

| 화면 | 역할 |
|---|---|
| ShareReceiverActivity | UI 없음. 인텐트 수신 → 토스트 → finish() |
| HomeScreen | 지도 + 리스트 토글. 태그 필터 칩 |
| InboxScreen | 미확정 게시물 카드 목록. 미등록 계정 배너 |
| ResolveScreen | 단일/다중 모드. 위쪽 원본, 아래쪽 검색 |
| PlaceDetailScreen | 장소 상세 + 관련 게시물 목록 |
| AccountsScreen | 맛집 계정 등록·관리 |
| SettingsScreen | 동기화 주기, 토큰 상태 |

하단 네비게이션 3탭: 홈 / 인박스(배지) / 설정

## 핵심 기능 명세

### F1. 공유 수신

- ACTION_SEND + text/plain 인텐트 필터
- 투명 테마 액티비티. UI 없이 토스트만 띄우고 즉시 finish()
- URL에서 shortcode 추출: `/p/{code}/`, `/reel/{code}/`, `/tv/{code}/`
- 쿼리 파라미터(`?igsh=...`) 제거
- Room에 SavedPost 생성 (동기 처리, 밀리초 단위)
- 이후 WorkManager로 위임
- 미등록 계정이어도 저장은 정상 진행. UNRESOLVED로 인박스에 보관
- 등록 유도는 다음 앱 실행 시 인박스 상단 배너로

### F2. 장소 추출 및 매칭 (WorkManager)

1. shortcode → 로컬 캐시(CachedMedia) 조회
2. 미스 → UNRESOLVED 종료
3. 1차 파싱: 캡션 텍스트만 LLM 전달, `[{상호명, 지역, 메뉴}]` 배열 응답
4. 결과 없으면 2차 파싱: 캡션 + 이미지 함께 전달 (캐리셀 앞 3장)
5. 각 항목 카카오 로컬 검색
6. 상태 결정:
   - 추출 1건 + 검색 1건 → 자동 확정 (RESOLVED)
   - 추출 2건 이상 → 무조건 NEEDS_REVIEW (다중 모드)
   - 검색 결과 다수 → NEEDS_REVIEW (후보 최대 5개)
   - 추출/검색 실패 → UNRESOLVED
7. 확정 시 알림: "○○식당 저장됨"

다중 장소 자동 확정 금지: 리스트형 게시물은 원하는 가게만 골라 저장해야 한다.

### F3. 계정 등록 및 동기화

- Business Discovery API 사용
- username 입력 → 프로페셔널 계정 확인 → 등록
- WorkManager: 1일 1회, NetworkType.UNMETERED + requiresCharging
- 계정당 최근 50건 유지
- 등록 후 UNRESOLVED 게시물 중 해당 계정 것만 매칭 재시도

### Business Discovery 엔드포인트

```
GET https://graph.facebook.com/v25.0/{MY_IG_USER_ID}
  ?fields=business_discovery.username({TARGET})
          {media.limit(50){permalink,caption,timestamp,media_url,
                           media_type,children{media_url,media_type}}}
  &access_token={TOKEN}
```

필요 권한: instagram_basic, instagram_manage_insights, pages_read_engagement
토큰: local.properties에 보관, BuildConfig로 주입. 절대 커밋 금지. 60일마다 갱신.

## 예외 처리 원칙

**F1은 항상 성공한다.** 실패는 F2 이후 단계에서만 발생한다.

| 상황 | 처리 |
|---|---|
| 미등록 계정 공유 | UNRESOLVED 저장, 다음 앱 실행 시 배너 안내 |
| shortcode 캐시 미스 | UNRESOLVED, 조용히 인박스로 |
| 캡션에 상호명 없음 | 이미지 포함 2차 파싱 재시도 |
| 추출 2건 이상 | NEEDS_REVIEW, 다중 체크박스 화면 |
| media_url 만료 | 이미지 파싱 생략, 캡션 결과만 사용 |
| 릴스 리스트형 | 영상 파싱 불가, UNRESOLVED |
| 중복 공유 | 기존 레코드 유지, 신규 생성 안 함 |
| 이미 저장된 가게 | kakaoPlaceId 일치 시 게시물만 연결 |
| 네트워크 없음 | WorkManager 지수 백오프 재시도 |
| 토큰 만료 | 앱 내 배너, 동기화 중단. 공유 수신은 정상 동작 |

## 개발 단계

### 1단계 — 저장 파이프라인 (완료)

Business Discovery 없이 동작하는 최소 흐름.
이 단계만으로 실제로 쓸 수 있는 앱이 된다.

- [x] 의존성 추가 (Room, Hilt, Navigation Compose, Coroutines, Retrofit)
- [x] ShareReceiverActivity — 인텐트 수신, 토스트, finish()
- [x] URL 파싱 유틸 — shortcode 추출
- [x] Room 세팅 — SavedPostEntity, Dao, Database
- [x] InboxScreen — 저장된 카드 목록
- [x] ResolveScreen — 카카오 로컬 검색 + 장소 확정 (단일 모드)

### 2단계 — 조회 기능 (완료)

- [x] PlaceEntity, PostPlaceCrossRef Room 세팅
- [x] HomeScreen — 지도, 리스트 토글, 태그 필터
- [x] PlaceDetailScreen

### 3단계 — 자동 매칭 (App Review 병행) (완료)

- [x] WatchedAccountEntity, CachedMediaEntity Room 세팅
- [x] AccountsScreen — 계정 등록
- [x] Business Discovery 동기화 WorkManager
- [x] 캡션 파싱 WorkManager (LLM + 카카오 로컬)
- [x] ResolveScreen 다중 모드

### 3.5단계 — 이미지 파싱 (보류)

캡션 파싱 적중률 실측 후 도입 여부 결정. 아직 실측 전이라 보류 중.

### 현재: 4단계 — 다듬기

- [x] 위젯 — 인박스 정리 대기 개수를 보여주는 홈 화면 위젯
- [x] 정리 알림 — 정리 안 된 게시물이 있으면 주기적으로(7일마다) 알림
- [x] 오래된 미확정 항목 정리 제안 — 30일 넘은 항목을 인박스에서 일괄 삭제 제안

## 코딩 컨벤션

- 각 화면은 단일 UiState data class를 StateFlow로 노출
- 이벤트성 처리(토스트, 네비게이션)는 Channel 또는 SharedFlow로 분리
- Repository는 domain 레이어에 인터페이스, data 레이어에 구현체
- 모든 네트워크/DB 작업은 코루틴. UI 레이어에서 직접 호출 금지
