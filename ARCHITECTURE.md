# 언제가지 (WhenAreWeGoing) — 구조 문서

인스타그램에서 본 맛집을 저장해두고, 나중에 외식할 때 지도에서 꺼내 보는 안드로이드 앱.

- **패키지명**: `com.github.chsssssss.eonje`
- **최소 지원 버전**: Android 8.0 (API 26)
- **언어/UI**: Kotlin + Jetpack Compose (Material 3)

---

## 1. 핵심 아이디어

인스타에서 맛집 게시물을 보면 "나중에 가봐야지" 하고 저장은 해두지만, 정작 외식할 때가 되면 저장한 게 어디 있는지 못 찾는다. 이 앱은 그 간극을 메운다.

```
인스타 보다가 "공유" 누름   →   앱이 알아서 가게를 찾아둠   →   외식할 때 지도에서 꺼내 봄
     (사용자 할 일 끝)              (백그라운드, 사용자 모름)          (지도에 핀으로 쌓임)
```

### 설계 원칙

| # | 원칙 | 의미 |
|---|---|---|
| 1 | **저장은 절대 실패하지 않는다** | 가게 이름을 못 찾아도, 네트워크가 끊겨도 URL은 무조건 저장된다 |
| 2 | **자동 인식은 최적화이지 필수가 아니다** | 인식에 실패해도 앱은 정상 동작한다. 사용자가 직접 찾으면 된다 |
| 3 | **서버를 두지 않는다** | 사용자가 늘어도 개발자 비용이 비례해서 늘지 않는 구조. 유일한 예외는 API 키를 숨기기 위한 Cloud Function 하나 |
| 4 | **게시물 단위로 관심을 표현한다** | 계정을 통째로 팔로우해서 전부 자동 수집하는 방식은 실제 사용 패턴과 안 맞아서 걷어냄 (→ 9장 참고) |

---

## 2. 유저 플로우

### 전체 그림

```
┌─────────────┐
│  인스타그램   │
│   게시물     │
└──────┬──────┘
       │ 공유 버튼 → "언제가지" 선택
       ▼
┌─────────────────────┐
│ F1. 공유 수신         │  화면 없음. 토스트만 띄우고 즉시 종료
│ ShareReceiver        │  Room에 URL 저장 (밀리초, 절대 실패 안 함)
└──────┬──────────────┘
       │ WorkManager에 작업 위임
       ▼
┌─────────────────────┐
│ F2. 자동 인식 (백그라운드) │  캡션 조회 → AI 파싱 → 지도 검색
│ CaptionParsingWorker │  사용자는 이 과정을 몰라도 됨
└──────┬──────────────┘
       │
       ├──── 가게 1곳 확정 ───────────► RESOLVED     → 홈 지도에 바로 등장 + 알림
       ├──── 후보가 여러 개 ──────────► NEEDS_REVIEW → 인박스에서 골라야 함
       └──── 못 찾음 / 네트워크 실패 ──► UNRESOLVED   → 인박스에서 직접 검색
                                            │
                                            ▼
                                  ┌──────────────────┐
                                  │ F3. 정리          │  사용자가 가게를 확정
                                  │ ResolveScreen     │
                                  └────────┬─────────┘
                                           ▼
                                  ┌──────────────────┐
                                  │ F4. 꺼내 쓰기      │  지도 / 리스트 / 태그 필터
                                  │ HomeScreen        │
                                  └──────────────────┘
```

### 플로우 1 — 저장 (F1)

사용자가 하는 일은 **공유 버튼 누르기 하나뿐**이다.

1. 인스타그램에서 게시물 → 공유 → "언제가지" 선택
2. `ShareReceiverActivity`가 인텐트를 받음 (투명 테마, UI 없음)
3. URL에서 shortcode 추출 (`/p/{code}/`, `/reel/{code}/`, `/tv/{code}/`), 추적용 쿼리 파라미터(`?igsh=...`) 제거
4. Room에 `SavedPost` 레코드 생성 — **동기 처리**라 밀리초 단위로 끝남
5. 토스트 표시 후 즉시 `finish()`

