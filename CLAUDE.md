# 언제가지 (WhenAreWeGoing)

인스타그램에서 본 맛집을 저장하고 나중에 꺼내 쓰는 안드로이드 앱. 플레이스토어 공개 출시 예정.

## 앱 개요

- **핵심 흐름**: 인스타에서 공유 버튼 → URL만 즉시 저장(화면 없음) → 나중에 앱에서 가게 확정 → 외식할 때 지도에서 꺼내 봄
- **설계 원칙 1**: 저장은 절대 실패하지 않는다. 가게 이름을 못 찾아도, 네트워크가 끊겨도 URL은 저장된다
- **설계 원칙 2**: 자동 인식(캡션 파싱)은 최적화이지 필수 기능이 아니다. 실패해도 앱은 정상 동작한다
- **설계 원칙 3**: 직접 운영하는 서버는 두지 않는다. 데이터의 원본은 Firestore(서버리스)이고, 캡션 파싱은 Firebase AI Logic(Gemini 무료 티어)과 온디바이스 Gemini Nano를 쓰며, 지도 검색은 클라이언트에서 직접 호출한다. 커스텀 백엔드 코드는 Firebase Cloud Functions 함수 하나(게시물 URL로 계정명·캡션을 Apify로 대신 조회)뿐이고, Apify 토큰을 앱에 내장하지 않기 위한 최소한의 게이트웨이다. **비용 원칙은 "사용량에 비례하지 않는 구조"에서 "무료 한도 안에서 운영하고 예산 알림으로 감시"로 바뀌었다** — Firestore는 사용량 비례 과금이라서(2026-11 결정: 이후 iOS 앱과 친구 공유를 붙이려면 데이터가 서버에 있어야 한다), 무료 한도를 넘기지 않는지를 예산 알림으로 본다. 자세한 건 "비용·서버 구조" 참고
- **설계 원칙 4**: 앱의 핵심 동작은 "인스타 보다가 마음에 드는 것만 공유"다. 계정을 통째로 팔로우해서 전부 자동 수집하는 기능(과거 F3)은 이 사용 패턴과 안 맞아서 걷어냈다 — 자세한 배경은 "개발 단계" 5단계 참고

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
| 데이터 원본 | **Firestore**(named FirebaseApp `wherewegoing`, 영구 캐시 20MB) — 개인 데이터는 전부 `users/{uid}` 아래 |
| 로그인 | Firebase Auth 익명 로그인(named 앱 `wherewegoing`). 계정 UI는 없다 |
| 로컬 DB | Room — **읽기 전용 레거시**. 업데이트 전 사용자의 데이터를 Firestore로 1회 옮기려고 남겨둔 것(다음 버전에서 제거 예정) |
| 네트워크 | Retrofit + OkHttp + kotlinx.serialization (카카오 로컬) + Firebase Functions(계정명·캡션 조회) + Firestore |
| 캡션 파싱 (AI) | 지원 기기는 ML Kit GenAI(Gemini Nano) 온디바이스 우선 시도, 아니면 Firebase AI Logic(Gemini Flash 무료 티어, App Check 필수)으로 폴백 |
| 비동기 | Coroutines + Flow |
| 백그라운드 | WorkManager |
| 지도 | 카카오맵 SDK (카카오 로컬 API와 좌표계 통일) |
| 네비게이션 | Navigation Compose |
| 이미지 | Coil |
| 백엔드 | 직접 운영하는 서버 없음. 데이터는 Firestore(보안 규칙 `firestore.rules`: 본인 `users/{uid}`의 folders·places·posts만 읽기·쓰기·삭제, 그 밖의 경로는 전부 거부, 쓴 뒤의 문서 전체에 필드 허용 목록·길이·enum·좌표·배열 크기 검증), 게시물 계정명·캡션 조회만 Firebase Cloud Functions 함수 하나(Apify 토큰 보관용 게이트웨이)를 거친다 (아래 "비용·서버 구조" 참고) |

*캡션 파싱은 실기기(Galaxy Note9, API 29)에서 공유 → Apify 캡션 조회 → Gemini 파싱 → 카카오 검색 → 자동 확정까지 전 과정 검증 완료(2026-09-12). Firebase AI Logic은 App Check(디버그: Debug Provider, 릴리스: Play Integrity)가 없으면 요청 자체를 거부하므로 `EonjeApplication`에서 필수로 초기화한다. Gemini는 Blaze(결제 계정 연결) 프로젝트에서 호출하면 무료 티어 대신 Prepay(선불) 결제로 전환돼버려서, Gemini 전용 프로젝트(`fir-gemini-706d3`, Spark 유지)를 Apify 게이트웨이 프로젝트(`wherewegoing-a5493`, Blaze)와 분리해뒀다 — 자세한 내용은 "비용·서버 구조" 참고.*

## 패키지 구조

```
com.github.chsssssss.eonje/
├─ di/                 Hilt 모듈 (RepositoryModule: 기본 바인딩은 Firestore, @RoomStore는 이전용 Room 구현 / FirestoreModule: named 앱 wherewegoing)
├─ data/
│  ├─ firestore/       Firestore 저장소 구현 4개, AuthSession(익명 로그인), FirestoreMappers(도메인 ↔ 문서), FirestoreSetup(캐시 설정·에뮬레이터 연결 순서)
│  ├─ migration/       Room → Firestore 1회 이전 (RoomToFirestoreMigrator, 진행 기록 RoomMigrationStateStore, RoomMigrationStatusImpl)
│  ├─ local/           Room(Entity, Dao, Database — 레거시, 읽기 전용), ThumbnailStore
│  ├─ mapper/          Room Entity ↔ 도메인 모델
│  ├─ remote/          Retrofit(Kakao) + Firebase Functions/AI Logic 앱 이름 상수(FirebaseApps)
│  ├─ repository/      Firestore가 아닌 Repository 구현체 + Room 구현(RoomXxxRepository, 이전용)
│  └─ worker/          WorkManager (CaptionParsingWorker, SaveSharedPostWorker, RoomMigrationWorker, InboxReminderWorker, ThumbnailMaintenanceWorker)
├─ domain/
│  ├─ model/           도메인 모델 (SavedPost, Place, Folder, ExtractedCandidate … — 저장소 구현에 의존하지 않는 순수 모델)
│  ├─ repository/      Repository 인터페이스 + UserSession, PostWorkScheduler, LegacyMigrationStatus
│  ├─ usecase/         SaveSharedPostUseCase, MatchPostUseCase
│  └─ util/            InstagramUrlParser, ShareKey
├─ ui/
│  ├─ home/            Screen + ViewModel + State — 지도, 커스텀 바텀시트(목록/상세 모드), 폴더 관리
│  ├─ inbox/
│  ├─ resolve/         정리 화면 (단일/다중 모드)
│  ├─ components/      화면 간 공유 컴포저블 (FolderPicker, PlaceholderImage, BottomNavBar 등)
│  ├─ settings/        테마(라이트/다크/시스템)·사용법·개인정보처리방침·버전 정보 (8단계 참고)
│  ├─ onboarding/      첫 실행 온보딩 (9단계 참고)
│  └─ theme/
├─ share/              ShareReceiverActivity
└─ widget/             홈 화면 위젯 '오늘의 맛집' (Glance, 10단계 참고)
scripts/start-emulators.sh   Firebase Emulator Suite(Auth+Firestore) 실행 (아래 "에뮬레이터" 참고)
firestore.rules / firestore.indexes.json   보안 규칙·인덱스 (배포는 `firebase deploy --only firestore`, CLI에서 직접)
```

## 데이터 모델

게시물(SavedPost)과 장소(Place)는 다대다 관계다.
게시물 하나에 장소가 여럿일 수 있고(리스트형 게시물),
같은 가게가 여러 게시물에 나올 수도 있다.

### 원본: Firestore (`users/{uid}` 아래, iOS에서도 쓸 수 있게 플랫폼 중립)

```
users/{uid}/folders/{folderId}   name, color, iconKey, createdAt
users/{uid}/places/{placeId}     name, address, latitude, longitude, kakaoPlaceId, category, status, createdAt, resolvedAt, folderId(null=미분류)
users/{uid}/posts/{postId}       instagramUrl, shortcode, caption, status, extractedCount, createdAt, unresolvedReason, placeIds[], candidates[]
```

- enum은 이름 문자열, 시간은 기기에서 만든 Long 밀리초(serverTimestamp 안 씀), 색은 ARGB 정수. **문서 ID는 UUID 문자열**이다 — 직접 공유하면 `ShareKey.postId(shortcode)`에서 만든 결정적 UUID, 장소·폴더는 `UUID.randomUUID()`, Room에서 옮긴 것은 Room의 UUID 그대로.
- **게시물-장소 연결은 게시물 문서의 `placeIds` 배열**이다(장소 문서엔 게시물 정보가 없다). **검색 후보는 별도 컬렉션이 아니라 게시물 문서의 `candidates` 배열**이다(정리하면 사라지는 임시 데이터, 항목당 최대 5개라 문서 크기 한도 1MiB에 한참 못 미친다). 비어 있는 배열은 필드를 만들지 않는다.
- 문서에 없는 것: `memo`(기능 제거됨), `thumbnailUrl` — 썸네일은 인스타 CDN을 `ThumbnailStore`가 기기에 받아둔 `{postId}.jpg`이고, 읽을 때 파일이 있으면 도메인 `SavedPost.thumbnailUrl`을 `file://`로 채우고 없으면 null이다(다른 기기에선 썸네일 없음을 허용, Storage는 안 쓴다).
- 필드 ↔ 도메인 변환은 `data/firestore/FirestoreMappers.kt` 한 곳이다. 이전된 데이터와 새로 만든 데이터의 문서 모양이 달라지면 안 되므로 새 필드는 여기서만 추가한다. **새 필드·enum 값을 추가하면 `firestore.rules`의 허용 목록과 `FirestoreRulesTest`도 같이 고친다** — 안 고치면 서버가 쓰기를 거부하고, 앱은 로그만 남기고 그 쓰기를 버린다(`logFailure`).
- 친구 공유는 별도 최상위 컬렉션으로 추가할 예정이라 이 구조가 막지 않는다(보안 규칙도 그때 따로 추가).
- 쿼리: 복합 인덱스가 필요한 쿼리는 없다(`firestore.indexes.json`은 비어 있는 게 맞다). 미확정 게시물은 `status != RESOLVED`(단일 필드 자동 인덱스).

