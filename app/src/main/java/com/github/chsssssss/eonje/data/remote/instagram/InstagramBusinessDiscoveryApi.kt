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
        fun fields(targetUsername: String) =
            "business_discovery.username($targetUsername)" +
                "{id,username,profile_picture_url," +
                "media.limit(25){permalink,caption,timestamp,media_url,media_type," +
                "children{media_url,media_type}}}"
    }
}
