@file:JsModule("firebase/auth")

package com.royalchance.data.firebase.web.externals

import kotlin.js.Promise

external interface Auth : JsAny {
    val currentUser: User?
}

external interface User : JsAny {
    val uid: String
    val email: String?
    val emailVerified: Boolean
}

external interface UserCredential : JsAny

external interface AuthCredential : JsAny

external class EmailAuthProvider : JsAny {
    companion object {
        fun credential(email: String, password: String): AuthCredential
    }
}

external fun getAuth(app: FirebaseApp): Auth

external fun connectAuthEmulator(auth: Auth, url: String, options: JsAny?)

/** Devuelve la función para cancelar la suscripción. */
external fun onAuthStateChanged(auth: Auth, nextOrObserver: (User?) -> Unit): () -> Unit

external fun signInWithEmailAndPassword(auth: Auth, email: String, password: String): Promise<UserCredential>

external fun createUserWithEmailAndPassword(auth: Auth, email: String, password: String): Promise<UserCredential>

external fun reauthenticateWithCredential(user: User, credential: AuthCredential): Promise<UserCredential>

external fun sendPasswordResetEmail(auth: Auth, email: String): Promise<JsAny?>

external fun sendEmailVerification(user: User): Promise<JsAny?>

external fun reload(user: User): Promise<JsAny?>

external fun deleteUser(user: User): Promise<JsAny?>

external fun signOut(auth: Auth): Promise<JsAny?>
