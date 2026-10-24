package my.passman.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import my.passman.util.ElapsedRealtimeTimeSource
import kotlin.time.TimeSource

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    @Provides
    fun provideTimeSource(): TimeSource = ElapsedRealtimeTimeSource
}