| 상황 | 토스트 문구 |
|---|---|
| 정상 저장 | "저장됨" |
| 같은 게시물 중복 공유 | "이미 저장된 게시물이에요" |
| 인스타 링크가 아님 | "인스타그램 링크를 찾을 수 없어요" |

> 이 단계는 네트워크를 전혀 쓰지 않는다. 비행기 모드에서도 저장은 성공한다. (설계 원칙 1)

### 플로우 2 — 자동 인식 (F2, 백그라운드)

`CaptionParsingWorker`가 WorkManager로 실행된다. 사용자는 이 과정을 볼 필요가 없고, 인박스에서 "정리 중" 표시만 보인다.

```
1. 캡션 조회     게시물 URL → Cloud Function(fetchInstagramMeta) → Apify
                 결과: 계정명 + 캡션 원문(안 잘림) + 대표 이미지 URL

2. AI 파싱 1차   캡션 텍스트만 → LLM
                 ├ 기기가 지원하면: 온디바이스 Gemini Nano (ML Kit GenAI) — 비용 0, 네트워크 불필요
                 └ 미지원/실패 시: Firebase AI Logic (Gemini 클라우드, 무료 티어)
                 결과: [{상호명, 지역, 메뉴}] 배열

3. AI 파싱 2차   1차에서 아무것도 못 건졌고 이미지가 있으면
                 캡션 + 이미지를 같이 전달해 재시도 (비전은 클라우드만 사용)

4. 지도 검색     추출된 항목별로 "지역 + 상호명"을 카카오 로컬 API로 검색
                 항목당 후보 최대 5개

5. 상태 결정     아래 표 참고
```

| 조건 | 결과 상태 | 사용자에게 보이는 모습 |
|---|---|---|
| 추출 1건 + 검색 결과 1건 | `RESOLVED` | 홈 지도에 바로 등장 + "○○식당 저장됨" 알림 |
| 추출 2건 이상 (리스트형 게시물) | `NEEDS_REVIEW` | 인박스에 "후보 N곳" 칩 → 다중 선택 화면 |
| 검색 결과가 여러 개 | `NEEDS_REVIEW` | 인박스에 "후보 N곳" 칩 → 후보 중 선택 |
| 캡션에 상호명이 없거나 검색 실패 | `UNRESOLVED` (`PLACE_NOT_FOUND`) | 인박스에 "직접 찾기" 칩 |
| 네트워크/서버 실패 5회 초과 | `UNRESOLVED` (`NETWORK_ERROR`) | 인박스에 "직접 찾기" 칩 |

**다중 장소는 절대 자동 확정하지 않는다.** "대구 맛집 5곳" 같은 리스트형 게시물에서 원하지 않는 가게까지 저장되면 안 되기 때문이다.

**재시도 정책**: 네트워크·서버 오류는 WorkManager 지수 백오프로 재시도한다 (10초 → 20초 → 40초 → 80초 → 160초, 최대 5회). 그래도 안 되면 조용히 `UNRESOLVED`로 넘어가고, 앱 동작에는 지장이 없다.

### 플로우 3 — 정리 (인박스 → 정리 화면)

자동으로 확정되지 않은 게시물만 인박스에 쌓인다.

**인박스 화면**
- 미확정 게시물 카드 목록 (썸네일 + 상대 시간 + 상태 칩)
- 상태 칩: `정리 중`(처리 중) / `후보 N곳`(고르면 됨) / `직접 찾기`(직접 검색해야 함)
- 하단 **"순서대로 정리하기"** 버튼 — 밀린 게시물을 차례로 정리하는 흐름
- 30일 넘은 항목이 있으면 일괄 삭제 제안 배너

**정리 화면 (`ResolveScreen`)** — 두 가지 모드

| 모드 | 언제 | 화면 구성 |
|---|---|---|
| **단일 모드** | 후보가 없음 (`UNRESOLVED`) | 위쪽에 원본 게시물(썸네일·원본 열기), 아래쪽에 카카오 검색창 → 직접 검색해서 선택 |
| **다중 모드** | 후보가 있음 (`NEEDS_REVIEW`) | 추출된 항목별 그룹 + 체크박스. 각 그룹을 펼치면 후보 목록이 나오고, 원하는 가게만 골라서 저장 |

