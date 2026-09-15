package com.constrakr.network

import android.content.Context
import com.constrakr.BuildConfig
import com.constrakr.config.ConsTrakrConstants
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    fun create(context: Context): ConsTrakrApi {
        val prefs = context.getSharedPreferences("constrakr.sync", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("api_base_url", ConsTrakrConstants.API_BASE_URL)!!
        val logging = HttpLoggingInterceptor { message ->
            android.util.Log.d("ConsTrakr-HTTP", message)
        }.apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(90, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
        val moshi = Moshi.Builder()
            .add(UuidJsonAdapter)
            .add(KotlinJsonAdapterFactory())
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ConsTrakrApi::class.java)
    }

    fun authHeader(token: String?) = token?.let { "Bearer $it" }

    private fun String.ensureTrailingSlash() = if (endsWith("/")) this else "$this/"
}
