package app.dqxn.android.sdk.contracts.provider

import app.dqxn.android.sdk.contracts.setup.SetupPageDefinition
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow

public interface DataProvider<T : DataSnapshot> : DataProviderSpec {
  public val snapshotType: KClass<T>

  public fun provideState(): Flow<T>

  public val schema: DataSchema

  public val setupSchema: List<SetupPageDefinition>

  public val subscriberTimeout: Duration

  public val firstEmissionTimeout: Duration

  public val isAvailable: Boolean

  public val connectionState: Flow<Boolean>

  public val connectionErrorDescription: Flow<String?>
}
