# 언제가지 (WhenAreWeGoing)

인스타그램에서 본 맛집을 저장하고 나중에 꺼내 쓰는 안드로이드 앱. 플레이스토어 공개 출시 예정.

## 앱 개요

- **핵심 흐름**: 인스타에서 공유 버튼 → URL만 즉시 저장(화면 없음) → 나중에 앱에서 가게 확정 → 외식할 때 지도에서 꺼내 봄
- **설계 원칙 1**: 저장은 절대 실패하지 않는다. 가게 이름을 못 찾아도, 네트워크가 끊겨도 URL은 저장된다
- **설계 원칙 2**: 자동 인식(캡션 파싱)은 최적화이지 필수 기능이 아니다. 실패해도 앱은 정상 동작한다
- **설계 원칙 3**: 자체 서버는 두지 않고, 사용자가 늘어도 개발자 비용이 비례해서 늘지 않는 구조로 간다. 캡션 파싱은 Firebase AI Logic(Gemini 무료 티어)과 온디바이스 Gemini Nano를 쓰고, 지도 검색은 클라이언트에서 직접 호출한다. 유일한 예외는 Firebase Cloud Functions 함수 하나(게시물 URL로 계정명·캡션을 Apify로 대신 조회) — 직접 운영하는 서버는 아니지만 커스텀 백엔드 코드가 있다는 점은 예외이며, Apify 토큰을 앱에 내장하지 않기 위한 최소한의 게이트웨이다
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
| 로컬 DB | Room |
| 네트워크 | Retrofit + OkHttp + kotlinx.serialization (카카오 로컬) + Firebase Functions(계정명·캡션 조회) |
| 캡션 파싱 (AI) | 지원 기기는 ML Kit GenAI(Gemini Nano) 온디바이스 우선 시도, 아니면 Firebase AI Logic(Gemini Flash 무료 티어, App Check 필수)으로 폴백 |
| 비동기 | Coroutines + Flow |
| 백그라운드 | WorkManager |
| 지도 | 카카오맵 SDK (카카오 로컬 API와 좌표계 통일) |
| 네비게이션 | Navigation Compose |
| 이미지 | Coil |
| 백엔드 | 원칙적으로 없음. 모든 API는 클라이언트에서 직접 호출하되, 게시물 계정명·캡션 조회만 Firebase Cloud Functions 함수 하나(Apify 토큰 보관용 게이트웨이)를 거친다 (아래 "비용·서버 구조" 참고) |

*캡션 파싱은 실기기(Galaxy Note9, API 29)에서 공유 → Apify 캡션 조회 → Gemini 파싱 → 카카오 검색 → 자동 확정까지 전 과정 검증 완료(2026-09-12). Firebase AI Logic은 App Check(디버그: Debug Provider, 릴리스: Play Integrity)가 없으면 요청 자체를 거부하므로 `EonjeApplication`에서 필수로 초기화한다. Gemini는 Blaze(결제 계정 연결) 프로젝트에서 호출하면 무료 티어 대신 Prepay(선불) 결제로 전환돼버려서, Gemini 전용 프로젝트(`fir-gemini-706d3`, Spark 유지)를 Apify 게이트웨이 프로젝트(`wherewegoing-a5493`, Blaze)와 분리해뒀다 — 자세한 내용은 "비용·서버 구조" 참고.*

## 패키지 구조