### 도메인 모델 (`domain/model`)

화면·유스케이스·Repository 인터페이스는 도메인 모델(`SavedPost`, `Place`, `Folder`, `ExtractedCandidate` …)만 안다. Room Entity나 Firestore 문서 타입이 위로 새지 않는다. 변환은 `data/mapper`(Room)와 `data/firestore/FirestoreMappers.kt`(Firestore)에 있다.

### 레거시: Room 스키마 (v9, 읽기 전용 — 이전 전용으로만 남아 있다)

아래는 업데이트 전 사용자의 데이터가 들어 있는 Room 스키마다. **고정(v9, `app/schemas/.../9.json`)이고 이번 버전에서 바꾸거나 지우지 않는다.** 앱 코드는 Firestore만 읽고 쓰며, Room은 `RoomToFirestoreMigrator`만 읽는다.

```kotlin
@Entity(tableName = "saved_posts")
data class SavedPostEntity(
    @PrimaryKey val id: String,
    val shortcode: String?,
    val instagramUrl: String,
    val caption: String?,
    val thumbnailUrl: String?,   // 인스타 CDN URL이 아니라 ThumbnailStore가 받아둔 file:// 로컬 경로 (만료 안 됨)
    val status: ResolveStatus,
    val extractedCount: Int,
    val createdAt: Long,
    val unresolvedReason: UnresolvedReason?   // status == UNRESOLVED일 때만 값이 있음
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
    val resolvedAt: Long?,
    val folderId: String?     // null이면 미분류. 폴더는 장소당 하나(다대다 아님) — FolderEntity 참고
)

@Entity(primaryKeys = ["postId", "placeId"])
data class PostPlaceCrossRef(
    val postId: String,
    val placeId: String
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val color: Int = FolderVisuals.DEFAULT_COLOR,    // 지도 마커 배경색 — 채도 높은 8색 팔레트(FolderVisuals.colorPalette)에서 선택
    val iconKey: String = FolderVisuals.DEFAULT_ICON // 지도 마커에 얹는 이모지 아이콘
)

enum class ResolveStatus {
    PENDING,        // 저장 직후, 매칭 대기
    RESOLVED,       // 장소 확정
    NEEDS_REVIEW,   // 후보 여러 개 또는 다중 장소 — 사용자 선택 필요
    UNRESOLVED      // 캡션 조회·파싱 실패 — 구체적인 원인은 UnresolvedReason 참고
}

enum class UnresolvedReason {
    PLACE_NOT_FOUND,    // 캡션 파싱·카카오 검색에서 장소를 못 찾음 (비공개 게시물 등으로 캡션 자체를 못 가져온 경우 포함)
    NETWORK_ERROR,      // 재시도 한도 초과로 포기
}
```

인덱스: `SavedPostEntity.shortcode`, `PlaceEntity.kakaoPlaceId`, `PlaceEntity.folderId`

## 화면 구성

| 화면 | 역할 |
|---|---|
| ShareReceiverActivity | UI 없음. 인텐트 수신 → 토스트 → finish() |
| HomeScreen | 지도 + 커스텀 바텀시트(목록/상세 모드). 장소 상세는 별도 화면이 아니라 이 시트의 상세 모드로 통합돼 있다(과거 PlaceDetailScreen은 삭제됨) |
| InboxScreen | 미확정 게시물 카드 목록. 카드별 "후보 N곳" / "직접 찾기" 칩 |
| ResolveScreen | 단일/다중 모드. 위쪽 원본(열기 가능), 아래쪽 검색. 폴더 선택(선택사항, 다중 모드는 일괄 배정) |
| SettingsScreen | 화면 테마(시스템 설정/라이트/다크), 사용법(온보딩 다시 보기), 개인정보처리방침 링크, 버전 정보 (5단계에서 계정 관리·동기화 주기·토큰 상태를 걷어낸 뒤, 8단계에서 다시 채움) |
| OnboardingScreen | 첫 실행 시 한 번 뜨는 3장 안내. 홈 빈 상태 카드·설정 "사용법"에서 다시 볼 수 있다. 하단 네비게이션 없음 |

하단 네비게이션 3탭: 홈 / 인박스(배지) / 설정

### HomeScreen 바텀시트 구조

`AnchoredDraggableState` 기반 커스텀 바텀시트 — Material3의 `BottomSheetScaffold`는 정지 지점이 2개(peek/expanded)뿐이라 Peek/Mid(화면 40%)/Full(화면 95%) 3단 정지가 필요한 이 화면엔 못 쓴다.

- **목록 모드**: 시트 헤더(핸들+검색창+"장소 추가" 버튼+폴더 탭 줄)는 항상 고정, 장소 카드 리스트만 스크롤된다. 헤더 전체가 `anchoredDraggable`이라 어디를 잡고 끌어도 시트가 오르내린다. 폴더 탭 칩을 길게 누르면 삭제 확인 다이얼로그가 뜬다
- **상세 모드**: 장소를 탭하면 전환된다. 핸들만 고정, 이름부터 게시물 목록까지는 전부 한 스크롤 영역 — `NestedScrollConnection`이 스크롤이 맨 위/아래에 닿으면 남는 드래그를 시트로 넘긴다
- **지도 마커**: 일반 마커는 폴더 색+이모지 아이콘을 얹은 작은 원. 선택된 마커만 `ic_map_pin.xml`과 같은 경로를 폴더 색으로 채운 핀 모양(위가 둥글고 아래가 뾰족함)으로 바뀌고, 둥근 머리에 폴더 아이콘이 들어간다 — `KakaoMapView.kt`에서 Canvas로 직접 그린다(폴더별 색·아이콘 조합이라 정적 드로어블로는 표현이 안 됨)
- **장소 직접 추가**: 검색창 옆 "장소 추가" 버튼 → 카카오 로컬 검색 시트가 뜨고, 결과를 탭하면 게시물 없이 바로 저장된다(RESOLVED, 미분류). 같은 카카오 장소면 새로 만들지 않고 기존 걸 재사용
- **길찾기**: 장소 상세의 "길찾기" 버튼은 카카오맵 공식 공유 링크(`https://map.kakao.com/link/to/이름,위도,경도`)를 `ACTION_VIEW`로 연다 — 카카오맵 앱이 있으면 앱에서, 없으면 모바일 웹에서 열린다

## 핵심 기능 명세

### F1. 공유 수신

- ACTION_SEND + text/plain 인텐트 필터
- 투명 테마 액티비티. UI 없이 토스트만 띄우고 즉시 finish()
- URL에서 shortcode 추출: `/p/{code}/`, `/reel/{code}/`, `/tv/{code}/` — 인스타 게시물 링크만 받는다. 다른 URL(프로필·외부 사이트)은 자동 매칭을 시작할 수 없어 저장하지 않고 "인스타그램 게시물 링크를 찾을 수 없어요" 토스트만 띄운다
- `http://`, `m.instagram.com`, `instagr.am` 형태도 받아서 `https://www.instagram.com/{p|reel|tv}/{code}/`로 표준화해 저장한다(쿼리 `?igsh=...`·프래그먼트 제거). `fetchInstagramMeta`가 이 형태만 받기 때문 — `InstagramUrlParser.findPostUrl`
- 중복 확인과 저장은 프로세스 안에서 `Mutex`로 직렬화한다(연달아 공유 시 중복 생성 방지)
- **로그인돼 있으면**(`UserSession.isSignedIn()`) 기존 경로: 게시물 문서를 쓰고 로컬 캐시에 반영된 것까지 확인한 뒤(밀리초) `finish()`. 서버 응답은 기다리지 않는다 — 오프라인에서도 저장이 멈추지 않는다
- **로그인 전이면**(설치 직후 오프라인) **기다리지 않고** URL을 `SaveSharedPostWorker`(WorkManager 입력 데이터, 네트워크 조건)에 맡긴다. Worker가 직접 익명 로그인을 시도하고, 재시도 횟수 상한 없이 지수 백오프로 다시 시도한다(상한에서 포기하면 URL이 사라지므로). 앱 프로세스가 죽어도 WorkManager DB에 남는다
- 게시물 ID·고유 작업 이름·중복 확인은 모두 공유 키(`ShareKey`: shortcode, 없으면 URL 해시)에서 나온다 — 직접 저장과 Worker 저장이 겹쳐도 같은 문서가 된다
- 토스트: 온라인이면 "저장했어요", 오프라인이면 "저장했어요. 인터넷에 연결되면 정리를 시작해요"
- 이후 `PostWorkScheduler`로 캡션 조회(F2)를 고유 작업(KEEP)으로 예약 — Apify를 중복 호출하지 않는다

### F2. 장소 추출 및 매칭 (WorkManager)

