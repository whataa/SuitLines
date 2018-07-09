/*
 * Copyright 2017 linjiang.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tech.linjiang.suitlines;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.widget.EdgeEffect;

import java.lang.reflect.Field;

/**
 * https://github.com/whataa
 */
class Util {

    static int getCeil5(float num) {
        boolean isNegative = num < 0;
        return (((int) ((isNegative ? -num : num) + 4.9f)) / 5 * 5) * (isNegative ? -1 : 1);
    }

    static float calcTextSuitBaseY(RectF rectF, Paint paint) {
        return rectF.top + rectF.height() / 2 -
                (paint.getFontMetrics().ascent + paint.getFontMetrics().descent) / 2;
    }

    static float size2sp(float sp, Context context) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                sp, context.getResources().getDisplayMetrics());
    }

    static int dip2px(float dipValue) {
        final float scale = Resources.getSystem().getDisplayMetrics().density;
        return (int) (dipValue * scale + 0.5f);
    }

    static float getTextHeight(Paint textPaint) {
        return -textPaint.ascent() - textPaint.descent();
    }

    static void trySetColorForEdgeEffect(EdgeEffect edgeEffect, int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            edgeEffect.setColor(color);
            return;
        }
        try {
            Field edgeField = EdgeEffect.class.getDeclaredField("mEdge");
            edgeField.setAccessible(true);
            Drawable mEdge = (Drawable) edgeField.get(edgeEffect);
            mEdge.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            mEdge.setCallback(null);
            Field glowField = EdgeEffect.class.getDeclaredField("mGlow");
            glowField.setAccessible(true);
            Drawable mGlow = (Drawable) glowField.get(edgeEffect);
            mGlow.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            mGlow.setCallback(null);
        } catch (Exception ignored) {
        }
    }

    static int tryGetStartColorOfLinearGradient(LinearGradient gradient) {
        try {
            Field field = LinearGradient.class.getDeclaredField("mColors");
            field.setAccessible(true);
            int[] colors = (int[]) field.get(gradient);
            return colors[0];
        } catch (Exception e) {
            e.printStackTrace();
            try {
                Field field = LinearGradient.class.getDeclaredField("mColor0");
                field.setAccessible(true);
                return (int) field.get(gradient);
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }
        return Color.BLACK;
    }
}
