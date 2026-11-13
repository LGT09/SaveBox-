package com.example.data.remote

import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface NayanApiService {

    @Headers("Accept: application/json")
    @GET("ytsearch/playlist")
    suspend fun searchYouTubePlaylists(@Query("query") query: String): Response<JsonElement>

    @Headers("Accept: application/json")
    @GET("ytsearch")
    suspend fun searchYouTube(@Query("query") query: String): Response<JsonElement>

    @Headers("Accept: application/json")
    @GET("ytdown")
    suspend fun downloadYouTube(@Query("url") url: String): Response<JsonElement>

    @Headers("Accept: application/json")
    @GET("youtube")
    suspend fun fetchYouTubeMedia(@Query("url") url: String): Response<JsonElement>

    @Headers("Accept: application/json")
    @GET("twitterdown")
    suspend fun downloadTwitter(@Query("url") url: String): Response<JsonElement>

    @Headers("Accept: application/json")
    @GET("tikdown")
    suspend fun downloadTikTok(@Query("url") url: String): Response<JsonElement>

    @Headers("Accept: application/json")
    @GET("threads")
    suspend fun downloadThreads(@Query("url") url: String): Response<JsonElement>

    @Headers("Accept: application/json")
    @GET("teraboxv2")
    suspend fun downloadTeraboxV2(@Query("url") url: String): Response<JsonElement>

    @Headers("Accept: application/json")
    @GET("terabox")
    suspend fun downloadTerabox(@Query("url") url: String): Response<JsonElement>

    @Headers("Accept: application/json")
    @GET("spotifyDl")
    suspend fun downloadSpotify(@Query("url") url: String): Response<JsonElement>

    @Headers("Accept: application/json")
    @GET("spotify-search")
    suspend fun searchSpotify(@Query("name") query: String): Response<JsonElement>

    @Headers("Accept: application/json")
    @GET("soundcloud-search")
    suspend fun searchSoundCloud(@Query("name") query: String): Response<JsonElement>

    @Headers("Accept: application/json")
    @GET("soundcloud")
    suspend fun downloadSoundCloud(@Query("url") url: String): Response<JsonElement>

    @Headers("Accept: application/json")
    @GET("alldown")
    suspend fun downloadUniversal(@Query("url") url: String): Response<JsonElement>

    @Headers("Accept: application/json")
    @GET("alldown-v2")
    suspend fun downloadUniversalV2(@Query("url") url: String): Response<JsonElement>

    @Headers("Accept: application/json")
    @GET("fbdown2")
    suspend fun downloadFacebook(@Query("url") url: String): Response<JsonElement>

    @Headers("Accept: application/json")
    @GET("GDLink")
    suspend fun downloadGoogleDrive(@Query("url") url: String): Response<JsonElement>

    @Headers("Accept: application/json")
    @GET("instagram")
    suspend fun downloadInstagram(@Query("url") url: String): Response<JsonElement>

    @Headers("Accept: application/json")
    @GET("likee")
    suspend fun downloadLikee(@Query("url") url: String): Response<JsonElement>

    @Headers("Accept: application/json")
    @GET("ndown")
    suspend fun downloadNDown(@Query("url") url: String): Response<JsonElement>

    @Headers("Accept: application/json")
    @GET("pintarest")
    suspend fun downloadPinterest(@Query("url") url: String): Response<JsonElement>
}