1. **계정명·캡션 조회** — Firebase Cloud Function `fetchInstagramMeta` → Apify `apify/instagram-scraper` 액터로 게시물 URL만으로 캡션 원문(안 잘림) + 대표 이미지 URL을 받는다. 계정을 등록하거나 추적하지 않는다 — 이 게시물 하나를 푸는 데 그걸로 충분하다
1.5. 대표 이미지는 `ThumbnailStore`로 기기에 다운로드해서 `thumbnailUrl`에 file:// 경로로 저장한다 — 인스타 CDN의 media_url은 시간이 지나면 만료돼서 원본 URL을 그대로 저장해두면 나중에 인박스·상세화면에서 사진이 안 보이게 된다. 다운로드 실패 시엔 원본 URL을 그대로 폴백(최선 노력, F2 실패로 취급하지 않음). 게시물 삭제 시 로컬 파일도 함께 정리된다(`SavedPostRepositoryImpl.deleteByIds`)
1.9. 게시물이 아직 Firestore에 없는데 Room → Firestore 이전이 안 끝났으면(업데이트 전에 예약된 작업이 먼저 돈 경우) `MatchPostResult.WaitForMigration`으로 **재시도 횟수를 쓰지 않고** 기다린다. 이전이 끝났는데도 없으면 삭제된 게시물이라 조용히 끝낸다
2. 실패(비공개 게시물, Apify 오류, Cloud Run 일시 장애 등) → `MatchPostResult.Retry`로 WorkManager 지수 백오프 재시도. 재시도 한도(5회) 넘기면 `UNRESOLVED(NETWORK_ERROR)`
   - 캡션을 받아온 뒤 파싱·카카오 검색 단계에서 재시도하는 경우엔 저장해둔 캡션·로컬 썸네일을 그대로 쓰고 Apify를 다시 부르지 않는다(호출마다 크레딧 소모). 2차(이미지) 파싱도 원격 CDN URL 대신 로컬 썸네일을 쓴다
3. 캡션이 비어있으면 `UNRESOLVED(PLACE_NOT_FOUND)`
4. 캡션 파싱 시도 순서:
   a. 기기가 ML Kit GenAI(Gemini Nano) 지원 시 온디바이스로 우선 시도 — 비용 0, 네트워크 불필요
   b. 미지원 기기이거나 온디바이스 실패 시 Firebase AI Logic으로 Gemini Flash 호출 (무료 티어, App Check 필요, Gemini 전용 프로젝트 사용)
   c. 두 경로 모두 `@Generable` 구조화 출력으로 `[{상호명, 지역, 메뉴}]` 배열 응답 받기
   d. Gemini의 일시 오류(`ServerException` — 5xx·App Check 토큰 거부 포함, `QuotaExceededException`, `RequestTimeoutException`)는 `IOException`으로 감싸 재시도 대상으로 넘긴다. API 키 오류·서비스 비활성화 등 영구 오류만 바로 `PLACE_NOT_FOUND`로 종결
5. 결과 없으면 2차 파싱: 캡션 + 대표 이미지 함께 전달. 이미지 입력은 온디바이스보다 Firebase AI Logic(Gemini) 경로가 안정적 — 비전은 클라우드 우선
6. 각 항목 카카오 로컬 검색 (클라이언트에서 직접 호출, 앱 서명 기반 키 제한)
7. 상태 결정:
   - 추출 1건 + 검색 1건 → 자동 확정 (RESOLVED)
   - 추출 2건 이상 → 무조건 NEEDS_REVIEW (다중 모드)
   - 검색 결과 다수 → NEEDS_REVIEW (후보 최대 5개)
   - 캡션 파싱·카카오 검색 실패 → UNRESOLVED(`PLACE_NOT_FOUND`)
   - 재시도 한도 초과 → UNRESOLVED(`NETWORK_ERROR`)
8. 확정 시 알림: "○○식당 저장됨"

다중 장소 자동 확정 금지: 리스트형 게시물은 원하는 가게만 골라 저장해야 한다.

*실기기 검증 완료(2026-09-12): 공유 → Apify 캡션 조회 → Gemini 추출 → 카카오 검색 → 자동 확정까지 계정 등록 없이 전 과정 동작 확인.*

### F6. 폴더

- 장소는 폴더 하나에만 속한다. 기본값은 "미분류"
- 정리 화면(ResolveScreen)에서 장소 확정 시 폴더 선택은 선택사항 — 건너뛰면 자동으로 "미분류"에 들어간다. 강제하지 않는다
- 다중 모드에서 선택한 여러 장소를 한 번에 같은 폴더로 일괄 배정 가능
- 확정 이후에도 장소 상세 화면이나 리스트 뷰에서 언제든 폴더 재배정 가능
- 사용자가 폴더를 자유롭게 생성. 프리셋 없음 (태그와 달리 폴더명은 "성수 맛집", "회사 근처"처럼 사용자마다 쓰임이 달라 프리셋 의미 없음)
- 폴더 생성 시 지도 마커에 쓸 색상(채도 높은 8색)과 아이콘(이모지)을 함께 고른다 — `AddFolderContent`에 실시간 마커 미리보기 포함
- 장소 상세화면 헤더의 마커 버튼을 누르면 체크박스형 폴더 변경 시트(`FolderAssignSheetContent`)가 뜬다. 체크만으로는 안 바뀌고 저장 버튼을 눌러야 반영되며, 아무 것도 체크 안 하고 저장하면 배정이 지워진다("저장삭제")
- **삭제 연쇄 로직의 위치**(DAO가 아니라 Firestore 저장소 구현에 있다 — 문서 DB는 외래 키가 없어서 코드가 대신한다):
  - 폴더 삭제 = `FirestoreFolderRepository.delete`: 그 폴더의 장소를 전부 `folderId = null`로 바꾸는 쓰기를 먼저, 폴더 문서 삭제를 마지막에 WriteBatch(499개씩 나눠 커밋)로 보낸다. 중간에 끊겨도 "삭제된 폴더를 가리키는 장소"는 안 생긴다
  - 장소 삭제 = `FirestorePlaceRepository.delete`: 장소 문서 삭제 + 그 장소를 가리키는 게시물의 `placeIds`에서 `arrayRemove`(WriteBatch). 게시물은 남는다
  - 게시물 삭제 = `FirestoreSavedPostRepository.deleteByIds`: 문서 삭제 + 기기의 `{postId}.jpg` 삭제(`ThumbnailStore.deleteAll`)
  - 게시물 상태 변경 = `FirestoreSavedPostRepository.updateStatus`: `UNRESOLVED`로 바꿀 때 `extractedCount`를 같은 쓰기에서 0으로 되돌린다
- 폴더 삭제는 세 곳에서 가능하다 — ① 목록 카드의 폴더 칩으로 여는 기존 폴더 피커, ② 위의 폴더 변경 체크박스 시트, ③ 홈 목록 화면 폴더 탭 줄의 칩을 길게 누르기. 어느 경로든 확인 다이얼로그를 거치고, 삭제되면 그 폴더의 장소는 전부 미분류로 이동한다

## 예외 처리 원칙

**F1은 항상 성공한다.** 실패는 F2 이후 단계에서만 발생한다.

| 상황 | 처리 |
|---|---|
| 캡션 조회 실패(비공개 게시물, Apify/Cloud Run 오류 등) | WorkManager 지수 백오프 재시도, 한도 넘기면 UNRESOLVED(`NETWORK_ERROR`) |
| 캡션에 상호명 없음 | 이미지 포함 2차 파싱 재시도 |
| 추출 2건 이상 | NEEDS_REVIEW, 다중 체크박스 화면 |
| 릴스 리스트형 | 영상 파싱 불가, UNRESOLVED |
| 중복 공유 | 기존 레코드 유지, 신규 생성 안 함 |
| 이미 저장된 가게 | kakaoPlaceId 일치 시 게시물만 연결 |
| 네트워크 없음 | WorkManager 지수 백오프 재시도 |
| 공유 시점에 로그인 전(설치 직후 오프라인) | URL을 `SaveSharedPostWorker`에 맡겨 로그인 후 저장 — 유실 없음 |
| Room → Firestore 이전 도중 게시물을 못 찾음 | 이전이 끝날 때까지 횟수 없이 대기(`WaitForMigration`) |

## 비용·서버 구조

플레이스토어 공개 출시가 목표이지만, 사용자가 늘어도 개발자가 사용량 비례 비용을 지지 않는 구조로 설계한다. **자체 서버는 두지 않는다** (Apify 게이트웨이용 Cloud Functions 함수 하나는 예외 — 아래 참고).

| 기능 | 방식 | 비용 |
|---|---|---|
| 계정명·캡션 조회 (F2) | Firebase Cloud Function `fetchInstagramMeta`(App Check 필수) → Apify `apify/instagram-scraper` 액터. 실패하면 WorkManager가 재시도하고, 그래도 안 되면 UNRESOLVED로 조용히 종료 | Apify 무료 티어 월 $5 크레딧 ≈ 1,850건, 그 이상은 건당 $0.0027 (2026-09 기준). 캐시 없이 공유할 때마다 호출됨 |
| 캡션 파싱 (F2) | ML Kit GenAI(Gemini Nano) 온디바이스 우선, 미지원 기기는 Firebase AI Logic으로 Gemini Flash 무료 티어 호출 (App Check 필수, Gemini 전용 프로젝트) | 0원. 온디바이스는 추론 자체가 기기에서 일어나 무제한. 무료 티어는 분당 15회·일일 1500회(2026년 9월 기준) — 앱 전체 합산 |
| 카카오 로컬 검색 | 클라이언트에서 REST API 직접 호출, 카카오 개발자 콘솔에서 앱 서명(SHA) 기반 키 제한 설정 | 카카오 무료 쿼터 내 |
| Firestore(데이터 원본) | `wherewegoing-a5493`(Blaze)의 Firestore(서울 리전, 프로덕션 모드로 만들 예정 — 콘솔 작업은 11단계 참고). 쓰기는 호출만 하고 서버 응답을 기다리지 않는다. 리스너는 `shareIn(WhileSubscribed(5000))`로 화면을 벗어나면 해제, 위젯·`InboxReminderWorker`는 `Source.CACHE`로 캐시만 읽는다(서버 호출 금지). 영구 캐시 20MB 상한(`FirestoreSetup`) | 무료 한도(하루 읽기 5만·쓰기 2만·삭제 2만, 1GiB 저장) 안에서 운영, **예산 알림으로 감시**. 앱을 한 번 열 때 읽기는 대략 장소 수 + 확정 게시물 수 + 미확정 수 + 폴더 수(캐시가 유효하면 바뀐 문서만, 백그라운드 30분 넘으면 처음부터). Room 이전은 사용자당 1회: 쓰기 N건 + 읽기 약 N/30건 |
| Cloud Functions(Apify 게이트웨이) 인프라 | Cloud Run 2세대 위에서 실행. `wherewegoing-a5493`은 Secret Manager(Apify 토큰 보관)를 쓰려고 Blaze로 전환함 | Blaze 무료 티어: 월 200만 건 호출, 40만 GB-초, 5GB 아웃바운드까지 무료. 컨테이너 이미지는 배포 시 1일 뒤 자동 삭제 정책으로 스토리지 비용 억제 |

