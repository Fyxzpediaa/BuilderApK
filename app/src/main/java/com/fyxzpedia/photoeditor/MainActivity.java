package com.fyxzpedia.photoeditor;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 41;
    private Bitmap originalBitmap;
    private Bitmap editedBitmap;
    private ImageView preview;
    private TextView emptyState;
    private SeekBar brightnessBar, contrastBar;
    private TextView brightnessValue, contrastValue;
    private int brightness = 0;
    private int contrast = 100;
    private int rotation = 0;
    private LinearLayout root;
    private int panel = Color.rgb(20, 23, 33);
    private int accent = Color.rgb(155, 140, 255);

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(11, 13, 19));
        getWindow().setNavigationBarColor(Color.rgb(11, 13, 19));
        buildUi();
    }

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private GradientDrawableBuilder rounded(int color, int radius) {
        return new GradientDrawableBuilder(color, dp(radius));
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(10), dp(18), dp(12));
        root.setBackgroundColor(Color.rgb(11, 13, 19));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView brand = text("PHOTO STUDIO", 18, Color.WHITE, true);
        TextView version = text("  •  v2.0", 12, Color.rgb(155, 162, 181), false);
        header.addView(brand);
        header.addView(version);
        View spacer = new View(this);
        header.addView(spacer, new LinearLayout.LayoutParams(0, dp(1), 1));
        Button importButton = button("＋  Open", accent, Color.rgb(15, 15, 25));
        importButton.setOnClickListener(v -> pickImage());
        header.addView(importButton);
        Button remoteButton = button("⌁  Remote", panel, Color.WHITE);
        LinearLayout.LayoutParams remoteLp = new LinearLayout.LayoutParams(-2, dp(44));
        remoteLp.leftMargin = dp(6);
        remoteButton.setOnClickListener(v -> startActivity(new Intent(this, RemoteActivity.class)));
        header.addView(remoteButton, remoteLp);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(54)));

        TextView subtitle = text("A clean, focused workspace for your photos.", 12, Color.rgb(148, 154, 173), false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.bottomMargin = dp(14);
        root.addView(subtitle, subLp);

        FrameLayout canvas = new FrameLayout(this);
        canvas.setBackground(rounded(Color.rgb(18, 21, 30), 22).drawable());
        canvas.setClipToOutline(true);
        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setAdjustViewBounds(false);
        canvas.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        emptyState = text("Your photo appears here\n\nOpen an image to begin editing", 15, Color.rgb(160, 166, 184), false);
        emptyState.setGravity(Gravity.CENTER);
        canvas.addView(emptyState, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams canvasLp = new LinearLayout.LayoutParams(-1, 0, 1);
        canvasLp.bottomMargin = dp(14);
        root.addView(canvas, canvasLp);

        LinearLayout toolHeader = new LinearLayout(this);
        toolHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView toolsTitle = text("Adjustments", 16, Color.WHITE, true);
        toolHeader.addView(toolsTitle);
        View s2 = new View(this);
        toolHeader.addView(s2, new LinearLayout.LayoutParams(0, 1, 1));
        TextView reset = text("Reset", 13, accent, true);
        reset.setPadding(dp(12), dp(8), dp(4), dp(8));
        reset.setOnClickListener(v -> resetAdjustments());
        toolHeader.addView(reset);
        root.addView(toolHeader);

        LinearLayout slidersCard = new LinearLayout(this);
        slidersCard.setOrientation(LinearLayout.VERTICAL);
        slidersCard.setPadding(dp(14), dp(8), dp(14), dp(10));
        slidersCard.setBackground(rounded(panel, 18).drawable());
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.topMargin = dp(8);
        root.addView(slidersCard, cardLp);

        LinearLayout brightnessRow = new LinearLayout(this);
        brightnessRow.setGravity(Gravity.CENTER_VERTICAL);
        brightnessRow.addView(text("Brightness", 13, Color.rgb(225, 228, 239), true));
        View bs = new View(this);
        brightnessRow.addView(bs, new LinearLayout.LayoutParams(0, 1, 1));
        brightnessValue = text("0", 12, Color.rgb(170, 176, 195), false);
        brightnessRow.addView(brightnessValue);
        slidersCard.addView(brightnessRow);
        brightnessBar = new SeekBar(this);
        brightnessBar.setMax(100);
        brightnessBar.setProgress(50);
        brightnessBar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        brightnessBar.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        slidersCard.addView(brightnessBar, new LinearLayout.LayoutParams(-1, dp(32)));

        LinearLayout contrastRow = new LinearLayout(this);
        contrastRow.setGravity(Gravity.CENTER_VERTICAL);
        contrastRow.addView(text("Contrast", 13, Color.rgb(225, 228, 239), true));
        View cs = new View(this);
        contrastRow.addView(cs, new LinearLayout.LayoutParams(0, 1, 1));
        contrastValue = text("100%", 12, Color.rgb(170, 176, 195), false);
        contrastRow.addView(contrastValue);
        slidersCard.addView(contrastRow);
        contrastBar = new SeekBar(this);
        contrastBar.setMax(200);
        contrastBar.setProgress(100);
        contrastBar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        contrastBar.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        slidersCard.addView(contrastBar, new LinearLayout.LayoutParams(-1, dp(32)));

        brightnessBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                brightness = p - 50; brightnessValue.setText(String.valueOf(brightness));
                renderEdits();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        contrastBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                contrast = p; contrastValue.setText(p + "%");
                renderEdits();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, dp(52));
        actionsLp.topMargin = dp(10);
        root.addView(actions, actionsLp);
        Button rotate = button("↻  Rotate", panel, Color.WHITE);
        rotate.setOnClickListener(v -> rotateImage());
        actions.addView(rotate, new LinearLayout.LayoutParams(0, -1, 1));
        View gap = new View(this);
        actions.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
        Button save = button("Export image  ↗", accent, Color.rgb(15, 15, 25));
        save.setOnClickListener(v -> saveImage());
        actions.addView(save, new LinearLayout.LayoutParams(0, -1, 1.35f));

        setContentView(root);
    }

    private Button button(String label, int bg, int fg) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(fg);
        b.setTypeface(null, android.graphics.Typeface.BOLD);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setBackground(rounded(bg, 14).drawable());
        return b;
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, PICK_IMAGE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                originalBitmap = BitmapFactory.decodeStream(in);
                if (originalBitmap == null) throw new Exception("Unsupported image");
                rotation = 0; brightness = 0; contrast = 100;
                brightnessBar.setProgress(50); contrastBar.setProgress(100);
                emptyState.setVisibility(View.GONE);
                renderEdits();
            } catch (Exception e) {
                Toast.makeText(this, "Unable to open this image", Toast.LENGTH_LONG).show();
            }
        }
    }

    private Bitmap rotate(Bitmap source, int degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private void rotateImage() {
        if (originalBitmap == null) { toastOpenFirst(); return; }
        rotation = (rotation + 90) % 360;
        renderEdits();
    }

    private void renderEdits() {
        if (originalBitmap == null) return;
        Bitmap rotated = rotate(originalBitmap, rotation);
        int w = rotated.getWidth(), h = rotated.getHeight();
        // Bound processing size to keep previews responsive on mid-range phones.
        int maxDim = 1800;
        if (Math.max(w, h) > maxDim) {
            float scale = maxDim / (float)Math.max(w, h);
            Bitmap scaled = Bitmap.createScaledBitmap(rotated, Math.round(w * scale), Math.round(h * scale), true);
            if (rotated != originalBitmap) rotated.recycle();
            rotated = scaled; w = rotated.getWidth(); h = rotated.getHeight();
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[w * h];
        rotated.getPixels(pixels, 0, w, 0, 0, w, h);
        float c = contrast / 100f;
        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
            r = clamp((int)((r - 128) * c + 128 + brightness * 2.0f));
            g = clamp((int)((g - 128) * c + 128 + brightness * 2.0f));
            b = clamp((int)((b - 128) * c + 128 + brightness * 2.0f));
            pixels[i] = Color.argb(Color.alpha(color), r, g, b);
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h);
        if (editedBitmap != null && editedBitmap != originalBitmap && !editedBitmap.isRecycled()) editedBitmap.recycle();
        editedBitmap = out;
        preview.setImageBitmap(editedBitmap);
        if (rotated != originalBitmap && !rotated.isRecycled()) rotated.recycle();
    }

    private int clamp(int n) { return Math.max(0, Math.min(255, n)); }

    private void resetAdjustments() {
        brightness = 0; contrast = 100; rotation = 0;
        brightnessBar.setProgress(50); contrastBar.setProgress(100);
        renderEdits();
    }

    private void saveImage() {
        if (editedBitmap == null) { toastOpenFirst(); return; }
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "PhotoStudio_" + stamp + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PhotoStudio");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = null;
        try {
            uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new Exception("Unable to create output");
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null || !editedBitmap.compress(Bitmap.CompressFormat.JPEG, 94, out))
                    throw new Exception("Unable to write image");
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(uri, done, null, null);
            Toast.makeText(this, "Saved to Pictures/PhotoStudio", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            if (uri != null) getContentResolver().delete(uri, null, null);
            Toast.makeText(this, "Export failed. Please try again.", Toast.LENGTH_LONG).show();
        }
    }

    private void toastOpenFirst() {
        Toast.makeText(this, "Open a photo first", Toast.LENGTH_SHORT).show();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (editedBitmap != null && editedBitmap != originalBitmap && !editedBitmap.isRecycled()) editedBitmap.recycle();
    }

    private static class GradientDrawableBuilder {
        private final android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        GradientDrawableBuilder(int color, float radius) {
            d.setColor(color);
            d.setCornerRadius(radius);
        }
        android.graphics.drawable.GradientDrawable drawable() { return d; }
    }
}
