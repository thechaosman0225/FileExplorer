/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * This file is part of FileExplorer.
 *
 * FileExplorer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FileExplorer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SwiFTP.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.micode.fileexplorer;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.core.content.ContextCompat;

public class CategoryBar extends View {
    private static final String LOG_TAG = "CategoryBar";

    private static final int MARGIN = 12;

    private static final int ANI_TOTAL_FRAMES = 10;

    private static final int ANI_PERIOD = 100;
    private Timer timer;

    private class Category {
        public long value;

        public long tmpValue; // used for animation

        public long aniStep; // animation step

        public int resImg;
    }

    private ArrayList<Category> categories = new ArrayList<Category>();

    private long mFullValue;

    public void setFullValue(long value) {
        mFullValue = value;
    }

    public CategoryBar(Context context) {
        this(context, null);
    }

    public CategoryBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CategoryBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void addCategory(int categoryImg) {
        Category ca = new Category();
        ca.resImg = categoryImg;
        categories.add(ca);
    }

    public boolean setCategoryValue(int index, long value) {
        if (index < 0 || index >= categories.size())
            return false;
        categories.get(index).value = value;
        invalidate();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth() - MARGIN * 2;
        int height = getHeight() - MARGIN * 2;
        boolean isHorizontal = (width > height);
        float trackLeft = isHorizontal ? MARGIN : 0;
        float trackTop = isHorizontal ? 0 : MARGIN;
        float trackRight = isHorizontal ? MARGIN + width : getWidth();
        float trackBottom = isHorizontal ? getHeight() : MARGIN + height;
        if (trackRight <= trackLeft || trackBottom <= trackTop) {
            return;
        }

        RectF trackBounds = new RectF(trackLeft, trackTop, trackRight, trackBottom);
        float cornerRadius = Math.min(trackBounds.width(), trackBounds.height()) / 2;
        Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setColor(ContextCompat.getColor(getContext(), R.color.m3_surface_variant));
        canvas.drawRoundRect(trackBounds, cornerRadius, cornerRadius, trackPaint);

        Path trackPath = new Path();
        trackPath.addRoundRect(trackBounds, cornerRadius, cornerRadius, Path.Direction.CW);
        int saveCount = canvas.save();
        canvas.clipPath(trackPath);

        int beginning = isHorizontal ? MARGIN : MARGIN + height;
        if (mFullValue > 0) {
            for (Category c : categories) {
                long value = (timer == null ? c.value : c.tmpValue);
                if (isHorizontal) {
                    int w = (int) (value * width / mFullValue);
                    if (w == 0)
                        continue;
                    Drawable d = getDrawable(c.resImg);
                    Rect bounds = new Rect(beginning, 0, beginning + w, getHeight());
                    d.setBounds(bounds);
                    d.draw(canvas);
                    beginning += w;
                } else {
                    int h = (int) (value * height / mFullValue);
                    if (h == 0)
                        continue;
                    Drawable d = getDrawable(c.resImg);
                    Rect bounds = new Rect(0, beginning - h, getWidth(), beginning);
                    d.setBounds(bounds);
                    d.draw(canvas);
                    beginning -= h;
                }
            }
        }
        canvas.restoreToCount(saveCount);
    }

    private Drawable getDrawable(int id) {
        return getContext().getResources().getDrawable(id);
    }

    private void stepAnimation() {
        if (timer == null)
            return;

        int finished = 0;
        for (Category c : categories) {
            c.tmpValue += c.aniStep;
            if (c.tmpValue >= c.value) {
                c.tmpValue = c.value;
                finished++;
                if (finished >= categories.size()) {
                    // stop animation
                    timer.cancel();
                    timer = null;
                    Log.v(LOG_TAG, "Animation stopped");
                    break;
                }
            }
        }

        postInvalidate();
    }

    synchronized public void startAnimation() {
        if (timer != null) {
            return;
        }

        Log.v(LOG_TAG, "startAnimation");

        for (Category c : categories) {
            c.tmpValue = 0;
            c.aniStep = c.value / ANI_TOTAL_FRAMES;
        }

        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {

            public void run() {
                stepAnimation();
            }

        }, 0, ANI_PERIOD);
    }
}
