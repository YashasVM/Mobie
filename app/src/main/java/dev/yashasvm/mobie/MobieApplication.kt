package dev.yashasvm.mobie

import android.app.Application
import dev.yashasvm.mobie.core.AppContainer

class MobieApplication : Application() {
    val container by lazy { AppContainer(this) }
}
