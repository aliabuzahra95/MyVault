import java.nio.file.*;
import java.util.*;

class SignRelease {
    public static void main(String[] args) throws Exception {
        Properties p = new Properties();
        try (var input = Files.newInputStream(Path.of(System.getProperty("user.home"), ".gradle", "gradle.properties"))) {
            p.load(input);
        }
        String store = p.getProperty("MYVAULT_RELEASE_STORE_FILE");
        if (System.getenv("MYVAULT_EXISTING_KEY_PASSWORD") != null) {
            p.setProperty("MYVAULT_RELEASE_STORE_PASSWORD", System.getenv("MYVAULT_EXISTING_KEY_PASSWORD"));
            p.setProperty("MYVAULT_RELEASE_KEY_PASSWORD", System.getenv("MYVAULT_EXISTING_KEY_PASSWORD"));
        }
        if (!Files.isRegularFile(Path.of(store))) throw new IllegalStateException("Configured keystore missing");
        String alias = p.getProperty("MYVAULT_RELEASE_KEY_ALIAS");
        String expected = "5d33f907db32d404352fc160fbd0e7b27d648a50f97c648536211d68cf5e80a3";
        boolean matched = false;
        for (String candidate : List.of(store, "/Users/aliah/Desktop/Current Projects/MyVaultKey.jks", "/Users/aliah/Desktop/Completed Projects/MyVaultKey.jks")) {
            if (!Files.isRegularFile(Path.of(candidate))) continue;
            java.security.KeyStore keys;
            try { keys = java.security.KeyStore.getInstance(Path.of(candidate).toFile(), p.getProperty("MYVAULT_RELEASE_STORE_PASSWORD").toCharArray()); }
            catch (java.io.IOException error) { continue; }
            for (var names = keys.aliases(); names.hasMoreElements();) {
                String name = names.nextElement();
                String fingerprint = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(keys.getCertificate(name).getEncoded()));
                if (fingerprint.equals(expected) && keys.getKey(name, p.getProperty("MYVAULT_RELEASE_KEY_PASSWORD").toCharArray()) != null) {
                    store = candidate;
                    alias = name;
                    matched = true;
                    break;
                }
            }
            if (matched) break;
        }
        if (!matched) throw new IllegalStateException("No matching production signing key located");
        if (args.length == 0) {
            var keys = java.security.KeyStore.getInstance(Path.of(store).toFile(), p.getProperty("MYVAULT_RELEASE_STORE_PASSWORD").toCharArray());
            if (keys.getKey(alias, p.getProperty("MYVAULT_RELEASE_KEY_PASSWORD").toCharArray()) == null) throw new IllegalStateException("Signing key missing");
            var digest = java.security.MessageDigest.getInstance("SHA-256").digest(keys.getCertificate(alias).getEncoded());
            String fingerprint = HexFormat.of().formatHex(digest);
            if (!fingerprint.equals("5d33f907db32d404352fc160fbd0e7b27d648a50f97c648536211d68cf5e80a3")) throw new IllegalStateException("Certificate differs from previous APK");
            System.out.println("Signing key unlocked; certificate matches previous APK: " + fingerprint);
            return;
        }
        var process = new ProcessBuilder(
            "/Users/aliah/Library/Android/sdk/build-tools/36.1.0/apksigner", "sign",
            "--ks", store, "--ks-key-alias", alias,
            "--ks-pass", "env:MYVAULT_SIGN_STORE", "--key-pass", "env:MYVAULT_SIGN_KEY",
            "--v4-signing-enabled", "false", "--out", args[1], args[0]
        );
        process.environment().put("MYVAULT_SIGN_STORE", p.getProperty("MYVAULT_RELEASE_STORE_PASSWORD"));
        process.environment().put("MYVAULT_SIGN_KEY", p.getProperty("MYVAULT_RELEASE_KEY_PASSWORD"));
        process.inheritIO();
        if (process.start().waitFor() != 0) throw new IllegalStateException("Signing failed");
    }
}
