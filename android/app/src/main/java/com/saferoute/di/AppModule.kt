package com.saferoute.di

import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.saferoute.data.remote.ApiClient
import com.saferoute.data.remote.AuthInterceptor
import com.saferoute.data.remote.DeviceIdManager
import com.saferoute.data.remote.SafeRouteApi
import com.saferoute.data.remote.TokenManager
import com.saferoute.navigation.NavigationManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideTokenManager(
        @ApplicationContext context: Context
    ): TokenManager = TokenManager(context)

    @Provides
    @Singleton
    fun provideDeviceIdManager(
        @ApplicationContext context: Context
    ): DeviceIdManager = DeviceIdManager(context)

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        tokenManager: TokenManager,
        deviceIdManager: DeviceIdManager
    ): AuthInterceptor = AuthInterceptor(tokenManager, deviceIdManager)

    @Provides
    @Singleton
    fun provideApiClient(
        authInterceptor: AuthInterceptor
    ): ApiClient = ApiClient(authInterceptor)

    @Provides
    @Singleton
    fun provideSafeRouteApi(
        apiClient: ApiClient
    ): SafeRouteApi = SafeRouteApi(apiClient)

    @Provides
    @Singleton
    fun provideFusedLocationProviderClient(
        @ApplicationContext context: Context
    ): FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    @Provides
    @Singleton
    fun provideNavigationManager(
        @ApplicationContext context: Context,
        fusedLocationClient: FusedLocationProviderClient
    ): NavigationManager = NavigationManager(context, fusedLocationClient)
}
