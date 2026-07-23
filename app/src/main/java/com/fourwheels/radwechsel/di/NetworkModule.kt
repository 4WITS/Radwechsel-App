package com.fourwheels.radwechsel.di

import com.fourwheels.radwechsel.BuildConfig
import com.fourwheels.radwechsel.api.AuthApi
import com.fourwheels.radwechsel.api.FourWheelsApi
import com.fourwheels.radwechsel.auth.AuthInterceptor
import com.fourwheels.radwechsel.auth.TokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val AUTH_BASE_URL = BuildConfig.AUTH_BASE_URL
    private const val API_BASE_URL  = BuildConfig.API_BASE_URL
    private const val CLIENT_ID     = BuildConfig.CLIENT_ID
    const val SCOPE = "meta:read wheelhotel:read wheelhotel:write"

    private fun logging() = HttpLoggingInterceptor().apply {
        // BODY loggt Passwoerter und Tokens im Klartext -> nur im Debug-Build
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                else HttpLoggingInterceptor.Level.NONE
    }

    /**
     * Login-Client: OHNE AuthInterceptor/TokenAuthenticator.
     * Sonst würde der Refresh-Call (der über diesen Client läuft) bei 401 selbst
     * wieder den Authenticator triggern -> Henne/Ei bzw. Endlosschleife.
     */
    @Provides @Singleton @Named("auth")
    fun provideAuthClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging())
            .build()

    /**
     * API-Client: hängt den Bearer an (AuthInterceptor) und erneuert den Token
     * bei 401 automatisch (TokenAuthenticator).
     */
    @Provides @Singleton @Named("api")
    fun provideApiClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            .addInterceptor(logging())   // ganz zuletzt: sieht den echten Draht (inkl. Bearer)
            .build()

    @Provides @Singleton @Named("auth")
    fun provideAuthRetrofit(@Named("auth") client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(AUTH_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides @Singleton @Named("api")
    fun provideApiRetrofit(@Named("api") client: OkHttpClient): Retrofit =
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
