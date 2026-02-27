package com.fourwheels.radwechsel.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.fourwheels.radwechsel.api.AuthApi
import com.fourwheels.radwechsel.api.FourWheelsApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val AUTH_BASE_URL = "https://login-test.4wheels.de/"
    private const val API_BASE_URL  = "https://api-test.4wheels.de/"

    const val CLIENT_ID = "iev2uogo6Viqueap"
    const val SCOPE     = "meta:read wheelhotel:read wheelhotel:write"

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
            .build()

    @Provides
    @Singleton
    @Named("auth")
    fun provideAuthRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(AUTH_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    @Named("api")
    fun provideApiRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides @Singleton
    fun provideAuthApi(@Named("auth") retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)

    @Provides @Singleton
    fun provideFourWheelsApi(@Named("api") retrofit: Retrofit): FourWheelsApi =
        retrofit.create(FourWheelsApi::class.java)

    @Provides @Named("clientId") fun provideClientId(): String = CLIENT_ID
    @Provides @Named("scope")    fun provideScope(): String    = SCOPE
}
