package cz.spojenka.android.ui.drawable;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;

import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;

public class QrCodeDrawable extends BitmapDrawable {

    private final float paddingPercent;
    private final int backgroundColor;
    private final Paint backgroundPaint = new Paint();

    public QrCodeDrawable(Resources resources, Options options) {
        super(resources, generateQRBitmap(options.getData(), options.foregroundColor, options.backgroundColor));
        setFilterBitmap(false);
        paddingPercent = options.padding;
        backgroundColor = options.backgroundColor;
        backgroundPaint.setColor(backgroundColor);
        backgroundPaint.setStyle(Paint.Style.FILL);
    }

    private final Outline imageOutline = new Outline();
    private final Rect imageRect = new Rect();

    @Override
    public void draw(Canvas canvas) {
        if (paddingPercent > 0) {
            Rect bounds = getBounds();
            if (backgroundColor != Color.TRANSPARENT) {
                canvas.drawRect(bounds, backgroundPaint);
            }
            int width = bounds.width();
            int height = bounds.height();
            float paddingX = (int) (width * paddingPercent);
            float paddingY = (int) (height * paddingPercent);
            canvas.save();
            canvas.scale(
                    (width - paddingX * 2) / width,
                    (height - paddingY * 2) / height,
                    bounds.centerX(),
                    bounds.centerY()
            );
            super.draw(canvas);
            canvas.restore();
        } else {
            super.draw(canvas);
        }
    }

    private static Bitmap generateQRBitmap(QRCode qrCode, int foregroundColor, int backgroundColor) {
        ByteMatrix matrix = qrCode.getMatrix();
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                bitmap.setPixel(x, y, matrix.get(x, y) != 0 ? foregroundColor : backgroundColor);
            }
        }
        return bitmap;
    }

    private static QRCode textToQRCode(String text, ErrorCorrectionLevel errorCorrectionLevel) {
        try {
            return Encoder.encode(text, errorCorrectionLevel);
        } catch (WriterException e) {
            throw new RuntimeException(e);
        }
    }

    public static class Options {

        private int backgroundColor = Color.TRANSPARENT;
        private int foregroundColor = Color.BLACK;
        private ErrorCorrectionLevel errorCorrectionLevel = ErrorCorrectionLevel.L;
        private QRCode data;
        private String text;
        private float padding = 0f;

        public Options setBackgroundColor(int color) {
            this.backgroundColor = color;
            return this;
        }

        public Options setForegroundColor(int color) {
            this.foregroundColor = color;
            return this;
        }

        public Options setErrorCorrectionLevel(ErrorCorrectionLevel level) {
            this.errorCorrectionLevel = level;
            return this;
        }

        public Options setData(QRCode data) {
            this.data = data;
            return this;
        }

        public Options setData(String string) {
            this.text = string;
            return this;
        }

        public Options setPadding(float padding) {
            this.padding = padding;
            return this;
        }

        private QRCode getData() {
            if (data != null) {
                return data;
            }
            if (text != null) {
                return textToQRCode(text, errorCorrectionLevel);
            }
            throw new IllegalStateException("No data or text provided");
        }
    }
}