**API 키를 앱에 직접 내장하지 않는 이유** — APK는 디컴파일 가능해서, 내장된 키는 노출되면 누구나 그 키로 무제한 요청을 보낼 수 있고 비용은 키 소유자에게 청구된다. Firebase AI Logic은 이 문제를 게이트웨이 구조로 해결해서, 서버를 직접 짜지 않아도 클라이언트에서 안전하게 Gemini를 호출할 수 있다. 카카오는 앱 서명 기반 키 제한으로, Apify 토큰은 Firebase Cloud Function(Secret Manager) 뒤에 숨겨서 같은 문제를 해결한다.

**Gemini는 결제 계정과 분리된 프로젝트에 둔다** — Firebase AI Logic(Gemini Developer API)의 진짜 무료 티어는 결제 계정이 전혀 연결되지 않은 프로젝트에서만 유지된다. 프로젝트가 한 번 "Prepay(선불)" 상태로 전환되면 무료 티어로 자동 복귀가 안 되고 선불 잔액(최소 $10)이 있어야만 호출된다. 그래서 Apify 게이트웨이(Secret Manager 때문에 Blaze 필수인 `wherewegoing-a5493`)와 Gemini(`fir-gemini-706d3`, Spark 유지)를 완전히 다른 프로젝트로 분리했다. `EonjeApplication`에서 Gemini 프로젝트를 기본(default) FirebaseApp으로, `wherewegoing-a5493`을 이름 있는(named) FirebaseApp(`WHEREWEGOING_FIREBASE_APP_NAME`)으로 각각 초기화하고 App Check도 프로젝트별로 따로 설치한다 — Firebase AI Logic SDK가 이름과 무관하게 "기본 FirebaseApp"만 찾기 때문에 이 순서가 중요하다(`data/remote/FirebaseApps.kt` 참고).

**Firestore·Auth는 반드시 named FirebaseApp `wherewegoing`(Blaze)에 붙인다** — `FirebaseFirestore.getInstance(app)`, `FirebaseAuth.getInstance(app)`로 앱 인스턴스를 명시한다(`di/FirestoreModule`). `Firebase.firestore`·`Firebase.auth` 같은 KTX 단축형은 **default 앱(Gemini 전용, Spark)에 붙으므로 쓰지 않는다.** Gemini 프로젝트(`fir-gemini-706d3`)를 Blaze로 올리는 변경도 하지 않는다(위 "Gemini는 결제 계정과 분리된 프로젝트에 둔다" 참고). Firestore 설정(영구 캐시)은 첫 사용 전에 `EonjeApplication`에서 지정해야 하고, 순서는 **캐시 설정 → (디버그·옵션) 에뮬레이터 연결**이다 — `useEmulator`가 설정을 덮어쓰기 때문이다(`FirestoreSetup.configure`).

**에뮬레이터 (Auth + Firestore)** — 개발·테스트는 Firebase Emulator Suite로 한다(운영 데이터에 쓰지 않는다).
- 실행: `scripts/start-emulators.sh`(= `firebase emulators:start --only auth,firestore`). **firebase-tools 15는 Java 21 이상이 필요하다** — 기본 `JAVA_HOME`이 17이어도 스크립트가 JDK 21을 찾아 쓴다(`/usr/libexec/java_home -v 21`). 프로젝트는 `--project`를 주지 않으면 `.firebaserc`의 default(`wherewegoing`)라서 **Auth와 Firestore가 앱과 같은 프로젝트 ID로 모인다**(`demo-eonje`로 띄우면 Auth는 그 ID, Firestore는 앱 ID로 갈라져 REST 조회가 헷갈린다). 포트는 Auth 9099, Firestore 8080(`firebase.json`)
- 앱 연결: 디버그 빌드를 `-PfirebaseEmulator=true`로 빌드하면 Auth·Firestore가 에뮬레이터에 붙는다(안드로이드 에뮬레이터에서 호스트는 `10.0.2.2`, 실기기는 `adb reverse` 후 `-PfirebaseEmulatorHost=localhost`). 릴리스 빌드엔 이 연결 코드가 들어가지 않는다(`src/release`의 `connectFirebaseEmulators`는 아무것도 안 한다)
- 에뮬레이터 데이터는 메모리에만 있어서 에뮬레이터를 다시 켜면 사라진다. 에뮬레이터 REST 조회는 `Authorization: Bearer owner`로 보안 규칙을 우회한다
- 계측 테스트(`connectedDebugAndroidTest`)는 에뮬레이터가 떠 있어야 Firestore 테스트가 돈다(안 떠 있으면 건너뛴다 — 통과로 착각하지 말 것). `FirebaseEmulatorEnv`가 FirebaseApp을 프로세스에서 한 번만 만든다
- AVD는 데이터 파티션이 작으면 `INSTALL_FAILED_INSUFFICIENT_STORAGE`로 설치가 막힌다. 검증용 AVD(`Eonje_Test`, 데이터 10GB)를 따로 두고 쓴다(기존 AVD는 초기화하지 않는다)

**Claude API는 쓰지 않는다** — 무료 티어가 없어 사용량에 비례해 계속 비용이 발생하므로 이 프로젝트의 캡션 파싱에는 적합하지 않다. GPT API도 동일한 이유로 제외.

**App Check가 필수인 이유** — Firebase AI Logic은 남용 방지를 위해 App Check로 검증된 요청만 받는다(미설치 시 `ServerException: ... you must enforce Firebase App Check`로 전부 거부됨). 프로젝트 콘솔에서 해당 API(Firebase AI Logic/Generative Language API)의 App Check 강제 적용을 별도로 켜둬야 한다 — 클라이언트 SDK 설치만으로는 부족하다. `EonjeApplication`에서 디버그 빌드는 Debug Provider, 릴리스 빌드는 Play Integrity Provider를 프로젝트별로 설치한다 — 빌드 타입별 소스셋(`src/debug`, `src/release`의 `appCheckProviderFactory()`)에서 고르고 `firebase-appcheck-debug`는 `debugImplementation`이라 릴리스엔 디버그 프로바이더 코드가 아예 들어가지 않는다. Debug Provider의 디바이스별 토큰은 각 프로젝트의 Firebase 콘솔(App Check > 앱 > 디버그 토큰 관리, 또는 `firebase appcheck:debugtokens:create`)에 등록해야 그 기기에서 호출이 통과한다 — 새 개발 기기를 쓸 때마다, 그리고 프로젝트를 하나 더 쓰게 될 때마다 필요한 일회성 작업. **토큰은 앱 데이터에 저장되므로 앱을 지웠다 다시 설치하면(예: Play 스토어 빌드를 지우고 디버그 빌드 설치) 새 토큰이 생겨 다시 등록해야 한다** — 등록 전 공유한 게시물은 `fetchInstagramMeta`가 403(`App attestation failed`)으로 거부돼 재시도 5회 후 `UNRESOLVED(NETWORK_ERROR)`로 끝난다(2026-10-31, Apify 호출 전 단계라 과금 없음). `fetchInstagramMeta` Cloud Function도 `enforceAppCheck: true`로 동일하게 보호한다. fir-gemini의 Firebase AI Logic은 App Check **재생 보호(1회용 토큰)**까지 켜져 있어서, `Firebase.ai(...)`에 `useLimitedUseAppCheckTokens = true`를 꼭 넘겨야 한다 — 빠뜨리면 일반 토큰이 "재사용된 토큰"으로 집계되며 `ServerException: Firebase App Check token is invalid`로 전부 거부된다(2026-10-17 확인).

**Apify 토큰과 Cloud Function 배포** — `functions/index.js`가 Apify API 토큰을 쥐고 있어서 앱에는 절대 내장하지 않는다. 배포 전 1회 `firebase functions:secrets:set APIFY_TOKEN`으로 토큰을 등록하고, 이후 `firebase deploy --only functions`로 배포한다(둘 다 `firebase login` 후 CLI에서 직접 실행 — 에이전트가 대신 실행할 수 없다). `firebase.json`/`.firebaserc`가 프로젝트(`wherewegoing-a5493`)를 가리키도록 이미 세팅돼 있다.

