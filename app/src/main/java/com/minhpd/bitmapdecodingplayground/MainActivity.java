package com.minhpd.bitmapdecodingplayground;

import android.annotation.TargetApi;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.ImageDecoder;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final String INTENT_IMAGE_TYPE = "image/*";
    private static final int BITMAP_FACTORY_SCALE = 770;
    private static final int REGION_DECODER_SCALE_AND_CROP = 771;
    private static final int IMAGE_DECODER_SCALE = 772;
    private static final int IMAGE_DECODER_SCALE_AND_CROP = 773;
    private static final int REQUIRED_IMAGE_WIDTH = 480;

    ImageView preview;
    TextView text;
    ScrollView scroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupViews();
    }

    private void setupViews() {
        preview = findViewById(R.id.preview);
        text = findViewById(R.id.size);
        scroll = findViewById(R.id.scroll);

        findViewById(R.id.button1).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                chooseImage(BITMAP_FACTORY_SCALE);
            }
        });

        findViewById(R.id.button2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                chooseImage(REGION_DECODER_SCALE_AND_CROP);
            }
        });

        findViewById(R.id.button3).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestIfPie(IMAGE_DECODER_SCALE);
            }
        });

        findViewById(R.id.button4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestIfPie(IMAGE_DECODER_SCALE_AND_CROP);
            }
        });
    }

    private void requestIfPie(int requestCode) {
        if (isAtLeastPie()) {
            chooseImage(requestCode);
        } else {
            Toast.makeText(this, "Android P is required", Toast.LENGTH_SHORT).show();
        }
    }

    private void chooseImage(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT).setType(INTENT_IMAGE_TYPE);
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            Uri image = data.getData();
            Size initialSize = getImageSize(image);

            // use main thread here for the sake of simplicity
            Bitmap bitmap = null;
            switch (requestCode) {
                case BITMAP_FACTORY_SCALE:
                    bitmap = decodeScaledBitmap2(image); // ignore exif orientation tags
                    break;
                case REGION_DECODER_SCALE_AND_CROP:
                    bitmap = decodeScaledAndCroppedBitmap(image); // ignore exif
                    break;
                case IMAGE_DECODER_SCALE:
                    bitmap = decodeScaledBitmapWithTargetSize2(image); // respect exif
                    break;
                case IMAGE_DECODER_SCALE_AND_CROP:
                    bitmap = decodeScaledBitmapWithTargetSampleSize(image); // respect exif
                    break;
                default:
                    throw new IllegalStateException();
            }

            if (bitmap == null)
                return;

            preview.setImageBitmap(bitmap);

            Size finalSize = new Size(bitmap.getWidth(), bitmap.getHeight());
            text.setText("Initial: " + initialSize + "\nFinal: " + finalSize);
            scroll.fullScroll(View.FOCUS_DOWN);
        }
    }

    private Bitmap decodeScaledBitmap(Uri image) {
        Size initialSize = getImageSize(image);

        int requiredWidth = Math.min(REQUIRED_IMAGE_WIDTH, initialSize.width);
        int sourceWidth = initialSize.width;
        int sampleSize = calculateSampleSize(sourceWidth, requiredWidth);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        options.inDensity = sourceWidth;
        options.inTargetDensity = requiredWidth * sampleSize;

        try {
            InputStream input = getContentResolver().openInputStream(image);
            long start = System.currentTimeMillis();
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            Log.d(TAG, "decodeScaledBitmap: " + (System.currentTimeMillis() - start));
            // reset density to display bitmap correctly
            if (bitmap != null)
                bitmap.setDensity(getResources().getDisplayMetrics().densityDpi);
            return bitmap;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Bitmap decodeScaledBitmap2(Uri image) {
        Size initialSize = getImageSize(image);

        int areaLimit = REQUIRED_IMAGE_WIDTH * REQUIRED_IMAGE_WIDTH;
        int targetWidth = (int) Math.sqrt((double) areaLimit * initialSize.width / initialSize.height);
        int targetHeight = (int) Math.sqrt((double) areaLimit * initialSize.height / initialSize.width);
        int sampleSize = calculateSampleSize(initialSize.width, initialSize.height, REQUIRED_IMAGE_WIDTH);
//        int sampleSize = calculateSampleSize(initialSize.width, REQUIRED_IMAGE_WIDTH);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
//        options.inScaled = true;
        options.inDensity = Math.max(initialSize.width, initialSize.height);
        options.inTargetDensity = Math.max(targetWidth, targetHeight) * sampleSize;

        try {
            InputStream input = getContentResolver().openInputStream(image);
            long start = System.currentTimeMillis();
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            Log.d(TAG, "decodeScaledBitmap2: " + (System.currentTimeMillis() - start));
            // reset density to display bitmap correctly
            if (bitmap != null)
                bitmap.setDensity(getResources().getDisplayMetrics().densityDpi);
            return bitmap;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Bitmap decodeScaledAndCroppedBitmap(Uri image) {
        Size initialSize = getImageSize(image);
        int requiredWidth = Math.min(REQUIRED_IMAGE_WIDTH, initialSize.width);

        Rect cropRect = new Rect(0, 0, initialSize.width, initialSize.height / 2);

        InputStream input = null;
        try {
            input = getContentResolver().openInputStream(image);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateSampleSize(initialSize.width, requiredWidth);
        try {
            return BitmapRegionDecoder.newInstance(input, true).decodeRegion(cropRect, options);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @TargetApi(28)
    private Bitmap decodeScaledBitmapWithTargetSize(Uri image) {
        ImageDecoder.OnHeaderDecodedListener header = new ImageDecoder.OnHeaderDecodedListener() {
            @Override
            public void onHeaderDecoded(@NonNull ImageDecoder imageDecoder,
                                        @NonNull ImageDecoder.ImageInfo imageInfo,
                                        @NonNull ImageDecoder.Source source) {
                android.util.Size size = imageInfo.getSize();
                int requiredWidth = Math.min(REQUIRED_IMAGE_WIDTH, size.getWidth());
                double coefficient = requiredWidth / (double) size.getWidth();
                int newHeight = (int) (size.getHeight() * coefficient);
                imageDecoder.setTargetSize(requiredWidth, newHeight);
            }
        };

        ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), image);
        try {
            long start = System.currentTimeMillis();
            Bitmap bitmap = ImageDecoder.decodeBitmap(source, header);
            Log.d(TAG, "decodeScaledBitmapWithTargetSize: " + (System.currentTimeMillis() - start));
            return bitmap;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    @TargetApi(28)
    private Bitmap decodeScaledBitmapWithTargetSize2(Uri image) {
        ImageDecoder.OnHeaderDecodedListener header = new ImageDecoder.OnHeaderDecodedListener() {
            @Override
            public void onHeaderDecoded(@NonNull ImageDecoder imageDecoder,
                                        @NonNull ImageDecoder.ImageInfo imageInfo,
                                        @NonNull ImageDecoder.Source source) {
                android.util.Size initialSize = imageInfo.getSize();

                int areaLimit = REQUIRED_IMAGE_WIDTH * REQUIRED_IMAGE_WIDTH;
                int targetWidth = (int) Math.sqrt((double) areaLimit * initialSize.getWidth() / initialSize.getHeight());
                int targetHeight = (int) Math.sqrt((double) areaLimit * initialSize.getHeight() / initialSize.getWidth());
                imageDecoder.setTargetSize(targetWidth, targetHeight);
            }
        };

        ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), image);
        try {
            long start = System.currentTimeMillis();
            Bitmap bitmap = ImageDecoder.decodeBitmap(source, header);
            Log.d(TAG, "decodeScaledBitmapWithTargetSize2: " + (System.currentTimeMillis() - start));
            return bitmap;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    @TargetApi(28)
    private Bitmap decodeScaledBitmapWithTargetSampleSize(Uri image) {
        ImageDecoder.OnHeaderDecodedListener header = new ImageDecoder.OnHeaderDecodedListener() {
            @Override
            public void onHeaderDecoded(@NonNull ImageDecoder imageDecoder,
                                        @NonNull ImageDecoder.ImageInfo imageInfo,
                                        @NonNull ImageDecoder.Source source) {
                android.util.Size size = imageInfo.getSize();
                int sampleSize = calculateSampleSize(size.getWidth(), Math.min(REQUIRED_IMAGE_WIDTH, size.getWidth()));
                Size newSize = new Size(size.getWidth() / sampleSize, size.getHeight() / sampleSize);
                imageDecoder.setTargetSampleSize(sampleSize);
                imageDecoder.setCrop(new Rect(0, 0, newSize.width, newSize.height));
            }
        };

        ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), image);
        try {
            return ImageDecoder.decodeBitmap(source, header);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Size getImageSize(Uri image) {
        InputStream input = null;
        try {
            input = getContentResolver().openInputStream(image);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(input, null, options);
            return new Size(options.outWidth, options.outHeight);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return new Size(0, 0);
    }

    private int calculateSampleSize(int currentWidth, int requiredWidth) {
        int inSampleSize = 1;
        if (currentWidth > requiredWidth) {
            int halfWidth = currentWidth / 2;
            // Calculate the largest inSampleSize value that is a power of 2 and keeps
            // width larger than the requested width
            while (halfWidth / inSampleSize >= requiredWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private int calculateSampleSize(int width, int height, int target) {
        int result = 1;
        int targetPow2 = target * target;
        if (width * height < targetPow2) return result;
        for (int i = 0; i < 10; i++) {
            if (width * height< targetPow2) {
                result = result / 2;
                break;
            }

            width = width / 2;
            height = height / 2;
            result = result * 2;
        }

        return result;
    }

    private boolean isAtLeastPie() {
        return Build.VERSION.SDK_INT > 28;
    }

    static final class Size {
        int width;
        int height;

        public Size(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @NonNull
        @Override
        public String toString() {
            return "width: " + width + ", height: " + height;
        }
    }
}
