package com.dkonak.dartat;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class CropImageActivity extends Activity {
    private CropView cropView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Uri sourceUri = getIntent().getData();
        Bitmap sourceBitmap = decodeBitmap(sourceUri);
        if (sourceBitmap == null) {
            Toast.makeText(this, "Resim acilamadi.", Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        cropView = new CropView(this, sourceBitmap);
        root.addView(cropView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(dp(16), dp(10), dp(16), dp(16));
        actions.setBackgroundColor(Color.argb(205, 250, 246, 237));

        Button cancelButton = new Button(this);
        cancelButton.setText("Iptal");
        cancelButton.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        Button saveButton = new Button(this);
        saveButton.setText("Kaydet");
        saveButton.setOnClickListener(v -> saveCroppedImage());

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        actions.addView(cancelButton, buttonParams);
        actions.addView(saveButton, buttonParams);

        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        actionParams.gravity = android.view.Gravity.BOTTOM;
        root.addView(actions, actionParams);

        setContentView(root);
    }

    private Bitmap decodeBitmap(Uri sourceUri) {
        if (sourceUri == null) {
            return null;
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream boundsStream = getContentResolver().openInputStream(sourceUri)) {
            BitmapFactory.decodeStream(boundsStream, null, bounds);
        } catch (Exception ignored) {
            return null;
        }

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, 2048);
        try (InputStream imageStream = getContentResolver().openInputStream(sourceUri)) {
            return BitmapFactory.decodeStream(imageStream, null, decodeOptions);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void saveCroppedImage() {
        Bitmap croppedBitmap = cropView.createCroppedBitmap(1024);
        File targetFile = new File(getFilesDir(), "dartat_custom_target.png");
        try (FileOutputStream outputStream = new FileOutputStream(targetFile)) {
            croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            croppedBitmap.recycle();
            Intent result = new Intent();
            result.setData(Uri.fromFile(targetFile));
            setResult(RESULT_OK, result);
            finish();
        } catch (Exception ignored) {
            Toast.makeText(this, "Resim kaydedilemedi.", Toast.LENGTH_SHORT).show();
        }
    }

    private int calculateSampleSize(int width, int height, int maxSize) {
        int sampleSize = 1;
        int largestSide = Math.max(width, height);
        while (largestSide / sampleSize > maxSize) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class CropView extends View {
        private final Bitmap bitmap;
        private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF cropRect = new RectF();
        private final RectF imageRect = new RectF();

        private float scale = 1f;
        private float offsetX;
        private float offsetY;
        private float lastX;
        private float lastY;
        private float lastDistance;
        private boolean initialized;

        CropView(Activity activity, Bitmap bitmap) {
            super(activity);
            this.bitmap = bitmap;
            overlayPaint.setColor(Color.argb(150, 0, 0, 0));
            borderPaint.setColor(Color.WHITE);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(activity.getResources().getDisplayMetrics().density * 2.5f);
        }

        @Override
        protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            float actionSpace = getResources().getDisplayMetrics().density * 96f;
            float size = Math.min(width * 0.82f, Math.max(1f, height - actionSpace) * 0.62f);
            cropRect.set((width - size) / 2f, (height - actionSpace - size) / 2f, (width + size) / 2f, (height - actionSpace + size) / 2f);
            if (!initialized) {
                scale = Math.max(cropRect.width() / bitmap.getWidth(), cropRect.height() / bitmap.getHeight());
                offsetX = cropRect.centerX() - (bitmap.getWidth() * scale) / 2f;
                offsetY = cropRect.centerY() - (bitmap.getHeight() * scale) / 2f;
                initialized = true;
            }
            constrainImage();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            updateImageRect();
            canvas.drawColor(Color.rgb(246, 241, 231));
            canvas.drawBitmap(bitmap, null, imageRect, imagePaint);

            canvas.drawRect(0f, 0f, getWidth(), cropRect.top, overlayPaint);
            canvas.drawRect(0f, cropRect.bottom, getWidth(), getHeight(), overlayPaint);
            canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, overlayPaint);
            canvas.drawRect(cropRect.right, cropRect.top, getWidth(), cropRect.bottom, overlayPaint);
            canvas.drawRect(cropRect, borderPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getPointerCount() >= 2) {
                handlePinch(event);
                return true;
            }

            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                lastX = event.getX();
                lastY = event.getY();
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                offsetX += event.getX() - lastX;
                offsetY += event.getY() - lastY;
                lastX = event.getX();
                lastY = event.getY();
                constrainImage();
                invalidate();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                lastDistance = 0f;
            }
            return true;
        }

        private void handlePinch(MotionEvent event) {
            float dx = event.getX(0) - event.getX(1);
            float dy = event.getY(0) - event.getY(1);
            float distance = (float) Math.sqrt((dx * dx) + (dy * dy));
            float focusX = (event.getX(0) + event.getX(1)) / 2f;
            float focusY = (event.getY(0) + event.getY(1)) / 2f;
            if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN || lastDistance <= 0f) {
                lastDistance = distance;
                return;
            }

            float oldScale = scale;
            scale = Math.max(getMinimumScale(), Math.min(scale * (distance / lastDistance), getMinimumScale() * 5f));
            float scaleChange = scale / oldScale;
            offsetX = focusX - ((focusX - offsetX) * scaleChange);
            offsetY = focusY - ((focusY - offsetY) * scaleChange);
            lastDistance = distance;
            constrainImage();
            invalidate();
        }

        private Bitmap createCroppedBitmap(int outputSize) {
            Bitmap output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            canvas.drawColor(Color.WHITE);
            float outputScale = outputSize / cropRect.width();
            canvas.scale(outputScale, outputScale);
            canvas.translate(-cropRect.left, -cropRect.top);
            updateImageRect();
            canvas.drawBitmap(bitmap, null, imageRect, imagePaint);
            return output;
        }

        private void constrainImage() {
            float minimumScale = getMinimumScale();
            scale = Math.max(scale, minimumScale);
            updateImageRect();
            if (imageRect.left > cropRect.left) {
                offsetX -= imageRect.left - cropRect.left;
            }
            if (imageRect.top > cropRect.top) {
                offsetY -= imageRect.top - cropRect.top;
            }
            if (imageRect.right < cropRect.right) {
                offsetX += cropRect.right - imageRect.right;
            }
            if (imageRect.bottom < cropRect.bottom) {
                offsetY += cropRect.bottom - imageRect.bottom;
            }
            updateImageRect();
        }

        private float getMinimumScale() {
            if (cropRect.width() <= 0f || cropRect.height() <= 0f) {
                return 1f;
            }
            return Math.max(cropRect.width() / bitmap.getWidth(), cropRect.height() / bitmap.getHeight());
        }

        private void updateImageRect() {
            imageRect.set(offsetX, offsetY, offsetX + (bitmap.getWidth() * scale), offsetY + (bitmap.getHeight() * scale));
        }
    }
}
