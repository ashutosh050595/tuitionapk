package com.example.network

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface SupabaseService {
    @Headers(
        "Content-Type: application/json",
        "Prefer: resolution=merge-duplicates"
    )
    @POST("rest/v1/tuition_backups")
    suspend fun upsertBackup(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Body body: List<BackupRecord>
    ): Response<Unit>

    @Headers(
        "Accept: application/json"
    )
    @GET("rest/v1/tuition_backups")
    suspend fun getBackup(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Query("email") emailQuery: String // "eq.user@email.com"
    ): Response<List<BackupRecord>>

    @Headers(
        "Accept: application/json"
    )
    @GET("rest/v1/tuition_backups")
    suspend fun pingSupabase(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Query("select") select: String = "email",
        @Query("limit") limit: Int = 1
    ): Response<List<BackupRecord>>
}

interface ResendService {
    @Headers("Content-Type: application/json")
    @POST("emails")
    suspend fun sendEmail(
        @Header("Authorization") authHeader: String, // "Bearer re_xxx"
        @Body body: ResendEmailRequest
    ): Response<ResendEmailResponse>
}

object NetworkClient {
    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val supabaseService: SupabaseService? by lazy {
        val baseUrl = BuildConfig.SUPABASE_URL.trim()
        if (baseUrl.isEmpty() || baseUrl.startsWith("https://yourproject") || !baseUrl.startsWith("http")) {
            null
        } else {
            val formattedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            Retrofit.Builder()
                .baseUrl(formattedUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(SupabaseService::class.java)
        }
    }

    val resendService: ResendService? by lazy {
        val baseUrl = "https://api.resend.com/"
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ResendService::class.java)
    }

    // Helper to get formatted keys
    fun getSupabaseKey(): String = BuildConfig.SUPABASE_ANON_KEY.trim()
    fun getResendKey(): String = BuildConfig.RESEND_API_KEY.trim()
    fun getResendFromEmail(): String = BuildConfig.RESEND_FROM_EMAIL.trim()
}