**Blaze 전환 시 흔한 함정** — 실제로 겪은 문제들, 순서대로:
1. Secret Manager는 Blaze 플랜이 있어야 활성화된다 (Spark에서는 `firebase functions:secrets:set` 자체가 막힘)
2. Firebase Console의 "Generated spend cap"을 너무 낮게(예: $1) 잡아두면, 배포 시 드는 소소한 부수 비용(컨테이너 이미지 빌드·저장)만으로도 캡을 넘겨서 결제가 자동 비활성화된다 — `The request failed because billing is disabled for this project` 에러로 나타남. $5~10 정도로 올려두면 충분하다
3. 프로젝트를 막 Blaze로 올리면 Cloud Run 인스턴스 할당량이 낮게(예: 5) 잡혀 있을 수 있다 — `The request was aborted because there was no available instance` 에러로 나타남. [할당량 콘솔](https://console.cloud.google.com/iam-admin/quotas)에서 해당 프로젝트의 Cloud Run 관련 할당량을 100 정도로 올리면 해결된다. 할당량은 상한선일 뿐 실제 비용과 무관하다
4. Blaze로 전환한 프로젝트에서 Gemini API를 호출하면 무료 티어 대신 Prepay로 전환된다 — 위 "Gemini는 결제 계정과 분리된 프로젝트에 둔다" 참고

## 개발 단계

### 1단계 — 저장 파이프라인 (완료)

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

### 3단계 — 자동 매칭 + 공개 출시 준비 (완료, 이후 5단계에서 구조 변경)

- [x] 캡션 파싱 WorkManager 골격 — 캡션 조회 → 파싱 → 카카오 로컬 검색 → 상태 결정
- [x] ResolveScreen 다중 모드
- [x] ML Kit GenAI(Gemini Nano) 온디바이스 지원 기기 분기 처리 — `FeatureStatus.AVAILABLE` 확인 후 시도, 안 되면 조용히 폴백
- [x] Firebase AI Logic SDK 연동 — `google-services.json` 없이 `FirebaseOptions`로 직접 초기화, App Check(디버그/Play Integrity) 연동 포함
- [x] 카카오 개발자 콘솔에서 앱 서명 기반 키 제한 설정

### 3.5단계 — 이미지 파싱 (보류)

캡션 파싱 적중률 실측 후 도입 여부 결정. 아직 실측 전이라 보류 중.

### 4단계 — 다듬기 (완료)

- [x] 위젯 — 홈 화면 위젯 (7단계에서 인박스 정리 대기 개수 표시에서 랜덤 장소 추천으로, 10단계에서 '오늘의 맛집'으로 교체됨)
- [x] 정리 알림 — 정리 안 된 게시물이 있으면 주기적으로(7일마다) 알림
- [x] 오래된 미확정 항목 정리 제안 — 30일 넘은 항목을 인박스에서 일괄 삭제 제안

### 5단계 — F3(계정 등록) 제거 + Apify 전환 (완료)

3단계에서는 Instagram 계정을 등록해두면(Business Discovery API) 최근 25건을 로컬 캐시에 동기화해두고, 공유받은 shortcode가 캐시에 없으면 등록된 계정을 순서대로 페이지네이션 탐색하는 구조였다. 이 구조를 완전히 걷어내고 게시물 URL 하나로 바로 캡션을 얻는 방식으로 바꿨다.

**왜 걷어냈나** — 두 가지 이유가 겹쳤다:
1. Business Discovery는 계정명을 미리 알아야 호출 가능한데, 캐시 미스가 났을 때 그 게시물이 어느 계정 것인지 모르는 상태로 등록된 계정을 전부 순서대로 페이지네이션 탐색해야 했다. 계정이 여러 개면 느리고, Meta API가 특정 페이지에서 반복적으로 500을 반환하는 경우 그 계정 전체가 사실상 막히는 문제도 있었다.
2. 더 근본적으로, 앱의 핵심 사용 패턴("인스타 보다가 마음에 드는 것만 공유")과 "계정을 통째로 팔로우해서 새 글을 전부 자동 수집"하는 F3의 설계가 안 맞았다. 사용자는 계정 단위가 아니라 게시물 단위로 관심을 표현한다.

**무엇으로 대체했나** — Firebase Cloud Function `fetchInstagramMeta`가 Apify `apify/instagram-scraper`를 호출해서, 게시물 URL 하나만으로 계정명 없이도 캡션 원문(안 잘림)과 대표 이미지를 바로 받는다. 계정을 등록하거나 추적하지 않는다 — "이 계정을 팔로우해서 새 글도 자동으로 받고 싶다"는 요구 자체가 없다고 판단했다.

- [x] `fetchInstagramMeta` Cloud Function 작성·배포 (Apify 게이트웨이, App Check 보호)
- [x] `InstagramMetaLookupRepository` 추가, `MatchPostUseCase`를 Apify 단일 경로로 단순화
- [x] Gemini 전용 Firebase 프로젝트(`fir-gemini-706d3`) 분리 — Blaze 전환 시 Prepay로 빠지는 문제 회피
- [x] WatchedAccountEntity/CachedMediaEntity, Business Discovery API/Repository, AccountsScreen, BusinessDiscoverySyncWorker, RegisterAccountUseCase, TokenStatusStore 전부 삭제
- [x] `UnresolvedReason.ACCOUNT_NOT_FOUND` 제거, Inbox의 미등록 계정 배너/칩 제거
- [x] SettingsScreen을 빈 자리로 축소 (계정 관리·동기화 주기·토큰 상태가 전부 없어짐 — 탭 구조는 유지)
- [x] 실기기 전 과정 검증 완료(2026-09-12)

### 6단계 — 홈 화면 UX 개편 (완료, 2026-09-19)

지도+리스트였던 홈 화면을 지도+커스텀 바텀시트(3단 정지) 구조로 다시 다듬고, 장소 상세를 별도 화면에서 바텀시트 상세 모드로 완전히 흡수했다. 자세한 구조는 "HomeScreen 바텀시트 구조" 참고.

- [x] 3단 바텀시트(Peek/Mid/Full)를 `AnchoredDraggableState`로 구현, 헤더 어디를 끌어도 시트가 반응하도록 nestedScroll 연동
- [x] 지도 마커를 Canvas로 직접 그리는 방식으로 전환 — 일반은 작은 원, 선택 시 핀 모양(위가 둥글고 아래가 뾰족함) + 폴더 아이콘
- [x] 폴더에 색상·아이콘 선택 추가, 삭제 진입점을 세 곳으로 확장(F6 참고)
- [x] 검색창을 지도 오버레이에서 시트 헤더로 이동, "폴더 추가"/"장소 추가" 버튼도 플로팅에서 시트 내부 칩·버튼으로 이동
- [x] 카카오 로컬 검색으로 게시물 없이 장소를 바로 추가하는 기능 추가 — 저장은 `PlaceRepository.save`(kakaoPlaceId 기준 upsert)를 그대로 재사용
- [x] 길찾기 버튼을 카카오맵 공유 링크로 연결
- [x] `AnchoredDraggableState.settle()`에 `snapAnimationSpec`을 직접 안 넘겨서 빠른 플링 시 나던 크래시 수정

### 7단계 — 위젯을 랜덤 장소 추천으로 교체 (완료, 2026-09-21 — 10단계에서 '오늘의 맛집'으로 다시 교체됨)

인박스 정리 대기 개수만 보여주던 홈 화면 위젯(`InboxWidget`)을 걷어내고, 저장된 장소(RESOLVED) 중 하나를 랜덤으로 보여주는 `PlaceRecommendationWidget`으로 교체했다. "오늘 뭐 먹지" 같은 상황에서 인박스 상태보다 장소 추천이 더 쓸모 있다고 판단.

- [x] `PlaceRecommendationWidget` 추가 — `PlaceRepository.observeAll()`에서 `status == RESOLVED`이고 이름이 있는 장소 중 랜덤 하나를 뽑아 표시
- [x] "다른 곳 보기" 버튼(`ShuffleRecommendationAction`, Glance `ActionCallback`)으로 위젯을 다시 그려 새 랜덤 장소를 뽑음. 별도 상태 저장 없이 `provideGlance`가 매번 새로 랜덤을 뽑는 방식이라 단순함
- [x] 위젯 전체 탭은 앱(`MainActivity`) 실행으로 연결 — 특정 장소로 바로 딥링크하는 기능은 없음(홈 화면에 그런 내비게이션 진입점 자체가 아직 없음)
- [x] 기존 `InboxWidget().updateAll(context)` 호출부 정리 — 장소가 RESOLVED로 확정되는 두 지점(`ResolveViewModel`의 단일/다중 확정)만 위젯을 갱신하도록 남기고, 인박스 게시물 저장·삭제 시점의 호출은 랜덤 장소 목록과 무관해 제거

### 8단계 — SettingsScreen 채우기 + 스토어 출시 준비 (완료, 2026-09-22)

5단계에서 계정 관리 기능을 걷어내며 빈 자리만 남았던 SettingsScreen을 플레이스토어 공개 출시에 필요한 최소 항목으로 채웠다.

- [x] `ThemeMode`(SYSTEM/LIGHT/DARK) 도입 — `ThemePreferenceRepository`(SharedPreferences 기반, `data/repository/ThemePreferenceRepositoryImpl`)가 값을 들고 있고, `MainActivity`가 이를 구독해 `EonjeTheme(darkTheme = ...)`에 반영. 다크/라이트 팔레트 자체는 이미 `EonjeDarkPalette`/`EonjeLightPalette`로 존재했고 이번에 사용자가 고를 수 있는 진입점만 추가한 것
- [x] SettingsScreen에 "화면" 섹션(테마 3단 선택 칩)과 "정보" 섹션(개인정보처리방침 링크, 문의하기, 버전 정보) 추가. 개인정보처리방침은 Notion 페이지로, `ACTION_VIEW`로 외부 브라우저를 연다(HomeScreen의 길찾기·원본 게시물 열기와 동일한 패턴). 문의하기는 `ACTION_SENDTO`(mailto:)로 개발자 이메일(chaheesun42@gmail.com)을 받는사람으로 채워 메일 앱을 연다
- [x] 오픈소스 라이선스 화면은 이번 범위에서 보류 — 필요해지면 추가
- [x] `data_extraction_rules.xml`/`backup_rules.xml`에 `files/thumbnails` 제외 규칙 추가 — 기존엔 둘 다 Android Studio 기본 템플릿(주석 처리된 TODO)이라 `allowBackup="true"` 상태에서 `ThumbnailStore`가 받아두는 게시물 이미지까지 Auto Backup(앱당 25MB 한도) 대상이었다. 이미지가 쌓이면 한도를 넘겨 진짜 지켜야 할 Room DB(SavedPost/Place)까지 백업이 조용히 실패할 수 있어서 이미지 캐시만 제외
- [x] Galaxy Note9(API 29) 실기기에 디버그 빌드 설치해 테마 전환(즉시 반영+재실행 후 유지 확인)·개인정보처리방침 링크(브라우저 선택 창 정상 표시)·문의하기(메일 작성 화면에 제목 정상 채워짐)·인박스 화면까지 스모크 테스트, logcat에 에러 없음 확인(2026-09-22)

**스토어 출시 관련, 코드로 처리되지 않는 항목** — Play Console에서 별도로 해야 함: 개인정보처리방침 URL을 앱 콘텐츠(App content) 섹션에 등록, 데이터 안전(Data safety) 설문 작성(캡션·이미지·위치 등 어떤 데이터를 어떻게 쓰는지), 타겟 연령층·콘텐츠 등급 설문. 이 앱은 로그인/계정 개념이 없어 "계정 삭제" 관련 요구사항은 해당 없음.

**출시 전 남은 것 — 실기기 점검 중 발견 (2026-09-22)**
- [x] 앱 아이콘 교체 — 오렌지 배경(`#E2693D`) + 오프화이트(`#FAF7F2`) 위치핀 안에 북마크 리본이 뚫린 모양으로 교체. 어댑티브 아이콘 배경은 단색 벡터(`drawable/ic_launcher_background.xml`)로 남기고, 포그라운드는 제공받은 원본 PNG에서 배경/글리프 색을 분리해 밀도별(`drawable-mdpi`~`xxxhdpi`) 래스터로 생성 — 북마크 리본 부분은 투명 처리해서 배경색이 그대로 비쳐 보이게 함(원본 디자인과 동일). 레거시 `mipmap-*/ic_launcher(.round).webp`도 같은 방식으로 만든 PNG로 교체(정사각형은 풀블리드, 원형은 원형 마스크 적용). 글리프가 어댑티브 아이콘 세이프존(66/108) 안에 들어오는 것 확인
- [x] 릴리스 서명(signing) 설정 — `keytool`로 업로드용 키스토어(`~/.android/eonje-upload-keystore.jks`, PKCS12, alias `eonje-upload`, 유효기간 30년)를 생성해 리포 밖에 두고, 경로·비밀번호는 `local.properties`(기존 카카오/Firebase 키와 같은 패턴)에 `RELEASE_KEYSTORE_PATH`/`RELEASE_KEYSTORE_PASSWORD`/`RELEASE_KEY_ALIAS`/`RELEASE_KEY_PASSWORD`로 추가. `app/build.gradle.kts`는 이 값이 있을 때만 `signingConfigs["release"]`를 만들어 `release` 빌드 타입에 연결하므로, 키스토어가 없는 환경(새 클론 등)에서도 다른 태스크는 그대로 돌아가고 `assembleRelease`/`bundleRelease`만 명확한 에러로 실패한다. `.gitignore`에 `*.jks`/`*.keystore`도 방어적으로 추가. `assembleRelease`로 만든 APK를 `apksigner verify`로 서명 확인 완료 — **키스토어 파일과 비밀번호는 이 기기에만 있으니 최초 Play Console 업로드 전에 반드시 별도로 안전하게 백업해둘 것** (분실 시 이 업로드 키로는 더 이상 업데이트를 올릴 수 없음)
- [x] R8/난독화 — `release` 빌드에 `isMinifyEnabled = true` + `isShrinkResources = true` 켬. 실제 사용 중인 라이브러리(Retrofit, OkHttp, kotlinx.serialization, ML Kit GenAI, Firebase AI)의 AAR/jar를 직접 열어 확인해보니 전부 `META-INF/proguard` 또는 `proguard.txt`로 자체 consumer 규칙을 내장하고 있어서 손댈 게 없었고, **카카오맵 SDK(`com.kakao.maps.open:android`)만 유일하게 내장 규칙이 전혀 없는 데다 JNI 네이티브 라이브러리(`libK3fAndroid.so`)까지 써서** `proguard-rules.pro`에 `-keep class com.kakao.vectormap.** { *; }` 추가. `assembleRelease` 후 `missing_rules.txt`가 안 생겼고(R8이 못 찾은 참조 없음), Galaxy Note9(API 29)에 새 릴리스 키로 서명한 APK를 직접 설치해 확인: Compose UI·Hilt·StateFlow·SharedPreferences 기반 테마 저장까지 전부 정상 동작, 카카오맵 관련 예외 스택트레이스에 `com.kakao.vectormap.MapAuthException` 등 원래 클래스명이 그대로 남아있어 keep 규칙이 제대로 먹힌 것도 확인
- [x] 카카오 디벨로퍼스 콘솔에 릴리스 키 해시(`Hax1qjOPQX3hNGF6cVKAUh5eLPA=`) 등록 완료. **주의**: Play Console에 업로드하면 Play App Signing이 이 업로드 키와 별개인 자체 배포 서명 키로 앱을 다시 서명하므로, 실제 사용자에게 배포되는 빌드가 지도를 쓰려면 Play Console의 "앱 무결성(App integrity)" 메뉴에서 그 배포 서명 키의 SHA-1도 확인해서 카카오 콘솔에 추가로 등록해야 함(최초 업로드 후에만 확인 가능)
- [x] **최종 실기기 검증(2026-09-23)** — 릴리스 키 등록 후 Galaxy Note9에 릴리스+R8 APK를 새로 설치해 전 과정 재확인: 지도 정상 렌더링(더 이상 `MapAuthException` 없음), 설정 화면(테마·링크·버전) 정상, F1 공유 저장이 인박스 배지에 즉시 반영, F2 `CaptionParsingWorker`가 실행되며 WorkManager 재시도(backoff)까지 정상 동작. 다만 이 테스트에서는 Firebase AI Logic 호출 직전 `FirebaseContextProvider: Error getting App Check token`으로 재시도만 반복됐는데 — 이건 R8 문제가 아니라 **Play Console에 아직 앱을 올리지 않아 Play Integrity가 이 릴리스 서명을 검증할 수 없어서** 생기는, 첫 업로드 전엔 피할 수 없는 정상적인 상태다(테스트에 쓴 인스타그램 URL도 실존 게시물이 아니었어서 어차피 Apify 조회는 실패했을 것). 전체 세션 동안 FATAL 크래시 0건, 앱 프로세스 끝까지 안정적으로 유지됨

### 9단계 — 첫 실행 온보딩 + 홈 빈 상태 (완료, 2026-10-22)

공유 시트에서 '언제가지'를 고르는 게 앱의 유일한 입력 경로인데, 처음 설치한 사용자는 이걸 알 방법이 없어서 첫 실행 안내를 추가했다.

- [x] `OnboardingScreen` — `HorizontalPager` 3장(인스타 게시물 열기 → 공유 버튼에서 '언제가지' 선택 → 자동 저장·못 찾으면 인박스). 건너뛰기/다음/시작하기. 공유 안내 아이콘은 Material `Send`가 아니라 인스타 공유 버튼과 같은 종이비행기 외곽선(`ui/components/PaperPlaneIcon.kt`) — 사용자가 실제 버튼과 바로 연결 짓게 하려는 것
- [x] 완료 여부는 `OnboardingRepository`(DataStore Preferences, 파일명 `onboarding`)에 저장한다. 시작하기·건너뛰기 둘 다 완료로 친다. **DataStore는 직접 선언한 의존성이 아니라 Glance(`glance-appwidget`)가 끌어오는 `datastore-preferences`를 그대로 쓴다** — 위젯을 걷어내거나 Glance를 빼면 `libs.versions.toml`에 명시적으로 추가해야 한다
- [x] 시작 화면 결정: `AppShellViewModel.onboardingCompleted`가 null(DataStore 읽는 중)이면 배경만 그리고, 값이 나오면 그 값으로 한 번만 `startDestination`(홈/온보딩)을 정한다. 알림 권한(`NotificationPermissionRequester`)은 완료 후에만, 위치 권한은 `HomeScreen`이 뜰 때 요청하므로 온보딩 위에 권한 팝업이 겹치지 않는다
- [x] 다시 보기 진입점 두 곳 — 홈 빈 상태 카드의 [사용법 다시 보기], 설정 "정보" 섹션의 "사용법". 라우트는 `onboarding?entry={entry}`이고, 끝나면 뒤에 화면이 있으면 pop, 첫 실행이면 홈으로 교체한다
- [x] 홈 빈 상태 — 필터·검색과 무관하게 저장된 장소가 0개(`HomeUiState.hasNoSavedPlaces`)일 때 "인스타에서 맛집 게시물을 공유해보세요" 카드. Mid(40%)에선 카드가 잘려서(Note9 기준 보이는 높이 약 135dp, 카드 약 210dp) 이때만 시트를 Full로 올리고 카드 영역은 스크롤 가능하게 했다. 로딩 중 깜빡임을 막으려고 기본값은 false
- [x] Analytics(`Events`) — `onboarding_viewed`/`onboarding_completed`/`onboarding_skipped`에 `source`(`first_run`·`home_empty`·`settings`)와 `step`(1부터; completed는 3, skipped는 건너뛴 장). `first_share_after_onboarding`은 온보딩 완료 후 첫 공유 저장 시 기기당 한 번 — `ShareReceiverActivity`에서 기존 `share_received` 바로 뒤에 찍고, DataStore 오류는 삼켜서 F1 저장에는 영향이 없다
- [x] Galaxy Note9(API 29) 디버그 빌드에서 첫 실행·재실행 시 미표시·다시 보기(홈/설정)·건너뛰기·시작하기·시스템 뒤로가기와 이벤트 로그(Analytics DebugView용 `FA-SVC` logcat) 확인(2026-10-22). `first_share_after_onboarding`은 실제 공유(Apify 호출 과금)가 필요해 아직 미확인

**참고** — 이 업데이트 이전부터 설치돼 있던 사용자도 온보딩을 한 번 보게 된다(완료 플래그가 없으므로). 그리고 Play 스토어에서 받은 빌드는 Play 배포 키로 서명돼 있어서 로컬 빌드(디버그·릴리스 모두)로 덮어쓸 수 없다 — 실기기 테스트하려면 앱을 지우고(데이터 삭제됨) 설치해야 한다.

### 10단계 — 스플래시 + '오늘의 맛집' 위젯 + 다듬기 (완료, 2026-10-31)

- [x] **스플래시** — `core-splashscreen`. `MainActivity` 테마를 `Theme.Eonje.Starting`(Theme.SplashScreen)으로 두고 `installSplashScreen()`을 `super.onCreate` 전에 호출. 배경은 런처 아이콘 배경색(`#E2693D`), 아이콘은 어댑티브 포그라운드 재사용, 다크는 `values-night`에서 아이콘 배경 원으로 처리. 유지 조건은 `AppShellViewModel.isReady`(시작 화면 결정 + 장소 목록 첫 Room 읽기)이고 최대 1초 상한 — 인위적 지연 없음. 공유 수신(`ShareReceiverActivity`)엔 스플래시가 없다. 카카오맵 SDK 초기화는 설치 직후 첫 실행만 약 340ms, 이후 15~40ms라 스플래시 길이에 거의 영향 없음
- [x] **'오늘의 맛집' 위젯** (`widget/TodayPlaceWidget`) — 7단계 랜덤 위젯을 교체. Receiver 클래스명(`PlaceRecommendationWidgetReceiver`)과 `xml/place_recommendation_widget_info.xml`은 이미 홈에 놓인 위젯 호환을 위해 이름 유지
  - RESOLVED 장소 중 하루 하나: 날짜·오늘 장소·어제 장소를 SharedPreferences(`today_place_widget`, `TodayPickStore`)에 저장해 같은 날엔 같은 장소, 다음 날엔 어제와 다른 장소. 선택 로직은 순수 함수 `chooseTodayPick`(단위 테스트 있음)
  - 표시: 이름·동네·폴더(아이콘+이름, 미분류면 숨김). 동네는 스키마 변경 없이 저장된 주소에서 동/읍/면 → 구/군 → 시 순으로 뽑는다(`neighborhoodOf`). 주소가 대부분 도로명이라 실제론 구 단위가 많다 — 동 단위가 필요하면 지번 주소 컬럼이 필요(스키마 변경)
  - 갱신: 장소 저장·삭제·폴더 배정·폴더 삭제는 `PlaceRepositoryImpl`/`FolderRepositoryImpl`이 `PlacesChangedNotifier`(구현 `TodayPlaceWidgetUpdater`)를 불러 한곳에서 처리 — 화면별로 `updateAll`을 부르지 않는다. 날짜 변경은 `TodayPlaceWidgetWorker`(하루 1회, 자정 5분 뒤, 위젯 추가 시 예약·전부 제거 시 취소). `updatePeriodMillis=0`, 네트워크 호출 없음
  - 탭: `MainActivity`가 extra(`EXTRA_FROM_WIDGET`/`EXTRA_PLACE_ID`)를 받아 홈 시트의 해당 장소 상세로 연다(폴더 필터·검색 해제). 그 사이 삭제됐거나 미확정이면 홈만 연다. 온보딩 전이면 아무것도 안 함
  - 모양: `glance-material3`로 앱 M3 색(`eonjeColorScheme`) 사용, 시스템 다크 모드만 따른다(앱 내 테마 설정은 반영 안 됨). 배경은 크림→살구빛 그라데이션 드로어블(`widget_background`, 모서리를 드로어블에도 넣어 API 30 이하도 둥글게). 기본 3x1 가로형(핀 배지 + 라벨·이름·동네 · 폴더), 2칸 높이 이상이면 라벨 칩이 위에 붙는 세로형
  - Analytics: `widget_added`/`widget_removed`/`widget_clicked`(`target`=`place`|`empty`). 가게 이름·id·좌표는 넣지 않는다
  - 일부러 뺀 것: 폴더 선택 설정(configure 화면), 주변 맛집, 썸네일 이미지, "다른 곳 보기" 셔플(하루 고정과 충돌)
- [x] **홈 바텀시트가 Peek으로 시작** — 첫 측정 전 임시 앵커가 전부 0f라 `updateAnchors`의 기본 `newTarget`(현재 오프셋에 가장 가까운 정지점)이 Full을 골라 올라간 채 시작했다. `newTarget = sheetState.targetValue`를 명시하고, 앵커가 잡히기 전엔 시트를 화면 밖에 둔다
- [x] **인박스 '순서대로 정리하기' 버튼 제거** — 첫 카드를 여는 것과 같은 동작이었고 다음 항목으로 이어지는 흐름도 없었다
- [x] API 36 에뮬레이터(릴리스 빌드)에서 스플래시 라이트/다크, 위젯 추가·빈 상태·장소 탭·삭제 후 갱신·폴더 변경·다크·크기 조절·이벤트 확인, Galaxy Note9(디버그)에서 인박스 확인. versionCode 5로 `bundleRelease`
- **미확인**: API 30 이하 스플래시·위젯 모양, 실제 자정 넘김 시 워커 실행, 기존 위젯을 둔 채 앱 업데이트하는 경로

### 11단계 — Firestore를 원본으로 전환 (완료, 2026-11-06, 브랜치 `feature/firestore`)

이후 iOS 앱과 친구 공유를 붙이려면 데이터가 서버에 있어야 해서, Room 대신 Firestore를 원본으로 바꿨다. 세 덩어리로 나눠 진행했다.

- [x] **도메인 모델 분리**: Room Entity가 화면·유스케이스로 새던 것을 `domain/model`의 순수 모델로 분리, Repository 인터페이스를 도메인 모델 기준으로 정리. Room 구현은 `RoomXxxRepository`로 이름을 바꿔 남기고 Hilt에 `@RoomStore`로 등록
- [x] **Firestore 구현**: 저장소 4개(`data/firestore`), 익명 로그인(`AuthSession`), 쓰기는 await하지 않음, 리스너는 화면 범위에만. 로그인 전 공유는 `SaveSharedPostWorker`로 유실 없이 보관(F1 참고). 에뮬레이터 연결(디버그 전용)과 계측 테스트
- [x] **Room → Firestore 1회 이전** (`data/migration`, `RoomMigrationWorker`):
  - 앱 시작 때마다 예약(네트워크 조건, 지수 백오프, REPLACE). 이 uid로 이미 끝났으면 DataStore 한 번 읽고 바로 끝난다. Room이 비어 있으면(신규 설치) 이전 없이 바로 완료 표시
  - 순서 폴더 → 장소 → 게시물. 배치(499개)마다 **서버에 이미 있는 id 확인(덮어쓰지 않음) → 없는 문서만 쓰기 → 서버 확정까지 대기 → 체크포인트 저장**. 끊겨도 확정된 곳부터 이어가고, 이미 옮겨진 문서는 사용자가 Firestore 쪽에서 고쳤어도 Room의 옛 값으로 덮지 않는다
  - 진행 기록은 **uid와 함께** DataStore(`room_migration`)에 저장한다. 자동 백업이 이 파일을 새 기기로 복원해도 거기서 로그인된 uid가 다르면 처음부터 다시 이전한다(같은 uid면 이미 끝난 것). 백업에서 제외하지 않는다 — uid와 짝으로 판단하므로 안전하고, 제외하면 같은 uid에서 불필요한 읽기가 생긴다
  - 필드 변환은 Firestore 저장소와 같은 매퍼를 쓴다. memo는 옮기지 않고, 썸네일 파일은 옮기지 않지만 문서 ID가 Room의 UUID 그대로라서 `{postId}.jpg`가 그대로 연결된다(원격 URL만 저장돼 있던 썸네일은 만료되는 값이라 옮기지 않는다)
  - **Room 데이터는 지우지 않는다**(정리는 다음 버전). 서버에 쓰기가 확정되지 않은 상태에서는 어떤 경우에도 지우지 않는다
  - **이전이 끝나기 전에 막아둔 위험 3가지**: ① 썸네일 고아 정리(`ThumbnailMaintenance`)는 이전이 끝난 뒤에만 한다 — 일부만 옮겨진 상태에서 ID 목록에 없다고 지우면 다시 받을 수 없는 썸네일을 잃는다 ② 캡션 작업은 게시물이 없고 이전이 안 끝났으면 횟수 없이 대기한다(`WaitForMigration`) ③ 공유 중복 확인은 이전이 끝나기 전엔 Room도 shortcode로 본다
  - **홈 안내**: 이전 대상이 있는 사용자(Room에 데이터가 있고 현재 uid로 이전이 안 끝남)에게만 적용된다 — 신규 설치(Room이 비어 있음)엔 영향이 없다. 이전이 끝나기 전엔 "저장된 장소가 없어요"와 빈 상태 카드를 보여주지 않고, 상태가 1.5초 이상 이어질 때만 얇은 배너를 띄운다(이전 중 "기존 데이터를 옮기는 중이에요", 시작 못 함(오프라인·로그인 대기·재시도 대기) "인터넷에 연결되면 기존 데이터를 옮겨요. 데이터는 안전해요"). 상태는 DataStore(`room_migration`)의 uid 기반 완료 값 + Room 데이터 유무 + Worker 상태로만 계산하고 새 서버 호출은 없다(`LegacyMigrationStatus.observePhase`, 판정 함수 `legacyMigrationPhase`)
  - **문제 문서 격리**: 규칙이 거부하는 문서(캡션 상한 초과 등)나 Room에서 읽을 수 없는 행(앱이 모르는 status 문자열을 `Converters`가 `valueOf`로 읽다 예외를 던진다) 하나 때문에 이전이 영영 끝나지 않으면 안 된다. 배치 커밋이 **영구 실패**(`PERMISSION_DENIED`, `INVALID_ARGUMENT`)로 거부되면 배치를 반으로 나눠 다시 커밋하고(정상 문서는 한 번만 쓰인다. 499개를 하나씩 순차로 쓰면 왕복이 499번이라 이분 탐색으로 찾는다), 문서 하나가 남았는데도 거부되면 그 문서만 건너뛰고 `종류:ID:사유`를 DataStore(`room_migration`의 `skipped_docs`)와 로그에 남긴다. **ID와 사유 코드뿐이고 캡션 같은 내용은 남기지 않는다.** 네트워크 오류 같은 **일시적 실패는 격리하지 않고 그대로 재시도**한다. 건너뛴 문서는 Room에 그대로 남아 있고, 모든 문서를 처리하면(건너뛴 것 포함) 완료로 표시해 홈 배너가 사라진다. `room_migration_completed`에 `skipped_count`(숫자만)가 들어간다
  - **계정 전체 거부는 격리하지 않는다**: 규칙 미배포(프로덕션 모드), 로그인 풀림, App Check 시행처럼 정상 문서도 전부 거부되는 상황이면 모든 문서를 건너뜀으로 처리하고 이전이 완료돼 버린다. 서버엔 한 건도 없는데 완료로 표시되고, 다음 버전의 Room 정리 때 데이터를 잃을 수 있다. 그래서 영구 실패가 처음 나오면 작은 정상 문서(`posts/migration-probe`) 하나를 쓰고 지워서(쓰기 1 + 삭제 1) 거부가 문서 때문인지 계정 전체 때문인지 가르고, 프로브마저 거부되면 건너뛰지 않고 실패로 던져 재시도한다
  - 건너뛴 게시물의 썸네일 파일은 서버에 문서가 없어도 고아가 아니다(`LegacyMigrationStatus.skippedPostIds`). Room에 남아 있고 썸네일은 복구할 수 없다. 건너뛴 폴더에 속했던 장소는 미분류로 옮겨진다
  - 건너뛴 문서를 나중에 다시 옮기는 장치는 아직 없다(완료 뒤엔 다시 안 돈다). 규칙·상한을 고친 뒤 되살리려면 앱 버전별로 한 번 `skipped_docs`만 재시도하는 방식을 다음 버전에서 검토한다
  - Analytics: `room_migration_started`(이어하기에선 안 찍힘)/`room_migration_completed`(폴더·장소·게시물 개수, 처음 시작부터의 소요 시간)/`room_migration_failed`(`error_type`: network·permission·unknown). 내용은 넣지 않는다
  - **규칙 상한과 앱이 만들 수 있는 값의 어긋남**: 폴더 이름 입력(`AddFolderContent`, 폴더 피커)에 길이 제한이 없는데 규칙은 100자까지만 받는다. 101자 이상이면 폴더 생성이 서버에서 거부되고 앱은 로그만 남긴다(`logFailure`). 그 밖의 값(캡션 20,000자 vs 인스타 상한 2,200자, URL 500자 vs 정규화된 약 60자, 장소 이름·주소·카테고리는 카카오 값, 후보 500개 vs 항목당 5개)은 여유가 크다
  - 알려진 한계: 이전 도중 사용자가 같은 이름의 폴더를 새로 만들면 이름이 겹칠 수 있다. 이전 도중 지운 장소가 아직 안 옮겨진 게시물의 `placeIds`에 남을 수 있다(읽는 쪽이 무시하므로 화면엔 안 보인다)
- **썸네일 고아 정리의 서버 확인**: `ThumbnailMaintenance`는 캐시(`cachedPostIds`) 기준으로 고아로 의심되는 파일만 `SavedPostRepository.findIdsAbsentOnServer`(`whereIn(documentId)` 30개씩, `Source.SERVER`)로 확인하고 **서버에도 없다고 확인된 것만** 지운다. 오프라인·실패·상한(한 번에 60개) 초과분은 지우지 않고 다음 실행으로 미룬다. 읽기는 의심 파일이 없으면 0, 있으면 질의당 최소 1건(서버에 있는 문서만큼 더). 확인된 문서는 캐시에 들어가 다음부터 의심에서 빠진다. 이전이 끝나기 전엔 고아 판단 자체를 안 한다(위 규칙 유지). 아래 "알려진 위험(2단계부터)"은 이 확인으로 완화됐다
- **보안 규칙** (`firestore.rules`, 12단계): `users/{uid}/{folders,places,posts}`만 `request.auth.uid == uid`일 때 허용하고 나머지(users/{uid} 문서 자체·하위 컬렉션·다른 컬렉션)는 거부한다. 쓴 뒤의 문서 전체(`request.resource.data`)를 검사하므로 필드 하나만 고치는 update·`arrayUnion`/`arrayRemove`·WriteBatch도 결과가 올바르면 통과한다. 허용 필드는 `FirestoreMappers`의 `Fields`·`toFields`·`toMap` 출력에서 뽑았다(`hasOnly`). 앱이 null로 저장하는 선택 필드(shortcode, caption, unresolvedReason, resolvedAt, folderId, name, address, latitude, longitude, kakaoPlaceId, category)는 "없거나 null이거나 올바른 값"이다. 한계: 규칙 언어에 반복문이 없어 `candidates` 배열 안 항목의 필드는 검증하지 못한다(크기 500 + 문서 1MiB 한도). 테스트는 `FirestoreRulesTest`(정상 문서는 매퍼 출력 그대로, 거부는 한 군데만 망가뜨림, enum 전체를 써서 규칙과 어긋나면 깨짐). 에뮬레이터는 `firebase.json`의 `firestore.rules`를 로드하고 파일을 고치면 자동으로 다시 읽는다 — **계측 테스트는 항상 규칙이 적용된 상태**였다(2단계부터)
- **Firestore 콘솔·CLI에서 직접 할 일**(코드로 안 됨, 순서대로): ① 서울 리전(`asia-northeast3`) 프로덕션 모드 DB 생성(리전은 못 바꾼다) → ② **앱 배포 전에** 규칙·인덱스 배포: `firebase deploy --only firestore`(CLI 직접, `firebase login` 후; 프로덕션 모드는 규칙을 배포하기 전엔 모든 접근이 막힌다) → ③ Authentication 익명 로그인 켜기 → ④ 예산 알림 → ⑤ App Check는 모니터링 모드로 먼저 켜서 Firestore·Auth 요청의 "확인됨" 비율을 보고, 거의 100%일 때만 시행(enforce)으로 전환. 릴리스(Play Integrity)는 Play에 올린 서명 빌드에서만 토큰이 나오므로 그 전에 시행하면 사용자 전부가 막힌다. 디버그 빌드는 기기별 디버그 토큰 등록 필요
- **알려진 위험(2단계부터)**: `cachedPostIds()`(썸네일 고아 정리)는 Firestore **캐시**에서 ID를 읽는다. 캐시가 20MB 상한으로 정리(GC)돼 일부 게시물이 캐시에서 빠지면 그 썸네일이 고아로 오판돼 지워질 수 있다. 문서가 작아 거의 안 일어나지만, 캐시 상한을 줄이거나 문서를 크게 만들면 다시 봐야 한다

## 코딩 컨벤션

- 각 화면은 단일 UiState data class를 StateFlow로 노출
- 이벤트성 처리(토스트, 네비게이션)는 Channel 또는 SharedFlow로 분리
- Repository는 domain 레이어에 인터페이스, data 레이어에 구현체
- 모든 네트워크/DB 작업은 코루틴. UI 레이어에서 직접 호출 금지
- **Firestore**: named 앱 `wherewegoing`에만 붙인다(위 "비용·서버 구조"). 쓰기 Task는 await하지 않는다(오프라인에서 멈춘다) — 화면은 리스너로 갱신된다. 백그라운드 Worker·위젯은 `Source.CACHE`로만 읽고, 마이그레이션처럼 서버 확정이 필요한 Worker만 await한다. 문서 필드는 `FirestoreMappers`에서만 정한다. 에러·로그엔 작업 이름과 오류 종류만 남기고 문서 경로·내용은 남기지 않는다
- **빌드 검증은 종료 코드로 한다** — `grep`으로 걸러 보면 컴파일 에러를 놓친다(실제로 놓쳐서 깨진 상태로 커밋한 적이 있다). `assembleDebug`, `assembleRelease`, `testDebugUnitTest`, `compileDebugAndroidTestKotlin`, 에뮬레이터를 띄운 `connectedDebugAndroidTest`를 단계마다 돌린다
- **Room 스키마(레거시, v9)는 이번 버전에서 바꾸지 않는다. 바꿔야 한다면 반드시 마이그레이션 추가** — 1.0 출시 기준 스키마는 v9(`app/schemas/.../9.json`, git 관리). 파괴적 마이그레이션(`fallbackToDestructiveMigration`)은 제거했으므로, `version`을 올리고 `Migration`/`AutoMigration`을 빠뜨리면 앱 시작 시 크래시로 드러난다(데이터를 조용히 지우지 않게 하려는 의도)
- **릴리스 로그에 URL·캡션·가게 이름을 남기지 않는다** — OkHttp `HttpLoggingInterceptor`는 `BuildConfig.DEBUG`일 때만 붙인다(BASIC도 요청 URL 전체, 즉 카카오 검색어를 남김)
- **광고 ID 권한은 매니페스트에서 제거해둔 상태** — Firebase Analytics가 `AD_ID`/`ACCESS_ADSERVICES_*` 권한을 자동 추가하므로 `tools:node="remove"`로 빼고 `google_analytics_adid_collection_enabled=false`로 수집도 끈다. Play Console 광고 ID 신고는 "사용 안 함"과 일치해야 한다
