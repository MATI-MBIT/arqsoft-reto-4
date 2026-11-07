package co.mati.reto4.comun;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/** Lee la PKI de prueba que genera {@code services:pki} y firma o verifica con ECDSA. */
public final class Pki {

    public static final String ALGORITMO = "SHA256withECDSA";

    private Pki() {}

    public static Path dir() {
        return Path.of(System.getProperty("pki.dir", System.getenv().getOrDefault("PKI_DIR", "deploy/pki")));
    }

    public static X509Certificate certificado(String nombre) {
        try {
            return certificado(Files.readAllBytes(dir().resolve(nombre + ".crt")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static X509Certificate certificado(byte[] pemODer) {
        try {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(pemODer));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("certificado ilegible", e);
        }
    }

    public static X509CRL crl(byte[] der) throws GeneralSecurityException {
        return (X509CRL) CertificateFactory.getInstance("X.509").generateCRL(new ByteArrayInputStream(der));
    }

    public static PrivateKey llave(String nombre) {
        try {
            String pem = Files.readString(dir().resolve(nombre + ".key"), StandardCharsets.US_ASCII)
                    .replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
            return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("llave ilegible: " + nombre, e);
        }
    }

    public static byte[] firmar(PrivateKey llave, byte[] datos) {
        try {
            Signature s = Signature.getInstance(ALGORITMO);
            s.initSign(llave);
            s.update(datos);
            return s.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean verificar(X509Certificate crt, byte[] datos, byte[] firma) {
        try {
            Signature s = Signature.getInstance(ALGORITMO);
            s.initVerify(crt.getPublicKey());
            s.update(datos);
            return s.verify(firma);
        } catch (GeneralSecurityException | RuntimeException e) {
            return false;
        }
    }
}
