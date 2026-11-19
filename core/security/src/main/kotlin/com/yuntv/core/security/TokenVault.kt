package com.yuntv.core.security

import com.yuntv.core.model.TokenBundle

interface TokenVault {
    suspend fun load(): TokenBundle?
    suspend fun save(tokenBundle: TokenBundle)
    suspend fun clear()
}
