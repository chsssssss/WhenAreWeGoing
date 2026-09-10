# 언제가지 (WhenAreWeGoing)

인스타그램에서 본 맛집을 저장하고 나중에 꺼내 쓰는 안드로이드 앱. 플레이스토어 공개 출시 예정.

## 앱 개요

- **핵심 흐름**: 인스타에서 공유 버튼 → URL만 즉시 저장(화면 없음) → 나중에 앱에서 가게 확정 → 외식할 때 지도에서 꺼내 봄
- **설계 원칙 1**: 저장은 절대 실패하지 않는다. 가게 이름을 못 찾아도, 네트워크가 끊겨도 URL은 저장된다
- **설계 원칙 2**: 자동 인식(캡션 파싱)은 최적화이지 필수 기능이 아니다. 실패해도 앱은 정상 동작한다
- **설계 원칙 3**: 서버 없이, 사용자가 늘어도 개발자 비용이 비례해서 늘지 않는 구조로 간다. 캡션 파싱은 Firebase AI Logic(Gemini 무료 티어)과 온디바이스 Gemini Nano를 쓰고, 지도 검색은 클라이언트에서 직접 호출한다

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
| 네트워크 | Retrofit + OkHttp + kotlinx.serialization (Instagram, 카카오 로컬) |
| 캡션 파싱 (AI) | 지원 기기는 ML Kit GenAI(Gemini Nano) 온디바이스 우선 시도, 아니면 Firebase AI Logic(Gemini Flash 무료 티어, App Check 필수)으로 폴백 |
| 비동기 | Coroutines + Flow |
| 백그라운드 | WorkManager |
| 지도 | 카카오맵 SDK (카카오 로컬 API와 좌표계 통일) |
| 네비게이션 | Navigation Compose |
| 이미지 | Coil |
| 백엔드 | 없음. 모든 API는 클라이언트에서 직접 호출 (아래 "비용·서버 구조" 참고) |

*캡션 파싱은 실기기(Galaxy Note9, API 29)에서 공유 → 파싱 → 카카오 검색 → 자동 확정까지 전 과정 검증 완료(2026-09-10). Firebase AI Logic은 App Check(디버그: Debug Provider, 릴리스: Play Integrity)가 없으면 요청 자체를 거부하므로 `EonjeApplication`에서 필수로 초기화한다.*

## 패키지 구조

```
com.github.chsssssss.eonje/
├─ di/                 Hilt 모듈
├─ data/
│  ├─ local/           Room (Entity, Dao, Database)
│  ├─ remote/          Retrofit (Instagram, Kakao) + Firebase AI Logic / ML Kit GenAI 클라이언트
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
3. 캡션 파싱 시도 순서:
   a. 기기가 ML Kit GenAI(Gemini Nano) 지원 시 온디바이스로 우선 시도 — 비용 0, 네트워크 불필요
   b. 미지원 기기이거나 온디바이스 실패 시 Firebase AI Logic으로 Gemini Flash 호출 (무료 티어, App Check 필요)
   c. 두 경로 모두 `@Generable` 구조화 출력으로 `[{상호명, 지역, 메뉴}]` 배열 응답 받기
4. 결과 없으면 2차 파싱: 캡션 + 이미지 함께 전달 (캐리셀 앞 3장). 이미지 입력은 온디바이스보다 Firebase AI Logic(Gemini) 경로가 안정적 — 비전은 클라우드 우선
5. 각 항목 카카오 로컬 검색 (클라이언트에서 직접 호출, 앱 서명 기반 키 제한)
6. 상태 결정:
   - 추출 1건 + 검색 1건 → 자동 확정 (RESOLVED)
   - 추출 2건 이상 → 무조건 NEEDS_REVIEW (다중 모드)
   - 검색 결과 다수 → NEEDS_REVIEW (후보 최대 5개)
   - 추출/검색 실패 → UNRESOLVED
7. 확정 시 알림: "○○식당 저장됨"

다중 장소 자동 확정 금지: 리스트형 게시물은 원하는 가게만 골라 저장해야 한다.

*3단계 참고: 위 1~7번 전부 코드로 구현돼 있고 Firebase 프로젝트도 생성돼, 온디바이스(ML Kit GenAI) 미지원 기기나 이미지 포함 2차 파싱도 Firebase AI Logic 경로로 정상 동작한다.*

### F3. 계정 등록 및 동기화

- Business Discovery API 사용
- username 입력 → 프로페셔널 계정 확인 → 등록
- WorkManager: 1일 1회, NetworkType.UNMETERED + requiresCharging
- 계정당 최근 25건 유지 — `media.limit(50)`은 캐러셀·비디오 하위 필드까지 한 번에 요청하면 Meta가 `"Please reduce the amount of data you're asking for"`(에러 코드 1)로 거부해서 낮춤. 25건 기준 응답이 10초 넘게 걸릴 수 있어 Instagram Retrofit 클라이언트만 타임아웃을 30초로 늘려뒀음(`NetworkModule`)
- 등록 후 UNRESOLVED 게시물 중 해당 계정 것만 매칭 재시도

### Business Discovery 엔드포인트

```
GET https://graph.facebook.com/v25.0/{MY_IG_USER_ID}
  ?fields=business_discovery.username({TARGET})
          {media.limit(25){permalink,caption,timestamp,media_url,
                           media_type,children{media_url,media_type}}}
  &access_token={TOKEN}
