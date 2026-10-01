@file:JsModule("firebase/app")

package com.royalchance.data.firebase.web.externals

// Declaraciones del SDK oficial de Firebase para JavaScript (paquete npm "firebase", API modular).
// Solo se declara lo que usa la app.

external interface FirebaseApp : JsAny

external fun initializeApp(options: JsAny): FirebaseApp