두 모드 모두 **"원본 열기"** 로 인스타그램 앱/웹의 원본 게시물로 이동할 수 있다.

### 플로우 4 — 꺼내 쓰기 (홈 화면)

확정된 가게가 쌓이는 곳. 지도와 리스트를 토글해서 본다.

**지도 뷰**
- 저장된 모든 장소가 지도에 마커로 표시됨
- 마커를 탭하면 → 그 마커만 강조색(주황)으로 바뀌고 하단에 가게 정보 카드가 뜸
- 같은 마커를 다시 탭하거나, 지도의 빈 곳을 탭하면 → 선택 해제 (카드 사라짐)
- 하단 카드를 탭하면 → 장소 상세 화면으로 이동

**리스트 뷰** — 저장된 장소를 목록으로

**공통 필터**
- 상단 검색창: 가게 이름 / 카테고리 / 주소로 검색
- 태그 필터 칩: 태그로 좁혀보기
- "정리 대기 N" 배너: 인박스에 밀린 게 있으면 표시

**장소 상세 화면** — 가게 정보 + 메모 + 태그 + 이 가게가 나온 게시물 목록(탭하면 인스타 원본으로)

### 보조 플로우

| 기능 | 동작 |
|---|---|
| **홈 화면 위젯** | 인박스에 정리 대기 중인 개수를 홈 화면에 표시 (Glance) |
| **정리 알림** | 정리 안 된 게시물이 쌓여 있으면 7일마다 알림 |
| **확정 알림** | 자동 확정에 성공하면 "○○식당 저장됨" 알림 |
| **오래된 항목 정리** | 30일 넘게 방치된 미확정 항목을 일괄 삭제 제안 |

---

## 3. 화면 구성

```
하단 네비게이션 3탭
├─ 홈 (HomeScreen)         지도 ↔ 리스트 토글, 검색, 태그 필터
│   └─ 장소 상세 (PlaceDetailScreen)
├─ 인박스 (InboxScreen)     미확정 게시물 목록 (배지로 개수 표시)
│   └─ 정리 화면 (ResolveScreen)   단일/다중 모드
└─ 설정 (SettingsScreen)    현재는 비어 있음 (→ 9장 참고)
```

| 라우트 | 화면 |
|---|---|
| `home` | 홈 |
| `inbox` | 인박스 |
| `settings` | 설정 |
| `resolve/{postId}` | 정리 화면 |
| `placeDetail/{placeId}` | 장소 상세 |

---

## 4. 기술 스택

| 영역 | 선택 | 비고 |
|---|---|---|
| UI | Jetpack Compose + Material 3 | |
| 아키텍처 | MVVM + UDF | 화면당 단일 `UiState`를 `StateFlow`로 노출 |
| DI | Hilt | |
| 로컬 DB | Room | 현재 스키마 버전 6 |
| 네비게이션 | Navigation Compose | |
| 비동기 | Coroutines + Flow | |
| 백그라운드 | WorkManager | 캡션 파싱, 정리 알림 |
| 네트워크 | Retrofit + OkHttp + kotlinx.serialization | 카카오 로컬 API |
| 지도 | 카카오맵 SDK v2 | 카카오 로컬 API와 좌표계 통일 |
| 이미지 | Coil | |
| 위젯 | Glance (AppWidget) | |
| AI (온디바이스) | ML Kit GenAI — Gemini Nano | 지원 기기에서 우선 시도 |
| AI (클라우드) | Firebase AI Logic — Gemini Flash | 무료 티어, App Check 필수 |
| 백엔드 | Firebase Cloud Functions (Node.js) | 함수 하나뿐 (→ 6장) |
| 외부 스크래핑 | Apify `instagram-scraper` | Cloud Function 뒤에 숨김 |

---

## 5. 아키텍처

### 레이어 구조

```
ui/          Compose 화면 + ViewModel + UiState
             ↓ (ViewModel이 UseCase/Repository 호출)
domain/      비즈니스 로직. Repository는 인터페이스만 여기에 둔다
             ↓
data/        Repository 구현체, Room, Retrofit, WorkManager, Firebase 클라이언트
```

