// Genera la PKI de prueba: la raíz de la ECD, una autoridad no aceptada, la
// llave de estampa, los certificados de los remitentes, la CRL y la llave con
// que BCR firma la evidencia. Uso: make pki
plugins { application }

dependencies { implementation(libs.bcpkix) }

application { mainClass.set("co.mati.reto4.pki.GenerarPki") }
