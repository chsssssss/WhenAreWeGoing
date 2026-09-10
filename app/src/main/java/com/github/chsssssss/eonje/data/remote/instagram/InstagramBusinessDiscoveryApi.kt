package com.github.chsssssss.eonje.data.remote.instagram

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface InstagramBusinessDiscoveryApi {
    @GET("v25.0/{myIgUserId}")
    suspend fun getBusinessDiscovery(
        @Path("myIgUserId") myIgUserId: String,
        @Query("fields") fields: String,
        @Query("access_token") accessToken: String,
    ): BusinessDiscoveryResponse

    companion object {
        /** [after]가 주어지면 그 커서 다음 페이지를 요청한다 (오래된 게시물 조회용). */
        fun fields(targetUsername: String, after: String? = null): String {
            val afterClause = after?.let { ".after($it)" }.orEmpty()
            return "business_discovery.username($targetUsername)" +
                "{id,username,profile_picture_url," +
                "media$afterClause.limit(25){permalink,caption,timestamp,media_url,media_type," +
                "children{media_url,media_type}}}"
        }
    }
}