**규칙**
- Repository는 `domain`에 인터페이스, `data`에 구현체 (Hilt로 바인딩)
- UI 레이어에서 네트워크/DB를 직접 호출하지 않는다
- 토스트·네비게이션 같은 일회성 이벤트는 `Channel`/`SharedFlow`로 분리

### 패키지 구조

```
com.github.chsssssss.eonje/
├─ di/                    Hilt 모듈 (Database, Network, Repository, WorkManager, Notification)
├─ data/
│  ├─ local/              Room — Entity, Dao, Database, TypeConverter
│  ├─ remote/             카카오 Retrofit API + Firebase 앱 이름 상수
│  ├─ repository/         Repository 구현체
│  ├─ worker/             CaptionParsingWorker, InboxReminderWorker
│  └─ notification/       알림 구현체
├─ domain/
│  ├─ model/              도메인 모델 (ExtractedPlace, PlaceCandidate, ResolveStatus …)
│  ├─ repository/         Repository 인터페이스
│  ├─ usecase/            SaveSharedPostUseCase, MatchPostUseCase
│  └─ util/               InstagramUrlParser, RelativeTimeFormatter
├─ ui/
│  ├─ home/ inbox/ resolve/ placedetail/ settings/
│  ├─ components/         공용 컴포저블 (StatusChip, BottomNavBar …)
│  ├─ navigation/         NavHost, 라우트 정의
│  └─ theme/
├─ share/                 ShareReceiverActivity (F1 진입점)
└─ widget/                Glance 위젯
```

### 핵심 클래스

| 클래스 | 역할 |
|---|---|
| `SaveSharedPostUseCase` | F1 — URL 파싱 → Room 저장 → Worker 등록 |
| `MatchPostUseCase` | F2 전체 파이프라인 — 캡션 조회 → 파싱 → 검색 → 상태 결정 |
| `CaptionParsingRepositoryImpl` | 온디바이스 우선 → 클라우드 폴백 분기 처리 |
| `InstagramMetaLookupRepositoryImpl` | Cloud Function 호출 래퍼 |
| `KakaoMapView` | 카카오맵 SDK를 Compose로 감싼 `AndroidView` |

---

## 6. 백엔드 구조

**자체 서버는 없다.** 모든 API는 앱에서 직접 호출한다. 단 하나의 예외가 Cloud Function이다.

### `fetchInstagramMeta` (Firebase Cloud Functions, Node.js)

```
앱  ──(게시물 URL + App Check 토큰)──►  Cloud Function  ──►  Apify instagram-scraper
                                              │
    ◄──({ username, caption, imageUrl })──────┘
```

| 항목 | 값 |
|---|---|
| 리전 | `asia-northeast3` (서울) |
| 타임아웃 | 60초 |
| 보안 | `enforceAppCheck: true` — App Check 검증된 앱만 호출 가능 |
| 시크릿 | `APIFY_TOKEN` (Secret Manager) |

**왜 이 함수가 필요한가**: Apify API 토큰을 앱에 넣으면 APK 디컴파일로 털린다. 털린 토큰으로 누가 마음껏 요청을 보내면 비용은 토큰 소유자에게 청구된다. 그래서 토큰은 서버에만 두고, 앱은 App Check로 신원을 증명해서 이 함수만 호출한다.

**왜 Apify를 쓰는가**: 인스타그램 공식 API로는 임의 게시물의 캡션을 가져올 방법이 없다. Business Discovery API는 계정명을 미리 알아야만 호출할 수 있는데, 공유받은 URL만으로는 그 게시물이 누구 것인지 모른다. Apify는 URL 하나만으로 계정명과 캡션 원문(잘리지 않은 전체)을 돌려준다.

### Firebase 프로젝트를 둘로 나눈 이유

| 프로젝트 | 요금제 | 용도 |
|---|---|---|
| `fir-gemini-706d3` | **Spark (무료)** — 결제 계정 절대 연결 금지 | Firebase AI Logic (Gemini 캡션 파싱) |
| `wherewegoing-a5493` | **Blaze (종량제)** | Cloud Functions + Secret Manager |