```

필요 권한: instagram_basic, instagram_manage_insights, pages_read_engagement
토큰: local.properties에 보관, BuildConfig로 주입. 절대 커밋 금지. 60일마다 갱신.

**규모 확장 시 병목** — 조회당하는 계정(맛집 계정)만 프로페셔널이면 되고, 조회 주체는 개발자 본인 토큰 하나로 충분하다. 즉 사용자가 각자 인스타 로그인을 할 필요가 없다. 다만 이 토큰 하나에 시간당 호출 제한이 걸리므로, 사용자가 많아지면 전체 동기화가 이 한도에 묶인다. 비용 문제가 아니라 속도 문제이며, 이 기능이 막혀도 F1·F2는 정상 동작하므로 규모가 커지면 이 기능만 잠시 끄고 App Review(Advanced Access) 통과 후 다시 켜는 방식으로 대응한다.

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

## 비용·서버 구조

플레이스토어 공개 출시가 목표이지만, 사용자가 늘어도 개발자가 사용량 비례 비용을 지지 않는 구조로 설계한다. **서버를 두지 않는다.**

| 기능 | 방식 | 비용 |
|---|---|---|
| 캡션 파싱 (F2) | ML Kit GenAI(Gemini Nano) 온디바이스 우선, 미지원 기기는 Firebase AI Logic으로 Gemini Flash 무료 티어 호출 (App Check 필수) | 0원. 온디바이스는 추론 자체가 기기에서 일어나 무제한. 무료 티어는 분당 15회·일일 1500회(2026년 9월 기준) — 앱 전체 합산 |
| 카카오 로컬 검색 | 클라이언트에서 REST API 직접 호출, 카카오 개발자 콘솔에서 앱 서명(SHA) 기반 키 제한 설정 | 카카오 무료 쿼터 내 |
| Business Discovery | 개발자 본인 토큰으로 클라이언트가 직접 호출 (키는 앱에 두지 않고 필요 최소 범위로 관리 검토) | 무료. 단 속도 제한 있음 (위 참고) |

**API 키를 앱에 직접 내장하지 않는 이유** — APK는 디컴파일 가능해서, 내장된 키는 노출되면 누구나 그 키로 무제한 요청을 보낼 수 있고 비용은 키 소유자에게 청구된다. Firebase AI Logic은 이 문제를 게이트웨이 구조로 해결해서, 서버를 직접 짜지 않아도 클라이언트에서 안전하게 Gemini를 호출할 수 있다. 카카오는 앱 서명 기반 키 제한으로 같은 문제를 완화한다.

**무료 티어를 넘어서면** — Gemini 무료 티어 일일 한도(1500건)를 앱 전체 사용량이 넘으면 그 시점에 Firebase 프로젝트를 유료(Blaze) 플랜으로 전환하고 사용량만큼만 과금되는 구조로 넘어간다. 지금 규모에서는 해당 사항이 아니며, 이 한도에 근접하는지는 출시 후 모니터링으로 판단한다.

**Claude API는 쓰지 않는다** — 무료 티어가 없어 사용량에 비례해 계속 비용이 발생하므로 이 프로젝트의 캡션 파싱에는 적합하지 않다. GPT API도 동일한 이유로 제외. (과거엔 Claude Haiku로 구현했었으나 이 원칙에 따라 걷어내고 온디바이스/Firebase AI Logic으로 교체했다.)

**App Check가 필수인 이유** — Firebase AI Logic은 남용 방지를 위해 App Check로 검증된 요청만 받는다(미설치 시 `ServerException: ... you must enforce Firebase App Check`로 전부 거부됨). `EonjeApplication.initFirebaseAppCheck()`에서 디버그 빌드는 Debug Provider, 릴리스 빌드는 Play Integrity Provider를 설치한다. Debug Provider의 디바이스별 토큰은 Firebase 콘솔(App Check > 앱 > 디버그 토큰 관리)에 등록해야 그 기기에서 호출이 통과한다 — 새 개발 기기를 쓸 때마다 필요한 일회성 작업.

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

### 3단계 — 자동 매칭 (App Review 병행) + 공개 출시 준비 (완료)

- [x] WatchedAccountEntity, CachedMediaEntity Room 세팅
- [x] AccountsScreen — 계정 등록 (+ ResolveScreen 지름길 버튼)
- [x] Business Discovery 동기화 WorkManager
- [x] 캡션 파싱 WorkManager 골격 — 캐시 조회 → 파싱 → 카카오 로컬 검색 → 상태 결정
- [x] ResolveScreen 다중 모드
- [x] ML Kit GenAI(Gemini Nano) 온디바이스 지원 기기 분기 처리 — `FeatureStatus.AVAILABLE` 확인 후 시도, 안 되면 조용히 폴백
- [x] 캡션 파싱을 Anthropic Claude API → 온디바이스 우선 / Firebase AI Logic 폴백으로 교체 — `CaptionParsingRepositoryImpl` 재작성 완료
- [x] Firebase AI Logic SDK 연동(코드) — `google-services.json` 없이 `FirebaseOptions`로 기본 앱 직접 초기화, App Check(디버그/Play Integrity) 연동 포함
- [x] Firebase 프로젝트 생성(Spark 무료 플랜) — `wherewegoing-a5493` 프로젝트 생성, `local.properties`에 `FIREBASE_PROJECT_ID`/`FIREBASE_APPLICATION_ID`/`FIREBASE_API_KEY` 채워 넣음, Vertex AI in Firebase API 활성화
- [x] 카카오 개발자 콘솔에서 앱 서명 기반 키 제한 설정

### 3.5단계 — 이미지 파싱 (보류)

캡션 파싱 적중률 실측 후 도입 여부 결정. 아직 실측 전이라 보류 중.

### 4단계 — 다듬기 (완료)

- [x] 위젯 — 인박스 정리 대기 개수를 보여주는 홈 화면 위젯
- [x] 정리 알림 — 정리 안 된 게시물이 있으면 주기적으로(7일마다) 알림
- [x] 오래된 미확정 항목 정리 제안 — 30일 넘은 항목을 인박스에서 일괄 삭제 제안

## 코딩 컨벤션

- 각 화면은 단일 UiState data class를 StateFlow로 노출
- 이벤트성 처리(토스트, 네비게이션)는 Channel 또는 SharedFlow로 분리
- Repository는 domain 레이어에 인터페이스, data 레이어에 구현체
- 모든 네트워크/DB 작업은 코루틴. UI 레이어에서 직접 호출 금지
