package com.mebonsoft.memorix.core.security

object SecretMediaPathSupport {
    const val SecretExtension = ".mrxsecret"

    fun isEncryptedPath(path: String): Boolean = path.endsWith(SecretExtension)

    fun encryptedPath(path: String): String = if (isEncryptedPath(path)) path else path + SecretExtension

    fun decryptedPath(path: String): String = if (isEncryptedPath(path)) {
        path.removeSuffix(SecretExtension)
    } else {
        path
    }
}
