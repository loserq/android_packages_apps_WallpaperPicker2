/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.wallpaper.widget;

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;

import android.app.WallpaperColors;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Point;
import android.text.format.DateFormat;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import com.android.wallpaper.R;
import com.android.wallpaper.util.ScreenSizeCalculator;
import com.android.wallpaper.util.TimeUtils;
import com.android.wallpaper.util.TimeUtils.TimeTicker;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** A class to load the custom lockscreen view to the preview screen. */
public class LockScreenPreviewer implements LifecycleObserver {

    private static final String DEFAULT_DATE_PATTERN = "EEE, MMM d";

    private Context mContext;
    private String mDatePattern;
    private TimeTicker mTicker;
    private ImageView mLockIcon;
    private TextView mLockTime;
    private TextView mLockDate;

    public LockScreenPreviewer(Lifecycle lifecycle, Context context, ViewGroup previewContainer) {
        mContext = context;
        View contentView = LayoutInflater.from(mContext).inflate(
                R.layout.lock_screen_preview, /* root= */ null);
        mLockIcon = contentView.findViewById(R.id.lock_icon);
        mLockTime = contentView.findViewById(R.id.lock_time);
        mLockDate = contentView.findViewById(R.id.lock_date);
        mDatePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), DEFAULT_DATE_PATTERN);

        Display defaultDisplay = mContext.getSystemService(WindowManager.class).getDefaultDisplay();
        Point screenSize = ScreenSizeCalculator.getInstance().getScreenSize(defaultDisplay);

        Configuration config = mContext.getResources().getConfiguration();
        final boolean directionLTR = config.getLayoutDirection() == View.LAYOUT_DIRECTION_LTR;

        View rootView = previewContainer.getRootView();
        rootView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int cardHeight = previewContainer.getMeasuredHeight();
                int cardWidth = previewContainer.getMeasuredWidth();

                // Relayout the content view to match full screen size.
                contentView.measure(
                        makeMeasureSpec(screenSize.x, EXACTLY),
                        makeMeasureSpec(screenSize.y, EXACTLY));
                contentView.layout(0, 0, screenSize.x, screenSize.y);

                // Scale the content view from full screen size to the container(card) size.
                float scale = cardHeight > 0 ? (float) cardHeight / screenSize.y
                        : (float) cardWidth / screenSize.x;
                contentView.setScaleX(scale);
                contentView.setScaleY(scale);
                // The pivot point is centered by default, set to (0, 0).
                contentView.setPivotX(directionLTR ? 0f : contentView.getMeasuredWidth());
                contentView.setPivotY(0f);

                previewContainer.removeAllViews();
                previewContainer.addView(
                        contentView,
                        contentView.getMeasuredWidth(),
                        contentView.getMeasuredHeight());
                rootView.removeOnLayoutChangeListener(this);
            }
        });
        lifecycle.addObserver(this);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    @MainThread
    public void onResume() {
        mTicker = TimeTicker.registerNewReceiver(mContext, this::updateDateTime);
        updateDateTime();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    @MainThread
    public void onPause() {
        if (mContext != null) {
            mContext.unregisterReceiver(mTicker);
        }
    }

    /**
     * Sets the content's color based on the wallpaper's {@link WallpaperColors}.
     *
     * @param colors the {@link WallpaperColors} of the wallpaper which the lock screen overlay
     *               will attach to, or {@code null} to use light color as default
     */
    public void setColor(@Nullable WallpaperColors colors) {
        boolean useLightTextColor = colors == null
                || (colors.getColorHints() & WallpaperColors.HINT_SUPPORTS_DARK_TEXT) == 0;
        int color = mContext.getColor(useLightTextColor
                ? R.color.text_color_light : R.color.text_color_dark);
        int textShadowColor = mContext.getColor(useLightTextColor
                ? R.color.smartspace_preview_shadow_color_dark
                : R.color.smartspace_preview_shadow_color_transparent);
        mLockIcon.setImageTintList(ColorStateList.valueOf(color));
        mLockDate.setTextColor(color);
        mLockTime.setTextColor(color);

        mLockDate.setShadowLayer(
                mContext.getResources().getDimension(
                        R.dimen.smartspace_preview_key_ambient_shadow_blur),
                /* dx = */ 0,
                /* dy = */ 0,
                textShadowColor);
        mLockTime.setShadowLayer(
                mContext.getResources().getDimension(
                        R.dimen.smartspace_preview_key_ambient_shadow_blur),
                /* dx = */ 0,
                /* dy = */ 0,
                textShadowColor);
    }

    private void updateDateTime() {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        mLockTime.setText(TimeUtils.getFormattedTime(mContext, calendar));
        mLockDate.setText(DateFormat.format(mDatePattern, calendar));
    }
}
