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
│  ├─ home/            Screen + ViewModel + State
│  ├─ inbox/
│  ├─ resolve/         정리 화면 (단일/다중 모드)
│  ├─ placedetail/
│  ├─ settings/        지금은 빈 자리만 유지 (5단계 참고)
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
    val createdAt: Long
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
| HomeScreen | 지도 + 리스트 토글. 검색. 리스트 뷰에서 행별 폴더 칩으로 재배정 |
| InboxScreen | 미확정 게시물 카드 목록. 카드별 "후보 N곳" / "직접 찾기" 칩 |
| ResolveScreen | 단일/다중 모드. 위쪽 원본(열기 가능), 아래쪽 검색. 폴더 선택(선택사항, 다중 모드는 일괄 배정) |
| PlaceDetailScreen | 장소 상세 + 관련 게시물 목록(원본 열기 가능) + 폴더 재배정 |
| SettingsScreen | 지금은 빈 자리만 유지 (5단계에서 계정 관리·동기화 주기·토큰 상태를 걷어냄) |

하단 네비게이션 3탭: 홈 / 인박스(배지) / 설정

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

- [x] 위젯 — 인박스 정리 대기 개수를 보여주는 홈 화면 위젯
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

## 코딩 컨벤션

- 각 화면은 단일 UiState data class를 StateFlow로 노출
- 이벤트성 처리(토스트, 네비게이션)는 Channel 또는 SharedFlow로 분리
- Repository는 domain 레이어에 인터페이스, data 레이어에 구현체
- 모든 네트워크/DB 작업은 코루틴. UI 레이어에서 직접 호출 금지