Gemini Developer API는 **결제 계정이 연결되지 않은 프로젝트에서만 진짜 무료 티어가 유지된다.** 한 번 결제 계정이 붙으면 무료 티어 대신 선불(Prepay) 결제로 전환되고, 무료로 되돌릴 수 없다. 반면 Apify 토큰을 보관할 Secret Manager는 Blaze가 필수다. 두 요구사항이 충돌해서 프로젝트를 분리했다.

앱에서는 `EonjeApplication`이 **Gemini 프로젝트를 기본(default) FirebaseApp으로**, Cloud Functions 프로젝트를 이름 있는(named) FirebaseApp으로 각각 초기화한다. Firebase AI Logic SDK가 이름과 무관하게 "기본 FirebaseApp"만 찾기 때문에 이 순서가 중요하다.

### App Check

Firebase AI Logic과 Cloud Function 둘 다 App Check로 보호한다. 검증되지 않은 요청은 아예 거부된다.

- 디버그 빌드 → Debug Provider (기기별 토큰을 Firebase 콘솔에 등록해야 함)
- 릴리스 빌드 → Play Integrity Provider

프로젝트가 둘이라 **App Check도 프로젝트별로 따로 설치**한다.

---

## 7. 데이터 모델

게시물과 장소는 **다대다** 관계다. 게시물 하나에 장소가 여럿일 수 있고(리스트형 게시물), 같은 가게가 여러 게시물에 나올 수도 있다.

```
SavedPostEntity ──┐                    ┌── PlaceEntity
 (게시물)          │  PostPlaceCrossRef │    (장소)
                  └────────────────────┘        │
                                                │  PlaceTagCrossRef
ExtractedCandidateEntity                        └──────────── TagEntity
 (정리 화면에 보여줄 후보 목록)                                    (태그)
```

| 테이블 | 역할 | 주요 필드 |
|---|---|---|
| `saved_posts` | 공유받은 게시물 | `instagramUrl`, `shortcode`, `caption`, `thumbnailUrl`, `status`, `extractedCount`, `unresolvedReason` |
| `places` | 확정된 장소 | `name`, `address`, `latitude`, `longitude`, `kakaoPlaceId`, `category`, `memo`, `status` |
| `post_place_cross_ref` | 게시물 ↔ 장소 연결 | |
| `extracted_candidates` | AI가 뽑은 항목별 카카오 후보 | `extractionIndex`, `extractedName`, `rank`, 후보 정보 |
| `tags` / `place_tag_cross_ref` | 태그 | |

### 상태 전이

```
                    ┌──────────────► RESOLVED (확정, 홈 지도에 표시)
                    │                    ▲
  PENDING ──────────┼──► NEEDS_REVIEW ───┤  사용자가 후보 중 선택
 (저장 직후)         │    (후보 있음)      │
                    │                    │
                    └──► UNRESOLVED ─────┘  사용자가 직접 검색해서 선택
                         (못 찾음)
```

`UNRESOLVED`의 세부 사유(`UnresolvedReason`)

| 값 | 의미 |
|---|---|
| `PLACE_NOT_FOUND` | 캡션 파싱 또는 카카오 검색에서 장소를 못 찾음 (비공개 게시물로 캡션 자체를 못 가져온 경우 포함) |
| `NETWORK_ERROR` | 재시도 한도(5회)를 넘겨서 포기 |

---

## 8. 비용 구조

사용자가 늘어도 개발자가 사용량에 비례하는 비용을 지지 않는 것이 목표다.

| 항목 | 방식 | 비용 |
|---|---|---|
| 캡션 파싱 | 온디바이스 Gemini Nano 우선 / 클라우드 Gemini 무료 티어 폴백 | **0원**. 온디바이스는 추론이 기기에서 일어나 무제한. 클라우드도 무료 티어 안 |
| 카카오 로컬 검색 | 앱에서 직접 호출 (앱 서명 기반 키 제한) | 카카오 무료 쿼터 내 |
| 캡션 조회 (Apify) | Cloud Function 경유 | Apify 무료 크레딧 월 $5 ≈ 1,850건, 초과 시 건당 약 $0.0027 |
| Cloud Functions 인프라 | Cloud Run 2세대 | 월 200만 호출까지 무료 티어 |