```
com.github.chsssssss.eonje/
├─ di/                 Hilt 모듈
├─ data/
│  ├─ local/           Room (Entity, Dao, Database)
│  ├─ remote/          Retrofit(Kakao) + Firebase Functions/AI Logic 앱 이름 상수
│  ├─ repository/      Repository 구현체
│  └─ worker/          WorkManager
├─ domain/
│  ├─ model/           도메인 모델
│  ├─ repository/      Repository 인터페이스
│  └─ usecase/
├─ ui/
│  ├─ home/            Screen + ViewModel + State — 지도, 커스텀 바텀시트(목록/상세 모드), 폴더 관리
│  ├─ inbox/
│  ├─ resolve/         정리 화면 (단일/다중 모드)
│  ├─ components/      화면 간 공유 컴포저블 (FolderPicker, PlaceholderImage, BottomNavBar 등)
│  ├─ settings/        테마(라이트/다크/시스템)·개인정보처리방침·버전 정보 (8단계 참고)
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
| SettingsScreen | 화면 테마(시스템 설정/라이트/다크), 개인정보처리방침 링크, 버전 정보 (5단계에서 계정 관리·동기화 주기·토큰 상태를 걷어낸 뒤, 8단계에서 다시 채움) |

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
- URL에서 shortcode 추출: `/p/{code}/`, `/reel/{code}/`, `/tv/{code}/`
- 쿼리 파라미터(`?igsh=...`) 제거
- Room에 SavedPost 생성 (동기 처리, 밀리초 단위)
- 이후 WorkManager로 위임

### F2. 장소 추출 및 매칭 (WorkManager)

1. **계정명·캡션 조회** — Firebase Cloud Function `fetchInstagramMeta` → Apify `apify/instagram-scraper` 액터로 게시물 URL만으로 캡션 원문(안 잘림) + 대표 이미지 URL을 받는다. 계정을 등록하거나 추적하지 않는다 — 이 게시물 하나를 푸는 데 그걸로 충분하다
1.5. 대표 이미지는 `ThumbnailStore`로 기기에 다운로드해서 `thumbnailUrl`에 file:// 경로로 저장한다 — 인스타 CDN의 media_url은 시간이 지나면 만료돼서 원본 URL을 그대로 저장해두면 나중에 인박스·상세화면에서 사진이 안 보이게 된다. 다운로드 실패 시엔 원본 URL을 그대로 폴백(최선 노력, F2 실패로 취급하지 않음). 게시물 삭제 시 로컬 파일도 함께 정리된다(`SavedPostRepositoryImpl.deleteByIds`)
2. 실패(비공개 게시물, Apify 오류, Cloud Run 일시 장애 등) → `MatchPostResult.Retry`로 WorkManager 지수 백오프 재시도. 재시도 한도(5회) 넘기면 `UNRESOLVED(NETWORK_ERROR)`
3. 캡션이 비어있으면 `UNRESOLVED(PLACE_NOT_FOUND)`
4. 캡션 파싱 시도 순서:
   a. 기기가 ML Kit GenAI(Gemini Nano) 지원 시 온디바이스로 우선 시도 — 비용 0, 네트워크 불필요
   b. 미지원 기기이거나 온디바이스 실패 시 Firebase AI Logic으로 Gemini Flash 호출 (무료 티어, App Check 필요, Gemini 전용 프로젝트 사용)
   c. 두 경로 모두 `@Generable` 구조화 출력으로 `[{상호명, 지역, 메뉴}]` 배열 응답 받기
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

## 비용·서버 구조

플레이스토어 공개 출시가 목표이지만, 사용자가 늘어도 개발자가 사용량 비례 비용을 지지 않는 구조로 설계한다. **자체 서버는 두지 않는다** (Apify 게이트웨이용 Cloud Functions 함수 하나는 예외 — 아래 참고).

| 기능 | 방식 | 비용 |
|---|---|---|
| 계정명·캡션 조회 (F2) | Firebase Cloud Function `fetchInstagramMeta`(App Check 필수) → Apify `apify/instagram-scraper` 액터. 실패하면 WorkManager가 재시도하고, 그래도 안 되면 UNRESOLVED로 조용히 종료 | Apify 무료 티어 월 $5 크레딧 ≈ 1,850건, 그 이상은 건당 $0.0027 (2026-09 기준). 캐시 없이 공유할 때마다 호출됨 |
| 캡션 파싱 (F2) | ML Kit GenAI(Gemini Nano) 온디바이스 우선, 미지원 기기는 Firebase AI Logic으로 Gemini Flash 무료 티어 호출 (App Check 필수, Gemini 전용 프로젝트) | 0원. 온디바이스는 추론 자체가 기기에서 일어나 무제한. 무료 티어는 분당 15회·일일 1500회(2026년 9월 기준) — 앱 전체 합산 |
| 카카오 로컬 검색 | 클라이언트에서 REST API 직접 호출, 카카오 개발자 콘솔에서 앱 서명(SHA) 기반 키 제한 설정 | 카카오 무료 쿼터 내 |
| Cloud Functions(Apify 게이트웨이) 인프라 | Cloud Run 2세대 위에서 실행. `wherewegoing-a5493`은 Secret Manager(Apify 토큰 보관)를 쓰려고 Blaze로 전환함 | Blaze 무료 티어: 월 200만 건 호출, 40만 GB-초, 5GB 아웃바운드까지 무료. 컨테이너 이미지는 배포 시 1일 뒤 자동 삭제 정책으로 스토리지 비용 억제 |

**API 키를 앱에 직접 내장하지 않는 이유** — APK는 디컴파일 가능해서, 내장된 키는 노출되면 누구나 그 키로 무제한 요청을 보낼 수 있고 비용은 키 소유자에게 청구된다. Firebase AI Logic은 이 문제를 게이트웨이 구조로 해결해서, 서버를 직접 짜지 않아도 클라이언트에서 안전하게 Gemini를 호출할 수 있다. 카카오는 앱 서명 기반 키 제한으로, Apify 토큰은 Firebase Cloud Function(Secret Manager) 뒤에 숨겨서 같은 문제를 해결한다.

**Gemini는 결제 계정과 분리된 프로젝트에 둔다** — Firebase AI Logic(Gemini Developer API)의 진짜 무료 티어는 결제 계정이 전혀 연결되지 않은 프로젝트에서만 유지된다. 프로젝트가 한 번 "Prepay(선불)" 상태로 전환되면 무료 티어로 자동 복귀가 안 되고 선불 잔액(최소 $10)이 있어야만 호출된다. 그래서 Apify 게이트웨이(Secret Manager 때문에 Blaze 필수인 `wherewegoing-a5493`)와 Gemini(`fir-gemini-706d3`, Spark 유지)를 완전히 다른 프로젝트로 분리했다. `EonjeApplication`에서 Gemini 프로젝트를 기본(default) FirebaseApp으로, `wherewegoing-a5493`을 이름 있는(named) FirebaseApp(`WHEREWEGOING_FIREBASE_APP_NAME`)으로 각각 초기화하고 App Check도 프로젝트별로 따로 설치한다 — Firebase AI Logic SDK가 이름과 무관하게 "기본 FirebaseApp"만 찾기 때문에 이 순서가 중요하다(`data/remote/FirebaseApps.kt` 참고).

**Claude API는 쓰지 않는다** — 무료 티어가 없어 사용량에 비례해 계속 비용이 발생하므로 이 프로젝트의 캡션 파싱에는 적합하지 않다. GPT API도 동일한 이유로 제외.

**App Check가 필수인 이유** — Firebase AI Logic은 남용 방지를 위해 App Check로 검증된 요청만 받는다(미설치 시 `ServerException: ... you must enforce Firebase App Check`로 전부 거부됨). 프로젝트 콘솔에서 해당 API(Firebase AI Logic/Generative Language API)의 App Check 강제 적용을 별도로 켜둬야 한다 — 클라이언트 SDK 설치만으로는 부족하다. `EonjeApplication`에서 디버그 빌드는 Debug Provider, 릴리스 빌드는 Play Integrity Provider를 프로젝트별로 설치한다. Debug Provider의 디바이스별 토큰은 각 프로젝트의 Firebase 콘솔(App Check > 앱 > 디버그 토큰 관리, 또는 `firebase appcheck:debugtokens:create`)에 등록해야 그 기기에서 호출이 통과한다 — 새 개발 기기를 쓸 때마다, 그리고 프로젝트를 하나 더 쓰게 될 때마다 필요한 일회성 작업. `fetchInstagramMeta` Cloud Function도 `enforceAppCheck: true`로 동일하게 보호한다.

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

- [x] 위젯 — 홈 화면 위젯 (7단계에서 인박스 정리 대기 개수 표시에서 랜덤 장소 추천으로 교체됨)
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

### 7단계 — 위젯을 랜덤 장소 추천으로 교체 (완료, 2026-09-21)

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
- [ ] **앱 아이콘이 아직 Android Studio 기본 템플릿(녹색 그리드 로봇)이다** — `ic_launcher_foreground.xml`/`ic_launcher_background.xml`이 실제 브랜드 아이콘으로 안 바뀌어 있음. 플레이스토어에 이대로 올리면 안 되는 가장 시급한 항목
- [x] 릴리스 서명(signing) 설정 — `keytool`로 업로드용 키스토어(`~/.android/eonje-upload-keystore.jks`, PKCS12, alias `eonje-upload`, 유효기간 30년)를 생성해 리포 밖에 두고, 경로·비밀번호는 `local.properties`(기존 카카오/Firebase 키와 같은 패턴)에 `RELEASE_KEYSTORE_PATH`/`RELEASE_KEYSTORE_PASSWORD`/`RELEASE_KEY_ALIAS`/`RELEASE_KEY_PASSWORD`로 추가. `app/build.gradle.kts`는 이 값이 있을 때만 `signingConfigs["release"]`를 만들어 `release` 빌드 타입에 연결하므로, 키스토어가 없는 환경(새 클론 등)에서도 다른 태스크는 그대로 돌아가고 `assembleRelease`/`bundleRelease`만 명확한 에러로 실패한다. `.gitignore`에 `*.jks`/`*.keystore`도 방어적으로 추가. `assembleRelease`로 만든 APK를 `apksigner verify`로 서명 확인 완료 — **키스토어 파일과 비밀번호는 이 기기에만 있으니 최초 Play Console 업로드 전에 반드시 별도로 안전하게 백업해둘 것** (분실 시 이 업로드 키로는 더 이상 업데이트를 올릴 수 없음)
- [x] R8/난독화 — `release` 빌드에 `isMinifyEnabled = true` + `isShrinkResources = true` 켬. 실제 사용 중인 라이브러리(Retrofit, OkHttp, kotlinx.serialization, ML Kit GenAI, Firebase AI)의 AAR/jar를 직접 열어 확인해보니 전부 `META-INF/proguard` 또는 `proguard.txt`로 자체 consumer 규칙을 내장하고 있어서 손댈 게 없었고, **카카오맵 SDK(`com.kakao.maps.open:android`)만 유일하게 내장 규칙이 전혀 없는 데다 JNI 네이티브 라이브러리(`libK3fAndroid.so`)까지 써서** `proguard-rules.pro`에 `-keep class com.kakao.vectormap.** { *; }` 추가. `assembleRelease` 후 `missing_rules.txt`가 안 생겼고(R8이 못 찾은 참조 없음), Galaxy Note9(API 29)에 새 릴리스 키로 서명한 APK를 직접 설치해 확인: Compose UI·Hilt·StateFlow·SharedPreferences 기반 테마 저장까지 전부 정상 동작, 카카오맵 관련 예외 스택트레이스에 `com.kakao.vectormap.MapAuthException` 등 원래 클래스명이 그대로 남아있어 keep 규칙이 제대로 먹힌 것도 확인
- [x] 카카오 디벨로퍼스 콘솔에 릴리스 키 해시(`Hax1qjOPQX3hNGF6cVKAUh5eLPA=`) 등록 완료. **주의**: Play Console에 업로드하면 Play App Signing이 이 업로드 키와 별개인 자체 배포 서명 키로 앱을 다시 서명하므로, 실제 사용자에게 배포되는 빌드가 지도를 쓰려면 Play Console의 "앱 무결성(App integrity)" 메뉴에서 그 배포 서명 키의 SHA-1도 확인해서 카카오 콘솔에 추가로 등록해야 함(최초 업로드 후에만 확인 가능)
- [x] **최종 실기기 검증(2026-09-23)** — 릴리스 키 등록 후 Galaxy Note9에 릴리스+R8 APK를 새로 설치해 전 과정 재확인: 지도 정상 렌더링(더 이상 `MapAuthException` 없음), 설정 화면(테마·링크·버전) 정상, F1 공유 저장이 인박스 배지에 즉시 반영, F2 `CaptionParsingWorker`가 실행되며 WorkManager 재시도(backoff)까지 정상 동작. 다만 이 테스트에서는 Firebase AI Logic 호출 직전 `FirebaseContextProvider: Error getting App Check token`으로 재시도만 반복됐는데 — 이건 R8 문제가 아니라 **Play Console에 아직 앱을 올리지 않아 Play Integrity가 이 릴리스 서명을 검증할 수 없어서** 생기는, 첫 업로드 전엔 피할 수 없는 정상적인 상태다(테스트에 쓴 인스타그램 URL도 실존 게시물이 아니었어서 어차피 Apify 조회는 실패했을 것). 전체 세션 동안 FATAL 크래시 0건, 앱 프로세스 끝까지 안정적으로 유지됨

## 코딩 컨벤션

- 각 화면은 단일 UiState data class를 StateFlow로 노출
- 이벤트성 처리(토스트, 네비게이션)는 Channel 또는 SharedFlow로 분리
- Repository는 domain 레이어에 인터페이스, data 레이어에 구현체
- 모든 네트워크/DB 작업은 코루틴. UI 레이어에서 직접 호출 금지
