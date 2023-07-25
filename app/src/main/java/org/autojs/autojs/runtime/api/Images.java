package org.autojs.autojs.runtime.api;

import static org.autojs.autojs.util.StringUtils.str;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.Image;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;

import androidx.annotation.NonNull;

import org.autojs.autojs.annotation.ScriptVariable;
import org.autojs.autojs.concurrent.VolatileDispose;
import org.autojs.autojs.core.image.ColorFinder;
import org.autojs.autojs.core.image.ImageWrapper;
import org.autojs.autojs.core.image.TemplateMatching;
import org.autojs.autojs.core.image.capture.ScreenCaptureRequester;
import org.autojs.autojs.core.image.capture.ScreenCapturer;
import org.autojs.autojs.core.opencv.Mat;
import org.autojs.autojs.core.opencv.OpenCVHelper;
import org.autojs.autojs.core.ui.inflater.util.Drawables;
import org.autojs.autojs.pio.UncheckedIOException;
import org.autojs.autojs.pref.Language;
import org.autojs.autojs.runtime.ScriptRuntime;
import org.autojs.autojs6.inrt.R;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Stardust on 2017/5/20.
 */
@SuppressWarnings("unused")
public class Images {

    private static final String TAG = Images.class.getSimpleName();

    private final ScriptRuntime mScriptRuntime;
    private final ScreenCaptureRequester mScreenCaptureRequester;
    private ScreenCapturer mScreenCapturer;
    private final Context mContext;
    private Image mPreCapture;
    private ImageWrapper mPreCaptureImage;
    private final ScreenMetrics mScreenMetrics;
    private volatile boolean mOpenCvInitialized = false;

    @ScriptVariable
    public final ColorFinder colorFinder;

    public Images(Context context, ScriptRuntime scriptRuntime, ScreenCaptureRequester screenCaptureRequester) {
        mScriptRuntime = scriptRuntime;
        mScreenCaptureRequester = screenCaptureRequester;
        mContext = context;
        mScreenMetrics = mScriptRuntime.getScreenMetrics();
        colorFinder = new ColorFinder(mScreenMetrics);
    }

    public ScriptPromiseAdapter requestScreenCapture(int orientation) {
        ScriptPromiseAdapter promiseAdapter = new ScriptPromiseAdapter();
        if (mScreenCapturer == null || !mScreenCapturer.isMediaProjectionValid()) {
            mScreenCaptureRequester.setOnActivityResultCallback((result, data) -> {
                if (result == Activity.RESULT_OK) {
                    int density = ScreenMetrics.getDeviceScreenDensity();
                    Looper servantLooper = mScriptRuntime.loopers.getServantLooper();
                    Handler handler = new Handler(servantLooper);
                    mScreenCapturer = ScreenCapturer.getInstance(mContext, data, orientation, density, handler, mScriptRuntime);
                    promiseAdapter.resolve(true);
                } else {
                    promiseAdapter.resolve(false);
                }
            });
            mScreenCaptureRequester.request();
        } else {
            mScreenCapturer.setOrientation(orientation);
            // FIXME by SuperMonster003 on May 19, 2022.
            //  ! In JavaScript images module, code below will get a stuck
            //  ! when being called more than once in the same script runtime:
            //  ! ResultAdapter.wait(rtImages.requestScreenCapture(orientation))
            promiseAdapter.resolve(true);
        }
        return promiseAdapter;
    }

    public synchronized ImageWrapper captureScreen() {
        if (mScreenCapturer == null) {
            throw new SecurityException(mContext.getString(R.string.error_no_screen_capture_permission));
        }
        Image capture = mScreenCapturer.capture();
        if (capture != mPreCapture || mPreCaptureImage == null) {
            mPreCapture = capture;
            if (mPreCaptureImage != null) {
                mPreCaptureImage.recycle();
            }
            mPreCaptureImage = ImageWrapper.ofImage(capture);
        }
        return mPreCaptureImage;
    }

    public boolean captureScreen(String path) {
        path = mScriptRuntime.files.path(path);
        ImageWrapper image = captureScreen();
        if (image != null) {
            image.saveTo(path);
            return true;
        }
        return false;
    }

    public ImageWrapper copy(@NonNull ImageWrapper image) {
        ImageWrapper imageWrapper = image.clone();
        image.shoot();
        return imageWrapper;
    }

