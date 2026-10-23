package dev.flexy.dynamicisland.island;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

final class Glyphs {
    static final int PLAY = 1;
    static final int PAUSE = 2;
    static final int PREV = 3;
    static final int NEXT = 4;
    static final int BOLT = 5;
    static final int STOP = 6;
    static final int MIC = 7;
    static final int MIC_OFF = 8;
    static final int HANGUP = 9;
    static final int SPEAKER = 10;

    private static final Path PATH = new Path();
    private static final RectF R = new RectF();

    private Glyphs() {
    }

    static void draw(Canvas c, int glyph, float cx, float cy, float size, Paint paint) {
        float h = size / 2f;
        Path p = PATH;
        p.reset();
        switch (glyph) {
            case PLAY: {
                float w = size * 0.78f;
                float left = cx - w * 0.38f;
                p.moveTo(left, cy - h * 0.82f);
                p.lineTo(left + w * 0.86f, cy);
                p.lineTo(left, cy + h * 0.82f);
                p.close();
                roundJoin(paint, size);
                c.drawPath(p, paint);
                break;
            }
            case PAUSE: {
                float bw = size * 0.22f;
                float gap = size * 0.18f;
                float r = bw * 0.35f;
                R.set(cx - gap / 2f - bw, cy - h * 0.8f, cx - gap / 2f, cy + h * 0.8f);
                c.drawRoundRect(R, r, r, paint);
                R.set(cx + gap / 2f, cy - h * 0.8f, cx + gap / 2f + bw, cy + h * 0.8f);
                c.drawRoundRect(R, r, r, paint);
                break;
            }
            case PREV:
            case NEXT: {
                float dir = glyph == NEXT ? 1f : -1f;
                float tw = size * 0.52f;
                float th = h * 0.72f;
                float barW = size * 0.11f;
                float tip = cx + dir * (tw / 2f);
                float base = cx - dir * (tw / 2f);
                p.moveTo(base, cy - th);
                p.lineTo(tip, cy);
                p.lineTo(base, cy + th);
                p.close();
                roundJoin(paint, size);
                c.drawPath(p, paint);
                paint.setStyle(Paint.Style.FILL);
                float bx = tip + dir * barW * 0.9f;
                R.set(Math.min(bx, bx + dir * barW), cy - th, Math.max(bx, bx + dir * barW), cy + th);
                c.drawRoundRect(R, barW / 2f, barW / 2f, paint);
                break;
            }
            case BOLT: {
                float s = size;
                float x0 = cx - s * 0.5f;
                float y0 = cy - s * 0.5f;
                p.moveTo(x0 + s * 0.58f, y0 + s * 0.04f);
                p.lineTo(x0 + s * 0.20f, y0 + s * 0.56f);
                p.lineTo(x0 + s * 0.47f, y0 + s * 0.56f);
                p.lineTo(x0 + s * 0.40f, y0 + s * 0.96f);
                p.lineTo(x0 + s * 0.80f, y0 + s * 0.42f);
                p.lineTo(x0 + s * 0.53f, y0 + s * 0.42f);
                p.close();
                roundJoin(paint, size);
                c.drawPath(p, paint);
                break;
            }
            case STOP: {
                float half = size * 0.36f;
                R.set(cx - half, cy - half, cx + half, cy + half);
                c.drawRoundRect(R, size * 0.12f, size * 0.12f, paint);
                break;
            }
            case MIC:
            case MIC_OFF: {
                float s = size;
                float stroke = s * 0.085f;
                R.set(cx - s * 0.16f, cy - s * 0.46f, cx + s * 0.16f, cy + s * 0.12f);
                c.drawRoundRect(R, s * 0.16f, s * 0.16f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(stroke);
                paint.setStrokeCap(Paint.Cap.ROUND);
                R.set(cx - s * 0.30f, cy - s * 0.24f, cx + s * 0.30f, cy + s * 0.27f);
                c.drawArc(R, 0f, 180f, false, paint);
                c.drawLine(cx, cy + s * 0.27f, cx, cy + s * 0.42f, paint);
                c.drawLine(cx - s * 0.15f, cy + s * 0.44f, cx + s * 0.15f, cy + s * 0.44f, paint);
                if (glyph == MIC_OFF) c.drawLine(cx - s * 0.40f, cy - s * 0.44f, cx + s * 0.40f, cy + s * 0.44f, paint);
                paint.setStrokeCap(Paint.Cap.BUTT);
                break;
            }
            case HANGUP: {
                float s = size;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s * 0.17f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                R.set(cx - s * 0.42f, cy - s * 0.16f, cx + s * 0.42f, cy + s * 0.50f);
                c.drawArc(R, 200f, 140f, false, paint);
                paint.setStrokeWidth(s * 0.20f);
                float ey = cy + s * 0.10f;
                c.drawLine(cx - s * 0.40f, ey - s * 0.02f, cx - s * 0.36f, ey + s * 0.10f, paint);
                c.drawLine(cx + s * 0.40f, ey - s * 0.02f, cx + s * 0.36f, ey + s * 0.10f, paint);
                paint.setStrokeCap(Paint.Cap.BUTT);
                break;
            }
            case SPEAKER: {
                float s = size;
                float l = cx - s * 0.42f;
                p.moveTo(l, cy - s * 0.13f);
                p.lineTo(l + s * 0.17f, cy - s * 0.13f);
                p.lineTo(l + s * 0.40f, cy - s * 0.36f);
                p.lineTo(l + s * 0.40f, cy + s * 0.36f);
                p.lineTo(l + s * 0.17f, cy + s * 0.13f);
                p.lineTo(l, cy + s * 0.13f);
                p.close();
                roundJoin(paint, size);
                c.drawPath(p, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s * 0.085f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                float wx = l + s * 0.40f;
                R.set(wx - s * 0.20f, cy - s * 0.20f, wx + s * 0.20f, cy + s * 0.20f);
                c.drawArc(R, -50f, 100f, false, paint);
                R.set(wx - s * 0.40f, cy - s * 0.40f, wx + s * 0.40f, cy + s * 0.40f);
                c.drawArc(R, -50f, 100f, false, paint);
                paint.setStrokeCap(Paint.Cap.BUTT);
                break;
            }
            default:
                break;
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(0f);
    }

    private static void roundJoin(Paint paint, float size) {
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(size * 0.06f);
    }
}
