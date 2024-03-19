package pt.ulisboa.tecnico.hdsledger.security;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Random;

public class CryptoUtils {

    public static int generateNonce() {
        Random random = new Random();
        return random.nextInt(Integer.MAX_VALUE);
    }

    public static boolean verifySignature(String message, String signature, PublicKey publicKey) {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(message.getBytes());
            byte[] signatureBytes = Base64.getDecoder().decode(signature);
            return sig.verify(signatureBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String signMessage(String message, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(message.getBytes());
            byte[] signatureBytes = signature.sign();
            return Base64.getEncoder().encodeToString(signatureBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static byte[] readFile(String path) throws FileNotFoundException, IOException {
        FileInputStream fis = new FileInputStream(path);
        byte[] content = new byte[fis.available()];
        fis.read(content);
        fis.close();
        return content;
    }

    public static PrivateKey getPrivateKey(String file_path) {
        try {
            byte[] keyBytes = readFile(file_path);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
            return privateKey;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static PublicKey getPublicKey(String file_path) {
        try {
            byte[] keyBytes = readFile(file_path);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(keySpec);
            return publicKey;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Key readSymKey(String file_path){
        try{
            byte[] encoded = readFile(file_path);
            SecretKeySpec keySpec = new SecretKeySpec(encoded, "AES");
            return keySpec;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String encryptMessage(String message, Key key) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(message.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String decryptMessage(String message, Key key) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(message));
            return new String(decrypted);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static KeyPair createKeyPair(int keySize, String publicKeyPath, String privateKeyPath) throws NoSuchAlgorithmException, IOException {
        // Generate the key pair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(keySize);
        KeyPair keyPair = keyGen.generateKeyPair();

        // Get public and private keys
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();

        // Write public key to file
        try {
            Files.write(Paths.get(publicKeyPath), publicKey.getEncoded());
        } catch (IOException e) {
            System.err.println("ERROR: Unable to write public key to file.");
            throw e;
        }

        // Write private key to file
        try {
            Files.write(Paths.get(privateKeyPath), privateKey.getEncoded());
        } catch (IOException e) {
            System.err.println("ERROR: Unable to write private key to file.");
            throw e;
        }

        return keyPair;
    }
}