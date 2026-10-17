package dev.yashasvm.mobie;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Runs in the test APK process, which intentionally has no dependency on the target APK classes. */
public final class AttachmentFixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) {
        if ("mime-denied".equals(uri.getLastPathSegment())) {
            throw new SecurityException("Fixture provider refused MIME access");
        }
        return "text/markdown";
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if ("query-failed".equals(uri.getLastPathSegment())) {
            throw new IllegalStateException("Fixture provider unavailable");
        }
        MatrixCursor cursor = new MatrixCursor(new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE });
        cursor.addRow(new Object[] { "notes.md", null });
        return cursor;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = new File(getContext().getCacheDir(), "attachment-reader-fixture-" + uri.getLastPathSegment());
        byte[] bytes = "binary".equals(uri.getLastPathSegment())
            ? new byte[] { 65, 0, 66 }
            : "Local UTF-8 notes: café".getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(bytes);
        } catch (IOException error) {
            throw new FileNotFoundException(error.getMessage());
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
