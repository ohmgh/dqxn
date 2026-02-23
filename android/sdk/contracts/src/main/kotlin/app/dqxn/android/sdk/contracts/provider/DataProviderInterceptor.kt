package app.dqxn.android.sdk.contracts.provider

import kotlinx.coroutines.flow.Flow

public interface DataProviderInterceptor {
  public fun <T : DataSnapshot> intercept(
    provider: DataProvider<T>,
    upstream: Flow<T>,
  ): Flow<T>
}