**주의: 배포 비용은 무료 티어가 아니다.** 함수를 배포할 때마다 컨테이너 이미지가 빌드되어 Artifact Registry에 저장되는데, 여기 무료 용량은 0.5GB뿐이라 몇 번 배포하면 소액(수백 원)이 과금된다. Firebase의 "지출 한도"를 너무 낮게(예: 1,000원) 잡아두면 이 소액 때문에 한도가 트립되어 **프로젝트 결제가 통째로 비활성화**되고, 그러면 무료로 처리됐을 함수 호출까지 전부 막힌다. 한도는 1만 원 정도로 여유 있게 두는 것이 안전하다.

**Claude / GPT API는 쓰지 않는다.** 무료 티어가 없어 사용량에 비례해 계속 비용이 발생하므로 이 프로젝트의 원칙과 맞지 않는다.

---

## 9. 주요 설계 결정

### 계정 등록/동기화 기능(구 F3)을 통째로 제거했다

**원래 구조**: 맛집 인스타 계정을 등록해두면 Business Discovery API로 최근 게시물 25건을 로컬에 캐싱하고, 공유받은 게시물이 캐시에 있으면 바로 매칭하는 방식이었다.

**왜 걷어냈나**
1. **기술적 한계** — Business Discovery는 계정명을 알아야 호출할 수 있는데, 캐시에 없는 게시물은 그게 어느 계정 것인지 모른다. 그래서 등록된 계정을 전부 순서대로 페이지네이션하며 뒤져야 했고, 계정이 많으면 느리고 Meta API가 특정 페이지에서 500을 반환하면 그 계정 전체가 막혔다.
2. **더 근본적으로, 사용 패턴이 안 맞았다** — 사용자는 "이 계정을 팔로우해서 새 글을 전부 자동 수집"하고 싶은 게 아니라, **"인스타 보다가 마음에 드는 것만"** 공유한다. 관심의 단위가 계정이 아니라 게시물이다.

**대체 방식**: Apify 기반 `fetchInstagramMeta`가 게시물 URL 하나만으로 캡션을 가져온다. 계정을 등록할 필요도, 추적할 필요도 없다.

이 결정으로 계정 관리 화면, 일일 동기화 Worker, 캐시 테이블 2개, Business Discovery API 클라이언트, 토큰 상태 관리가 전부 사라졌다. 설정 화면은 관리할 항목이 없어져 현재 비어 있다.

### 그 외

| 결정 | 이유 |
|---|---|
| 공유 저장을 동기 처리로 | 밀리초 단위로 끝나고, 비동기로 만들면 "저장 실패" 가능성이 생긴다 (설계 원칙 1 위배) |
| 다중 장소 자동 확정 금지 | 리스트형 게시물에서 원하지 않는 가게까지 저장되면 사용자가 직접 지워야 한다 |
| 카카오맵 + 카카오 로컬 조합 | 좌표계가 통일되어 변환이 필요 없다 |
| 온디바이스 AI 우선 | 비용 0, 네트워크 불필요, 프라이버시. 단 비전(이미지)은 아직 불안정해 클라우드만 사용 |
| Room `fallbackToDestructiveMigration` | 출시 전이라 스키마 변경 시 마이그레이션 대신 초기화. **출시 전에 반드시 제거해야 함** |

---

## 10. 알려진 이슈 / 앞으로 할 일

- [ ] **출시 전 필수**: Room `fallbackToDestructiveMigration` 제거하고 실제 마이그레이션 작성
- [ ] **출시 전 필수**: App Check 디버그 토큰 정리 (`firebase appcheck:debugtokens:delete`)
- [ ] `UnresolvedReason.NETWORK_ERROR`와 `PLACE_NOT_FOUND`가 UI에서 똑같이 "직접 찾기"로 보인다 — 인프라 장애인지 진짜 못 찾은 건지 구분이 안 됨
- [ ] Cloud Function에 `maxInstances` 미설정 — 폭주 시 비용 상한이 없다. 지출 한도 킬스위치보다 이쪽이 안전한 방어책
- [ ] `domain/model/DiscoveredAccount.kt`는 F3 제거 후 남은 미사용 코드
- [ ] Cloud Run 콜드 스타트 시 "no available instance"로 요청이 거부되는 경우가 있음 → 재시도 백오프 때문에 체감 처리 시간이 수 분까지 늘어남
