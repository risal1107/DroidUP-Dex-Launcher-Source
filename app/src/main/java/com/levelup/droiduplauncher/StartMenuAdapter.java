package com.levelup.droiduplauncher;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class StartMenuAdapter extends BaseAdapter {
    private final Context context;
    private final List<AppEntry> apps;
    private final int itemPadding;
    private final int iconSize;

    public StartMenuAdapter(Context context, List<AppEntry> apps, int itemPadding, int iconSize) {
        this.context = context;
        this.apps = apps;
        this.itemPadding = itemPadding;
        this.iconSize = iconSize;
    }

    @Override
    public int getCount() {
        return apps.size();
    }

    @Override
    public Object getItem(int position) {
        return apps.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LinearLayout layout;
        if (convertView == null) {
            layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setGravity(Gravity.CENTER);
            layout.setPadding(itemPadding, itemPadding, itemPadding, itemPadding);

            ImageView iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            layout.addView(iconView, iconParams);

            TextView labelView = new TextView(context);
            labelView.setGravity(Gravity.CENTER);
            labelView.setTextColor(Color.WHITE);
            labelView.setTextSize(11f);
            labelView.setMaxLines(2);
            labelView.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            textParams.topMargin = Math.round(4 * context.getResources().getDisplayMetrics().density);
            layout.addView(labelView, textParams);
        } else {
            layout = (LinearLayout) convertView;
        }

        AppEntry app = apps.get(position);
        ImageView iconView = (ImageView) layout.getChildAt(0);
        TextView labelView = (TextView) layout.getChildAt(1);

        iconView.setImageDrawable(app.icon);
        labelView.setText(app.label);

        return layout;
    }
}