    public boolean save(@NonNull ImageWrapper image, String path, String format, int quality) throws IOException {
        Bitmap.CompressFormat compressFormat = parseImageFormat(format);
        Bitmap bitmap = image.getBitmap();
        FileOutputStream outputStream = new FileOutputStream(mScriptRuntime.files.path(path));
        boolean b = bitmap.compress(compressFormat, quality, outputStream);
        image.shoot();
        return b;
    }

    public static int pixel(ImageWrapper image, int x, int y) {
        if (image == null) {
            throw new NullPointerException(str(R.string.error_method_called_with_null_argument, "Images.pixel", "image"));
        }
        int pixel = image.pixel(x, y);
        image.shoot();
        return pixel;
    }

    public static ImageWrapper concat(ImageWrapper imgA, ImageWrapper imgB, int direction) {
        if (!Arrays.asList(Gravity.START, Gravity.END, Gravity.TOP, Gravity.BOTTOM).contains(direction)) {
            throw new IllegalArgumentException(str(R.string.error_illegal_argument, "direction", direction));
        }
        int width;
        int height;
        if (direction == Gravity.START || direction == Gravity.TOP) {
            ImageWrapper tmp = imgA;
            imgA = imgB;
            imgB = tmp;
        }
        if (direction == Gravity.START || direction == Gravity.END) {
            width = imgA.getWidth() + imgB.getWidth();
            height = Math.max(imgA.getHeight(), imgB.getHeight());
        } else {
            width = Math.max(imgA.getWidth(), imgB.getHeight());
            height = imgA.getHeight() + imgB.getHeight();
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        if (direction == Gravity.START || direction == Gravity.END) {
            canvas.drawBitmap(imgA.getBitmap(), 0, (float) (height - imgA.getHeight()) / 2, paint);
            canvas.drawBitmap(imgB.getBitmap(), imgA.getWidth(), (float) (height - imgB.getHeight()) / 2, paint);
        } else {
            canvas.drawBitmap(imgA.getBitmap(), (float) (width - imgA.getWidth()) / 2, 0, paint);
            canvas.drawBitmap(imgB.getBitmap(), (float) (width - imgB.getWidth()) / 2, imgA.getHeight(), paint);
        }
        imgA.shoot();
        imgB.shoot();
        return ImageWrapper.ofBitmap(bitmap);
    }

    public ImageWrapper rotate(@NonNull ImageWrapper img, float x, float y, float degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree, x, y);
        ImageWrapper imageWrapper = ImageWrapper.ofBitmap(Bitmap.createBitmap(img.getBitmap(), 0, 0, img.getWidth(), img.getHeight(), matrix, true));
        img.shoot();
        return imageWrapper;
    }

    public ImageWrapper clip(@NonNull ImageWrapper img, int x, int y, int w, int h) {
        ImageWrapper imageWrapper = ImageWrapper.ofBitmap(Bitmap.createBitmap(img.getBitmap(), x, y, w, h));
        img.shoot();
        return imageWrapper;
    }

    public ImageWrapper read(String path) {
        path = mScriptRuntime.files.path(path);
        Bitmap bitmap = BitmapFactory.decodeFile(path);
        return ImageWrapper.ofBitmap(bitmap);
    }

    public ImageWrapper fromBase64(String data) {
        return ImageWrapper.ofBitmap(Drawables.loadBase64Data(data));
    }

    public String toBase64(ImageWrapper img, String format, int quality) {
        return Base64.encodeToString(toBytes(img, format, quality), Base64.NO_WRAP);
    }

    public byte[] toBytes(@NonNull ImageWrapper img, String format, int quality) {
        Bitmap.CompressFormat compressFormat = parseImageFormat(format);
        Bitmap bitmap = img.getBitmap();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(compressFormat, quality, outputStream);
        img.shoot();
        return outputStream.toByteArray();
    }

    public ImageWrapper fromBytes(byte[] bytes) {
        return ImageWrapper.ofBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
    }

    private Bitmap.CompressFormat parseImageFormat(String format) {
        return switch (format.toLowerCase(Language.getPrefLanguage().getLocale())) {
            case "png" -> Bitmap.CompressFormat.PNG;
            case "jpeg", "jpg" -> Bitmap.CompressFormat.JPEG;
            case "webp" -> Bitmap.CompressFormat.WEBP;
            default -> throw new IllegalArgumentException(str(R.string.error_illegal_argument, "format", format));
        };
    }

    public ImageWrapper load(String src) {
        try {
            URL url = new URL(src);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            return ImageWrapper.ofBitmap(bitmap);
        } catch (IOException e) {
            return null;
        }
    }

