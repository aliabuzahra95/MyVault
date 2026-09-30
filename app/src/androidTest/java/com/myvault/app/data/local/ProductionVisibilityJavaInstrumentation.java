package com.myvault.app.data.local;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Disposable Drive probe and read-only backup-state diagnostic; never modifies Vault data. */
public final class ProductionVisibilityJavaInstrumentation extends Instrumentation {
    private static final String SCOPE = "https://www.googleapis.com/auth/drive.file";
    private static final String ACCOUNT_ID = "14287589311515837545";
    private static final String ROOT_ID = "1aLk2-9E1SOLPYvLuOhG-Hu-Zo9dQ4Mku";
    private static final String ROOT_NAME = "MYVAULT-OAUTH-VISIBILITY-DISPOSABLE-a0cc73a0-6dc1-4878-83ad-baa7b699a0c1";
    private static final String ANDROID_FILE_ID = "1TJcmWNIm7fSsWOT79hStp4JWFc2pe1Ga";
    private static final String WEB_FILE_ID = "12qUGwOVUBRffPoz6CG5HU_ql8LlcT298";
    private static final String WEB_SHA256 = "9cc9d23893eed1c2e4e748c2a5906713711862558a88b11080353324f0a98c1d";
    private static final byte[] WEB_BYTES = "Disposable Web visibility proof: English العربية"
            .getBytes(StandardCharsets.UTF_8);
    private static final String API = "https://www.googleapis.com/drive/v3";
    private Bundle arguments;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        arguments = args == null ? new Bundle() : args;
        if (!"true".equals(arguments.getString("visibilityApproved"))) {
            finish(Activity.RESULT_CANCELED, result("Explicit disposable-test approval required"));
            return;
        }
        start();
    }

    @Override public void onStart() {
        super.onStart();
        try {
            if ("readiness-diagnostic".equals(arguments.getString("visibilityPhase"))) {
                finish(Activity.RESULT_OK, result(readinessDiagnostic()));
                return;
            }
            if ("graph-remote-diagnostic".equals(arguments.getString("visibilityPhase"))) {
                finish(Activity.RESULT_OK, result(graphRemoteDiagnostic()));
                return;
            }
            verify();
            finish(Activity.RESULT_OK, result("cleanup".equals(arguments.getString("visibilityPhase"))
                    ? "PASS: exact disposable Drive files and root deleted"
                    : "PASS: Android read and verified the Web-created disposable file"));
        } catch (Throwable failure) {
            finish(Activity.RESULT_CANCELED, result("FAIL: " + failure.getClass().getSimpleName()
                    + (failure instanceof NoClassDefFoundError ? " (" + failure.getMessage() + ")" : "")
                    + "; no credentials or response body logged"));
        }
    }

    private static Bundle result(String message) {
        Bundle result = new Bundle();
        result.putString("result", message);
        return result;
    }

    private String readinessDiagnostic() {
        Context context = getTargetContext();
        if (!"com.myvault.app".equals(context.getPackageName())) {
            throw new IllegalStateException("Wrong target package");
        }
        SharedPreferences signIn = context.getSharedPreferences("com.google.android.gms.signin", Context.MODE_PRIVATE);
        String selectedId = signIn.getString("defaultGoogleSignInAccount", null);
        String selectedJson = selectedId == null ? null : signIn.getString("googleSignInAccount:" + selectedId, null);
        if (selectedJson == null) throw new IllegalStateException("No selected Drive account");
        String account;
        try {
            account = new JSONObject(selectedJson).getString("email").trim().toLowerCase(Locale.ROOT);
        } catch (Exception error) {
            throw new IllegalStateException("Selected account unreadable");
        }
        File database = context.getDatabasePath("my_vault.db");
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(database.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY)) {
            long[] clock = values(db, "SELECT originEpoch, suppressionDepth, CASE WHEN settingsToken IS NULL THEN 0 ELSE 1 END FROM backup_journal_state WHERE id=1");
            long bindings = scalar(db, "SELECT count(*) FROM backup_graph_bindings WHERE accountScope=?", account);
            long applied = scalar(db, "SELECT count(*) FROM backup_graph_applied_states WHERE accountScope=?", account);
            long publications = scalar(db, "SELECT count(*) FROM backup_graph_publications WHERE accountScope=? AND status != 'COMPLETE'", account);
            long restores = scalar(db, "SELECT count(*) FROM backup_graph_restores WHERE status != 'COMPLETE'");
            long accountRestores = scalar(db, "SELECT count(*) FROM backup_graph_restores WHERE accountScope=? AND status != 'COMPLETE'", account);
            long invalidBinding = scalar(db, "SELECT count(*) FROM backup_graph_bindings b LEFT JOIN backup_tracking_accounts a ON a.accountScope=b.accountScope WHERE b.accountScope=? AND (a.trusted IS NULL OR a.trusted != 1 OR a.checkpointId != b.checkpointId OR a.headId != b.deltaHeadId OR a.manifestId != b.checkpointFileId OR a.manifestSha256 != b.checkpointSha256 OR b.originEpoch != (SELECT originEpoch FROM backup_journal_state WHERE id=1))", account);
            long[] proof = values(db, "SELECT COALESCE(a.trusted,0), CASE WHEN a.checkpointId=b.checkpointId THEN 1 ELSE 0 END, CASE WHEN a.headId=b.deltaHeadId THEN 1 ELSE 0 END, CASE WHEN a.manifestId=b.checkpointFileId THEN 1 ELSE 0 END, CASE WHEN a.manifestSha256=b.checkpointSha256 THEN 1 ELSE 0 END, b.originEpoch FROM backup_graph_bindings b LEFT JOIN backup_tracking_accounts a ON a.accountScope=b.accountScope WHERE b.accountScope=?", account);
            long mismatchedLineage = scalar(db, "SELECT count(*) FROM backup_graph_bindings b JOIN backup_graph_applied_states r ON b.accountScope=r.accountScope WHERE b.accountScope=? AND b.lineageId != r.lineageId", account);
            return "READ_ONLY_BACKUP_DIAGNOSTIC originEpoch=" + clock[0]
                    + " suppressionDepth=" + clock[1] + " settingsTokenPresent=" + clock[2]
                    + " bindings=" + bindings + " applied=" + applied
                    + " invalidBinding=" + invalidBinding + " mismatchedLineage=" + mismatchedLineage
                    + " trusted=" + proof[0] + " checkpointMatch=" + proof[1] + " deltaHeadMatch=" + proof[2]
                    + " checkpointObjectMatch=" + proof[3] + " checkpointHashMatch=" + proof[4]
                    + " bindingOriginEpoch=" + proof[5]
                    + " unfinishedPublications=" + publications + " unfinishedRestoresAll=" + restores
                    + " unfinishedRestoresAccount=" + accountRestores;
        }
    }

    private String graphRemoteDiagnostic() throws Exception {
        Context context = getTargetContext();
        SharedPreferences signIn = context.getSharedPreferences("com.google.android.gms.signin", Context.MODE_PRIVATE);
        String selectedId = signIn.getString("defaultGoogleSignInAccount", null);
        String selectedJson = selectedId == null ? null : signIn.getString("googleSignInAccount:" + selectedId, null);
        if (selectedJson == null) throw new IllegalStateException("No selected Drive account");
        String email = new JSONObject(selectedJson).getString("email");
        String accountScope = email.trim().toLowerCase(Locale.ROOT);
        String expectedAccountId;
        String commitFileId;
        String expectedHash;
        long expectedSize;
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(context.getDatabasePath("my_vault.db").getAbsolutePath(),
                null, SQLiteDatabase.OPEN_READONLY);
             Cursor cursor = db.rawQuery("SELECT driveAccountId,commitFileId,commitSha256,commitSize FROM backup_graph_bindings WHERE accountScope=?", new String[]{accountScope})) {
            if (!cursor.moveToFirst()) throw new IllegalStateException("No local graph binding");
            expectedAccountId = cursor.getString(0);
            commitFileId = cursor.getString(1);
            expectedHash = cursor.getString(2);
            expectedSize = cursor.getLong(3);
            if (cursor.moveToNext()) throw new IllegalStateException("Multiple local graph bindings");
        }
        Account googleAccount = null;
        for (Account candidate : AccountManager.get(context).getAccountsByType("com.google")) {
            if (email.equalsIgnoreCase(candidate.name)) googleAccount = candidate;
        }
        if (googleAccount == null) throw new IllegalStateException("Selected account unavailable");
        String token = AccountManager.get(context).getAuthToken(googleAccount, "oauth2:" + SCOPE,
                null, false, null, null).getResult().getString(AccountManager.KEY_AUTHTOKEN);
        if (token == null) throw new IllegalStateException("Drive authorization requires user action");
        JSONObject identity = new JSONObject(new String(get(token, API + "/about?fields=user(permissionId)", 8192), StandardCharsets.UTF_8));
        boolean accountMatches = expectedAccountId.equals(identity.getJSONObject("user").getString("permissionId"));
        if (!accountMatches) return "READ_ONLY_GRAPH_REMOTE accountMatches=false; no graph files inspected";
        String query = URLEncoder.encode("name = 'MyVault Backup Graph v1' and mimeType = 'application/vnd.google-apps.folder' and trashed = false", StandardCharsets.UTF_8);
        JSONObject listed = new JSONObject(new String(get(token, API + "/files?q=" + query
                + "&fields=nextPageToken,files(id,name)&pageSize=1000", 16384), StandardCharsets.UTF_8));
        if (listed.has("nextPageToken")) throw new IllegalStateException("Graph namespace listing incomplete");
        int rootCount = listed.getJSONArray("files").length();
        String commitProof;
        try {
            JSONObject metadata = new JSONObject(new String(get(token, API + "/files/" + commitFileId
                    + "?fields=id,size,trashed", 8192), StandardCharsets.UTF_8));
            if (metadata.optBoolean("trashed") || metadata.getLong("size") != expectedSize) {
                commitProof = "mismatched";
            } else {
                byte[] bytes = get(token, API + "/files/" + commitFileId + "?alt=media", 65536);
                commitProof = bytes.length == expectedSize && expectedHash.equals(sha256(bytes)) ? "verified" : "mismatched";
            }
        } catch (IllegalStateException error) {
            if (!error.getMessage().endsWith("HTTP 404")) throw error;
            commitProof = "missing";
        }
        return "READ_ONLY_GRAPH_REMOTE accountMatches=true namespaceRoots=" + rootCount
                + " savedCommit=" + commitProof;
    }

    private static long scalar(SQLiteDatabase db, String sql, String... args) {
        try (Cursor cursor = db.rawQuery(sql, args)) {
            if (!cursor.moveToFirst()) throw new IllegalStateException("Diagnostic query empty");
            return cursor.getLong(0);
        }
    }

    private static long[] values(SQLiteDatabase db, String sql, String... args) {
        try (Cursor cursor = db.rawQuery(sql, args)) {
            if (!cursor.moveToFirst()) throw new IllegalStateException("Journal state unavailable");
            long[] result = new long[cursor.getColumnCount()];
            for (int index = 0; index < result.length; index++) result[index] = cursor.getLong(index);
            return result;
        }
    }

    private void verify() throws Exception {
        String phase = arguments.getString("visibilityPhase");
        if (!"read-web".equals(phase) && !"cleanup".equals(phase))
            throw new IllegalArgumentException("Unsupported disposable test phase");
        Context context = getTargetContext();
        if (!"com.myvault.app".equals(context.getPackageName())) {
            throw new IllegalStateException("Wrong target package");
        }
        File stateFile = new File(new File(context.getFilesDir(), "disposable-oauth-visibility-test"),
                "production-drive-visibility.json");
        if (!stateFile.isFile() || stateFile.length() > 4096) {
            throw new IllegalStateException("Original Android test state unavailable");
        }
        byte[] stateBytes = readLimited(new java.io.FileInputStream(stateFile), 4096);
        JSONObject state = new JSONObject(new String(stateBytes, StandardCharsets.UTF_8));
        if (!ACCOUNT_ID.equals(state.getString("accountId")) || !ROOT_ID.equals(state.getString("rootId"))
                || !ROOT_NAME.equals(state.getString("rootName"))
                || !ANDROID_FILE_ID.equals(state.getString("androidFileId"))
                || !("ANDROID_VERIFIED".equals(state.getString("status"))
                    || "BIDIRECTIONAL_VERIFIED".equals(state.getString("status")))) {
            throw new IllegalStateException("Original Android test proof mismatch");
        }

        SharedPreferences signIn = context.getSharedPreferences("com.google.android.gms.signin", Context.MODE_PRIVATE);
        String selectedId = signIn.getString("defaultGoogleSignInAccount", null);
        String selectedJson = selectedId == null ? null : signIn.getString("googleSignInAccount:" + selectedId, null);
        if (selectedJson == null) throw new IllegalStateException("Existing MyVault Drive connection unavailable");
        JSONObject selected = new JSONObject(selectedJson);
        String email = selected.getString("email");
        boolean hasScope = false;
        for (int index = 0; index < selected.getJSONArray("grantedScopes").length(); index++) {
            if (SCOPE.equals(selected.getJSONArray("grantedScopes").getString(index))) hasScope = true;
        }
        if (!hasScope) throw new IllegalStateException("Existing Drive permission unavailable");
        Account account = null;
        AccountManager manager = AccountManager.get(context);
        for (Account candidate : manager.getAccountsByType("com.google")) {
            if (email.equalsIgnoreCase(candidate.name)) account = candidate;
        }
        if (account == null) throw new IllegalStateException("Selected Google account unavailable on device");
        Bundle tokenResult = manager.getAuthToken(account, "oauth2:" + SCOPE, null, false, null, null).getResult();
        String token = tokenResult.getString(AccountManager.KEY_AUTHTOKEN);
        if (token == null) throw new IllegalStateException("Existing Drive authorization needs user action");
        JSONObject identity = new JSONObject(new String(get(token, API + "/about?fields=user(permissionId)", 8192),
                StandardCharsets.UTF_8));
        if (!ACCOUNT_ID.equals(identity.getJSONObject("user").getString("permissionId"))) {
            throw new IllegalStateException("Drive account changed");
        }
        if ("cleanup".equals(phase)) {
            cleanup(token, stateFile, state);
            return;
        }

        JSONObject file = new JSONObject(new String(get(token, API + "/files/" + WEB_FILE_ID
                + "?fields=id,name,parents,size,trashed", 8192), StandardCharsets.UTF_8));
        if (!WEB_FILE_ID.equals(file.getString("id")) || !"web-proof.txt".equals(file.getString("name"))
                || file.optBoolean("trashed") || !ROOT_ID.equals(file.getJSONArray("parents").getString(0))
                || file.getLong("size") != WEB_BYTES.length) {
            throw new IllegalStateException("Web test file identity mismatch");
        }
        byte[] actual = get(token, API + "/files/" + WEB_FILE_ID + "?alt=media", 512);
        if (!MessageDigest.isEqual(WEB_BYTES, actual) || !WEB_SHA256.equals(sha256(actual))) {
            throw new IllegalStateException("Web test bytes mismatch");
        }

        state.put("webFileId", WEB_FILE_ID).put("webSha256", WEB_SHA256)
                .put("status", "BIDIRECTIONAL_VERIFIED");
        try (FileOutputStream output = new FileOutputStream(stateFile)) {
            output.write(state.toString().getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
    }

    private static JSONArray children(String token) throws Exception {
        String query = URLEncoder.encode("'" + ROOT_ID + "' in parents and trashed = false", StandardCharsets.UTF_8);
        String fields = URLEncoder.encode("nextPageToken,files(id,name,parents)", StandardCharsets.UTF_8);
        JSONObject page = new JSONObject(new String(get(token, API + "/files?q=" + query
                + "&fields=" + fields + "&pageSize=1000", 16384), StandardCharsets.UTF_8));
        if (page.has("nextPageToken")) throw new IllegalStateException("Unexpected disposable folder page");
        return page.getJSONArray("files");
    }

    private static void cleanup(String token, File stateFile, JSONObject state) throws Exception {
        if (!"BIDIRECTIONAL_VERIFIED".equals(state.getString("status"))
                || !WEB_FILE_ID.equals(state.getString("webFileId"))
                || !WEB_SHA256.equals(state.getString("webSha256"))) {
            throw new IllegalStateException("Two-way proof unavailable; cleanup blocked");
        }
        JSONObject root = new JSONObject(new String(get(token, API + "/files/" + ROOT_ID
                + "?fields=id,name,mimeType,trashed", 8192), StandardCharsets.UTF_8));
        if (!ROOT_ID.equals(root.getString("id")) || !ROOT_NAME.equals(root.getString("name"))
                || !"application/vnd.google-apps.folder".equals(root.getString("mimeType"))
                || root.optBoolean("trashed")) throw new IllegalStateException("Disposable root mismatch");

        JSONArray listed = children(token);
        for (int index = 0; index < listed.length(); index++) {
            JSONObject file = listed.getJSONObject(index);
            String id = file.getString("id");
            String expectedName = ANDROID_FILE_ID.equals(id) ? "android-proof.txt"
                    : WEB_FILE_ID.equals(id) ? "web-proof.txt" : null;
            if (expectedName == null || !expectedName.equals(file.getString("name"))
                    || !ROOT_ID.equals(file.getJSONArray("parents").getString(0))) {
                throw new IllegalStateException("Unexpected disposable child; cleanup blocked");
            }
        }
        for (int index = 0; index < listed.length(); index++) {
            delete(token, listed.getJSONObject(index).getString("id"));
        }
        if (children(token).length() != 0) throw new IllegalStateException("Disposable children remain");
        delete(token, ROOT_ID);
        state.put("status", "CLEANED");
        try (FileOutputStream output = new FileOutputStream(stateFile)) {
            output.write(state.toString().getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
    }

    private static void delete(String token, String id) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(API + "/files/" + id).openConnection();
        try {
            connection.setRequestMethod("DELETE");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            if (connection.getResponseCode() != 204) {
                throw new IllegalStateException("Disposable delete did not complete");
            }
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] get(String token, String path, int limit) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(path).openConnection();
        try {
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            int status = connection.getResponseCode();
            if (status != 200) throw new IllegalStateException("Disposable Drive read returned HTTP " + status);
            return readLimited(connection.getInputStream(), limit);
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readLimited(InputStream input, int limit) throws Exception {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                if (output.size() + count > limit) throw new IllegalStateException("Disposable response too large");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return hex.toString();
    }
}
