package com.mckimquyen.feature.vip

import com.mckimquyen.BuildConfig
import android.util.Base64

object AdKeys {
    const val PRIVACY_POLICY_URL = BuildConfig.PRIVACY_POLICY_URL

    val VIP_SECRET: String by lazy {
        String(Base64.decode(VIP_SECRET_B64, Base64.NO_WRAP))
    }

    private const val VIP_SECRET_B64 = "OWZBMHE3ZU4hMjdjTHgwNEAyMTk5M1kydTBJNyNRMA=="
}
