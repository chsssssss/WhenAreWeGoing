const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");

// Apify API 토큰 — 앱에는 절대 내장하지 않고 여기(서버)에만 보관한다.
// 배포 전 1회: firebase functions:secrets:set APIFY_TOKEN
const APIFY_TOKEN = defineSecret("APIFY_TOKEN");

// apify/instagram-scraper — 게시물 하나당 1건 과금(무료 티어 월 ~1,850건).
const ACTOR_ID = "apify~instagram-scraper";
const INSTAGRAM_URL_PATTERN = /^https:\/\/(www\.)?instagram\.com\//;

/**
 * F2 캡션 파싱용: 공유받은 인스타그램 게시물 URL만으로 작성자 계정명과 캡션 원문을 얻는다.
 * 실패하면(Apify 오류, 비공개 게시물 등) 앱은 WorkManager 재시도 후 UNRESOLVED로 종료한다.
 */
exports.fetchInstagramMeta = onCall(
  {
    enforceAppCheck: true,
    secrets: [APIFY_TOKEN],
    region: "asia-northeast3",
    timeoutSeconds: 60,
  },
  async (request) => {
    const url = request.data?.url;
    if (typeof url !== "string" || !INSTAGRAM_URL_PATTERN.test(url)) {
      throw new HttpsError("invalid-argument", "유효한 인스타그램 URL이 아니에요");
    }

    const apifyUrl = `https://api.apify.com/v2/acts/${ACTOR_ID}/run-sync-get-dataset-items?token=${APIFY_TOKEN.value()}`;

    let items;
    try {
      const response = await fetch(apifyUrl, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          resultsType: "posts",
          directUrls: [url],
          resultsLimit: 1,
        }),
      });
      if (!response.ok) {
        throw new Error(`Apify responded ${response.status}`);
      }
      items = await response.json();
    } catch (error) {
      throw new HttpsError("unavailable", "게시물 정보를 가져오지 못했어요", error?.message);
    }

    const post = Array.isArray(items) ? items[0] : null;
    if (!post || typeof post.ownerUsername !== "string" || post.ownerUsername.length === 0) {
      return { username: null, caption: null, imageUrl: null };
    }

    return {
      username: post.ownerUsername,
      caption: typeof post.caption === "string" ? post.caption : null,
      imageUrl: typeof post.displayUrl === "string" ? post.displayUrl : null,
    };
  }
);
