package com.sonicredux.reborn;

import android.content.Context;
import com.android.apksig.ApkSigner;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collections;

public final class RebornSigner {
    private RebornSigner() {}

    public static void sign(Context context, File input, File output) throws Exception {
        byte[] keyBytes = readRaw(context, R.raw.reborn_key);
        byte[] certBytes = readRaw(context, R.raw.reborn_cert);
        PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        X509Certificate certificate;
        try (InputStream in = new java.io.ByteArrayInputStream(certBytes)) {
            certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }

        ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder(
                "REBORN", privateKey, Collections.singletonList(certificate)).build();
        ApkSigner.Builder builder = new ApkSigner.Builder(Collections.singletonList(signerConfig));
        builder.setInputApk(input);
        builder.setOutputApk(output);
        builder.setCreatedBy("Sonic Redux Re-born");
        builder.setV1SigningEnabled(true);
        builder.setV2SigningEnabled(true);
        builder.setV3SigningEnabled(true);
        builder.setV4SigningEnabled(false);
        builder.build().sign();
    }

    private static byte[] readRaw(Context context, int id) throws Exception {
        try (InputStream in = context.getResources().openRawResource(id);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }
}
