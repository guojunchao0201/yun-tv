package com.yuntv.app

import android.app.Application
import com.yuntv.core.model.MediaPathPolicy

class AppContainer(
    val application: Application,
) {
    val defaultRootPath: String = MediaPathPolicy.DEFAULT_ROOT_PATH
}
