package Seguridad;

import conexion.ConexionBD;

import java.io.FileOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.security.cert.X509Certificate;

import java.math.BigInteger;
import java.security.KeyStore;


public class GeneradorCertificadoDigital {


    public static void generar(){


        try {


            Security.addProvider(
            new BouncyCastleProvider()
            );


            KeyPairGenerator keyGen =
            KeyPairGenerator.getInstance(
                    "RSA"
            );


            keyGen.initialize(2048);


            KeyPair keyPair =
            keyGen.generateKeyPair();



            X500Name nombre =
            new X500Name(
            "CN=Sistema Certificados Tributarios"
            );



            Date inicio =
            new Date();



            Date fin =
            new Date(
            System.currentTimeMillis()
            + 365L * 24 * 60 * 60 * 1000
            );



            BigInteger serial =
            BigInteger.valueOf(
            System.currentTimeMillis()
            );



            JcaX509v3CertificateBuilder builder =
            new JcaX509v3CertificateBuilder(

            nombre,

            serial,

            inicio,

            fin,

            nombre,

            keyPair.getPublic()

            );



            ContentSigner signer =
            new JcaContentSignerBuilder(
            "SHA256withRSA"
            )
            .build(
            keyPair.getPrivate()
            );



            X509Certificate certificado =
            new JcaX509CertificateConverter()
            .getCertificate(
            builder.build(signer)
            );



            KeyStore keyStore =
            KeyStore.getInstance(
            "PKCS12"
            );



            keyStore.load(
            null,
            null
            );



            // Clave del certificado .p12: viene de la configuración, nunca del código
            char[] claveP12 =
            ConexionBD.obtener("firma.p12.password").toCharArray();


            keyStore.setKeyEntry(

            "certificado",

            keyPair.getPrivate(),

            claveP12,

            new X509Certificate[]{
            certificado
            }

            );



            FileOutputStream salida =
            new FileOutputStream(
            "Seguridad/certificado.p12"
            );



            keyStore.store(
            salida,
            claveP12
            );


            salida.close();



            System.out.println(
            "Certificado digital generado"
            );



        }catch(Exception e){


            System.out.println(
            "Error generando certificado: "
            + e.getMessage()
            );


        }


    }


}
