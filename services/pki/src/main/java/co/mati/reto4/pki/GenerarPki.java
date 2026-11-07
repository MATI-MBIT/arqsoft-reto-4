package co.mati.reto4.pki;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Genera la PKI de prueba del experimento en el directorio que se le indique.
 *
 * <ul>
 *   <li>{@code raiz}: la raíz de la ECD de prueba, la única aceptada por Orquídea.</li>
 *   <li>{@code intrusa}: una autoridad que Orquídea no acepta (caso «CA no aceptada»).</li>
 *   <li>{@code tsa}: la llave con que la ECD firma las estampas cronológicas.</li>
 *   <li>{@code R001}..{@code R010}: los remitentes legítimos.</li>
 *   <li>{@code R900}: un remitente cuyo certificado está revocado en la CRL.</li>
 *   <li>{@code R001-intruso}: un certificado con el nombre de R001, emitido por la intrusa.</li>
 *   <li>{@code crl.der}: la lista de revocación de la raíz, con R900.</li>
 *   <li>{@code bcr}: la llave con que BCR firma cada entrada del registro de evidencia.</li>
 * </ul>
 *
 * Las llaves son EC P-256 en PKCS#8 PEM; los certificados, X.509 en PEM. Todo es
 * material de prueba: no sale de la máquina y no se versiona.
 */
public final class GenerarPki {

    private static final Instant AHORA = Instant.now();
    private static final Date DESDE = Date.from(AHORA.minus(Duration.ofDays(1)));
    private static final Date HASTA = Date.from(AHORA.plus(Duration.ofDays(365)));
    private static long serial = 1000;

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 ? args[0] : "deploy/pki");
        Files.createDirectories(dir);

        KeyPair raiz = par();
        X509CertificateHolder raizCrt = ca("CN=ECD Prueba Raiz,O=ECD,C=CO", raiz, "CN=ECD Prueba Raiz,O=ECD,C=CO", raiz.getPrivate());
        guardar(dir, "raiz", raizCrt, raiz.getPrivate());

        KeyPair intrusa = par();
        X509CertificateHolder intrusaCrt = ca("CN=Autoridad No Aceptada,O=Otra,C=CO", intrusa, "CN=Autoridad No Aceptada,O=Otra,C=CO", intrusa.getPrivate());
        guardar(dir, "intrusa", intrusaCrt, intrusa.getPrivate());

        KeyPair tsa = par();
        guardar(dir, "tsa", hoja("CN=ECD Estampa,O=ECD,C=CO", tsa, raizCrt.getSubject(), raiz.getPrivate()), tsa.getPrivate());

        for (int i = 1; i <= 10; i++) {
            String nombre = String.format("R%03d", i);
            KeyPair p = par();
            guardar(dir, nombre, hoja("CN=" + nombre + ",O=Remitente,C=CO", p, raizCrt.getSubject(), raiz.getPrivate()), p.getPrivate());
        }

        KeyPair revocado = par();
        X509CertificateHolder revocadoCrt = hoja("CN=R900,O=Remitente,C=CO", revocado, raizCrt.getSubject(), raiz.getPrivate());
        guardar(dir, "R900", revocadoCrt, revocado.getPrivate());

        KeyPair impostor = par();
        guardar(dir, "R001-intruso", hoja("CN=R001,O=Remitente,C=CO", impostor, intrusaCrt.getSubject(), intrusa.getPrivate()), impostor.getPrivate());

        X509v2CRLBuilder crl = new X509v2CRLBuilder(raizCrt.getSubject(), DESDE);
        crl.setNextUpdate(HASTA);
        crl.addCRLEntry(revocadoCrt.getSerialNumber(), DESDE, org.bouncycastle.asn1.x509.CRLReason.keyCompromise);
        X509CRLHolder crlFirmada = crl.build(firmante(raiz.getPrivate()));
        Files.write(dir.resolve("crl.der"), crlFirmada.getEncoded());

        KeyPair bcr = par();
        guardar(dir, "bcr", ca("CN=BCR Evidencia,O=BCR,C=CO", bcr, "CN=BCR Evidencia,O=BCR,C=CO", bcr.getPrivate()), bcr.getPrivate());

        System.out.println("PKI de prueba en " + dir.toAbsolutePath());
    }

    private static KeyPair par() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        return g.generateKeyPair();
    }

    private static ContentSigner firmante(PrivateKey llave) throws Exception {
        return new JcaContentSignerBuilder("SHA256withECDSA").build(llave);
    }

    private static X509CertificateHolder ca(String sujeto, KeyPair par, String emisor, PrivateKey llaveEmisor) throws Exception {
        var b = new JcaX509v3CertificateBuilder(new X500Name(emisor), BigInteger.valueOf(serial++), DESDE, HASTA,
                new X500Name(sujeto), par.getPublic());
        b.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        b.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign | KeyUsage.digitalSignature));
        return b.build(firmante(llaveEmisor));
    }

    private static X509CertificateHolder hoja(String sujeto, KeyPair par, X500Name emisor, PrivateKey llaveEmisor) throws Exception {
        var b = new JcaX509v3CertificateBuilder(emisor, BigInteger.valueOf(serial++), DESDE, HASTA,
                new X500Name(sujeto), par.getPublic());
        b.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        b.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));
        return b.build(firmante(llaveEmisor));
    }

    private static void guardar(Path dir, String nombre, X509CertificateHolder crt, PrivateKey llave) throws IOException {
        Files.writeString(dir.resolve(nombre + ".crt"), pem("CERTIFICATE", crt.getEncoded()), StandardCharsets.US_ASCII);
        Files.writeString(dir.resolve(nombre + ".key"), pem("PRIVATE KEY", llave.getEncoded()), StandardCharsets.US_ASCII);
    }

    private static String pem(String tipo, byte[] der) {
        return "-----BEGIN " + tipo + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
                + "\n-----END " + tipo + "-----\n";
    }
}