    public static void saveBitmap(@NonNull Bitmap bitmap, String path) {
        try {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, new FileOutputStream(path));
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Bitmap scaleBitmap(Bitmap origin, int newWidth, int newHeight) {
        if (origin == null) {
            return null;
        }
        int height = origin.getHeight();
        int width = origin.getWidth();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        return Bitmap.createBitmap(origin, 0, 0, width, height, matrix, false);
    }

    public void releaseScreenCapturer() {
        if (mScreenCapturer != null) {
            mScreenCapturer.release(mScriptRuntime);
        }
    }

    public Point findImage(ImageWrapper image, ImageWrapper template) {
        return findImage(image, template, 0.9f, null);
    }

    public Point findImage(ImageWrapper image, ImageWrapper template, float threshold) {
        return findImage(image, template, threshold, null);
    }

    public Point findImage(ImageWrapper image, ImageWrapper template, float threshold, Rect rect) {
        return findImage(image, template, 0.7f, threshold, rect, TemplateMatching.MAX_LEVEL_AUTO);
    }

    public Point findImage(ImageWrapper image, ImageWrapper template, float weakThreshold, float threshold, Rect rect, int maxLevel) {
        initOpenCvIfNeeded();
        if (image == null) {
            throw new NullPointerException(mContext.getString(R.string.error_method_called_with_null_argument, "Images.findImage", "image"));
        }
        if (template == null) {
            throw new NullPointerException(str(R.string.error_method_called_with_null_argument, "Images.findImage", "template"));
        }
        Mat src = image.getMat();
        if (rect != null) {
            src = new Mat(src, rect);
        }
        org.opencv.core.Point point = TemplateMatching.fastTemplateMatching(
                src,
                template.getMat(),
                TemplateMatching.MATCHING_METHOD_DEFAULT,
                weakThreshold,
                threshold,
                maxLevel
        );

        if (src != image.getMat()) {
            OpenCVHelper.release(src);
        }
        image.shoot();
        template.shoot();

        if (point != null) {
            if (rect != null) {
                point.x += rect.x;
                point.y += rect.y;
            }
            point.x = mScreenMetrics.scaleX((int) point.x);
            point.y = mScreenMetrics.scaleX((int) point.y);
        }
        return point;
    }

    public List<TemplateMatching.Match> matchTemplate(ImageWrapper image, ImageWrapper template, float weakThreshold, float threshold, Rect rect, int maxLevel, int limit) {
        initOpenCvIfNeeded();
        if (image == null) {
            throw new NullPointerException(str(R.string.error_method_called_with_null_argument, "Images.matchTemplate", "image"));
        }
        if (template == null) {
            throw new NullPointerException(str(R.string.error_method_called_with_null_argument, "Images.matchTemplate", "template"));
        }
        Mat src = image.getMat();
        if (rect != null) {
            src = new Mat(src, rect);
        }
        List<TemplateMatching.Match> result = TemplateMatching.fastTemplateMatching(
                src,
                template.getMat(),
                Imgproc.TM_CCOEFF_NORMED,
                weakThreshold,
                threshold,
                maxLevel,
                limit
        );

        if (src != image.getMat()) {
            OpenCVHelper.release(src);
        }
        image.shoot();
        template.shoot();

        for (TemplateMatching.Match match : result) {
            Point point = match.point;
            if (rect != null) {
                point.x += rect.x;
                point.y += rect.y;
            }
            point.x = mScreenMetrics.scaleX((int) point.x);
            point.y = mScreenMetrics.scaleX((int) point.y);
        }
        return result;
    }

    public Mat newMat() {
        return new Mat();
    }

    public Mat newMat(Mat mat, Rect roi) {
        return new Mat(mat, roi);
    }

    public void initOpenCvIfNeeded() {
        if (mOpenCvInitialized || OpenCVHelper.isInitialized()) {
            return;
        }
        Activity currentActivity = mScriptRuntime.app.getCurrentActivity();
        Context context = currentActivity == null ? mContext : currentActivity;
        Log.i(TAG, "opencv: initializing");
        if (Looper.myLooper() == Looper.getMainLooper()) {
            OpenCVHelper.initIfNeeded(context, this::initSuccessfully);
        } else {
            VolatileDispose<Boolean> result = new VolatileDispose<>();
            OpenCVHelper.initIfNeeded(context, () -> {
                initSuccessfully();
                result.setAndNotify(true);
            });
            result.blockedGet();
        }

    }

    private void initSuccessfully() {
        mOpenCvInitialized = true;
        Log.i(TAG, "opencv: initialized");
    }

}