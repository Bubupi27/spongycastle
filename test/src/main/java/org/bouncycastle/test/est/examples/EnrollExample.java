package org.bouncycastle.test.est.examples;


import java.io.File;
import java.io.FileInputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.est.ESTAuth;
import org.bouncycastle.est.ESTService;
import org.bouncycastle.est.EnrollmentResponse;
import org.bouncycastle.est.jcajce.JsseESTServiceBuilder;
import org.bouncycastle.est.jcajce.JcaHttpAuthBuilder;
import org.bouncycastle.est.jcajce.JcaJceUtils;
import org.bouncycastle.est.jcajce.SSLSocketFactoryCreatorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.bouncycastle.test.est.BCChannelBindingProvider;

/**
 * Enroll example exercises the enrollment of a certificate.
 * It will generate a CSR using the supplied common name.
 * As this is a PEM encoded trusted operation a trust anchor will need to be provided.
 */
public class EnrollExample
{
    public EnrollExample(String[] args)
        throws Exception
    {
        Security.addProvider(new BouncyCastleProvider());

        File trustAnchorFile = null;
        String serverRootUrl = null;
        String cn = null;
        File clientKeyStoreFile = null;
        char[] clientKeyStoreFilePassword = null;
        String keyStoreType = null;
        boolean httpAuth = false;
        String[] credentials = null;
        boolean reEnroll = false;
        String tlsVersion = "TLS";
        String tlsProvider = "SunJSSE";
        String tlsProviderClass = null;
        boolean noNameVerifier = false;
        boolean pop = false;
        int timeout = 0;
        try
        {
            for (int t = 0; t < args.length; t++)
            {
                String arg = args[t];
                if (arg.equals("-r"))
                {
                    reEnroll = true;
                }
                else if (arg.equals("-t"))
                {
                    trustAnchorFile = ExampleUtils.nextArgAsFile("Trust Anchor File", args, t);
                    t += 1;
                }
                else if (arg.equals("-u"))
                {
                    serverRootUrl = ExampleUtils.nextArgAsString("Server URL", args, t);
                    t += 1;
                }
                else if (arg.equals("-c"))
                {
                    cn = ExampleUtils.nextArgAsString("Common Name", args, t);
                    t += 1;
                }
                else if (arg.equals("--keyStore"))
                {
                    clientKeyStoreFile = ExampleUtils.nextArgAsFile("Client Key store", args, t);
                    t += 1;
                }
                else if (arg.equals("--keyStorePass"))
                {
                    clientKeyStoreFilePassword = ExampleUtils.nextArgAsString("Keystore password", args, t).toCharArray();
                    t += 1;
                }
                else if (arg.equals("--keyStoreType"))
                {
                    keyStoreType = ExampleUtils.nextArgAsString("Keystore type", args, t);
                    t += 1;
                }
                else if (arg.equals("--keyStoreType"))
                {
                    keyStoreType = ExampleUtils.nextArgAsString("Keystore type", args, t);
                    t += 1;
                }
                else if (arg.equals("--auth"))
                {
                    credentials = ExampleUtils.nextArgAsString("Keystore type", args, t).split(":");
                    httpAuth = true;
                    t += 1;
                }
                else if (arg.equals("--tls"))
                {
                    tlsVersion = ExampleUtils.nextArgAsString("TLS version", args, t);
                    t += 1;
                }
                else if (arg.equals("--tlsProvider"))
                {
                    tlsProvider = ExampleUtils.nextArgAsString("TLS Provider", args, t);
                    t += 1;
                    tlsProviderClass = ExampleUtils.nextArgAsString("TLS Provider Class", args, t);
                    t += 1;
                }
                else if (arg.equals("--pop"))
                {
                    pop = true;
                }
                else if (arg.equals("--to"))
                {
                    timeout = ExampleUtils.nextArgAsInteger("Timeout", args, t);
                    t += 1;
                }
                else if (arg.equals("--no-name-verifier"))
                {
                    noNameVerifier = true;
                }
                else
                {
                    System.out.println(arg);
                    printArgs();
                    System.exit(0);
                }
            }
        }
        catch (IllegalArgumentException ilex)
        {
            System.err.println(ilex.getMessage());
            System.exit(1);
        }

        if (args.length == 0)
        {
            printArgs();
            System.exit(0);
        }


        if (serverRootUrl == null)
        {
            System.err.println("Server url (-u) must be defined.");
            System.exit(-1);
        }

        if (cn == null)
        {
            System.err.println("Common Name (-c) must be defined.");
            System.exit(-1);
        }

        if (trustAnchorFile == null)
        {
            System.err.println("Trust Anchor (-t) must be defined.");
            System.exit(-1);
        }

        if (tlsProviderClass != null)
        {
            Security.addProvider((Provider)Class.forName(tlsProviderClass).newInstance());
        }


        //
        // Make a CSR here
        //

        ECGenParameterSpec ecGenSpec = new ECGenParameterSpec("prime256v1");
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("ECDSA", "BC");
        kpg.initialize(ecGenSpec, new SecureRandom());
        KeyPair keyPair = kpg.generateKeyPair();

        PKCS10CertificationRequestBuilder pkcs10Builder = new JcaPKCS10CertificationRequestBuilder(
            new X500Name("CN=" + cn),
            keyPair.getPublic());

        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WITHECDSA").setProvider("BC").build(keyPair.getPrivate());

        // Make est client builder
        //
        JsseESTServiceBuilder builder = null;

        SSLSocketFactoryCreatorBuilder sfcb = new SSLSocketFactoryCreatorBuilder(JcaJceUtils.getCertPathTrustManager(ExampleUtils.toTrustAnchor(ExampleUtils.readPemCertificate(trustAnchorFile)), null));
        sfcb.withTLSVersion(tlsVersion);
        sfcb.withProvider(tlsProvider);

        if (clientKeyStoreFile != null)
        {
            if (keyStoreType == null)
            {
                keyStoreType = "JKS";
            }
            KeyStore ks = KeyStore.getInstance(keyStoreType, "BC");
            ks.load(new FileInputStream(clientKeyStoreFile), clientKeyStoreFilePassword);
            sfcb.withKeyManagers(JcaJceUtils.createKeyManagerFactory("X509", null, ks, clientKeyStoreFilePassword).getKeyManagers());
        }

        JsseESTServiceBuilder est = new JsseESTServiceBuilder(serverRootUrl, sfcb.build());


//        JcaESTServiceBuilder est = new JcaESTServiceBuilder(serverRootUrl,
//            JcaESTServiceBuilder.createDefaultSocketFactory(
//                "TLS",
//                null,
//                null,
//                JcaESTServiceBuilder.getCertPathTLSAuthorizer(),
//                ExampleUtils.toTrustAnchor(ExampleUtils.readPemCertificate(trustAnchorFile)),
//                null)
//            );


        builder = new JsseESTServiceBuilder(serverRootUrl, sfcb.build());
        builder.withTimeout(timeout);

        if (noNameVerifier)
        {
            builder.withHostNameAuthorizer(null);
        }

        ESTAuth auth = null;

        if (httpAuth)
        {
            if (credentials.length == 3)
            {
                auth = new JcaHttpAuthBuilder(credentials[0], credentials[1], credentials[2].toCharArray())
                    .setNonceGenerator(new SecureRandom()).setProvider("BC").build();
            }
            else if (credentials.length == 2)
            {
                auth = new JcaHttpAuthBuilder(null, credentials[0], credentials[1].toCharArray())
                    .setNonceGenerator(new SecureRandom()).setProvider("BC").build();
            }
            else
            {
                System.err.println("Not enough credential for digest auth.");
                System.exit(0);
            }
        }


        est.withTimeout(timeout);


        EnrollmentResponse enrollmentResponse;

        //
        // The enrollment action can be deferred by the server.
        // In this example we will check if the response is actually completed.
        // If it is not then we must wait long enough for it to be completed.
        //
        do
        {
            if (pop)
            {
                est.withChannelBindingProvider(new BCChannelBindingProvider());
                ESTService estService = est.build();
                enrollmentResponse = estService.simpleEnrollPoP(reEnroll, pkcs10Builder, contentSigner, auth);
            }
            else
            {
                ESTService estService = est.build();
                PKCS10CertificationRequest csr = pkcs10Builder.build(contentSigner);
                enrollmentResponse = estService.simpleEnroll(reEnroll, csr, auth);
            }

            if (!enrollmentResponse.isCompleted())
            {
                long t = enrollmentResponse.getNotBefore() - System.currentTimeMillis();
                if (t < 0)
                {
                    continue;
                }
                t += 1000;
                Thread.sleep(t);
                continue;
            }
        }
        while (!enrollmentResponse.isCompleted());


        for (X509CertificateHolder holder : ESTService.storeToArray(enrollmentResponse.getStore()))
        {

            //
            // Limited the amount of information for the sake of the example.
            // The default too string prints everything and is hard to follow.
            //

            System.out.println("Subject: " + holder.getSubject());
            System.out.println("Issuer: " + holder.getIssuer());
            System.out.println("Serial Number: " + holder.getSerialNumber());
            System.out.println("Not Before: " + holder.getNotBefore());
            System.out.println("Not After: " + holder.getNotAfter());
            System.out.println();
            System.out.println(ExampleUtils.toJavaX509Certificate(holder));


        }

    }

    public static void main(String[] args)
        throws Exception
    {
        try
        {
            new EnrollExample(args);
        }
        catch (Exception ex)
        {
            System.out.println("\n\n-----------------");
            System.out.println(ex.getMessage());
            System.out.println("-----------------\n\n");
            throw ex;
        }
    }

    public void printArgs()
    {
        System.out.println("-r                                     Re-enroll");
        System.out.println("-t <file>                              Trust anchor file");
        System.out.println("-u <url>                               EST server url.");
        System.out.println("-c <common name>                       EST server url.");
        System.out.println("--keyStore <file>                      Optional Key Store.");
        System.out.println("--keyStorePass <password>              Optional Key Store password.");
        System.out.println("--keyStoreType <JKS>                   Optional Key Store type, defaults to JKS");
        System.out.println("--auth <realm:user:password>           Auth credentials, if real is not");
        System.out.println("--tls <version>                        Use this TLS version when creating socket factory, Eg TLSv1.2");
        System.out.println("--tlsProvider <provider> <class>       The JSSE Provider.");
        System.out.println("--pop                                  Turn on PoP");
        System.out.println("--to <milliseconds>                    Timeout in milliseconds.");
        System.out.println("--no-name-verifier                     No hostname verifier.");


    }
}
