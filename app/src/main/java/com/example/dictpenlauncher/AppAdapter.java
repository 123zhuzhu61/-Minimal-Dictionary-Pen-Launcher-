package com.example.dictpenlauncher;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    // 数据模型，增加了缓存字段
    public static class AppItem {
        public String type; // "app" / "url" / "add"
        public android.content.pm.ResolveInfo info;  // 仅 type="app" 时有效
        public String title;   // 仅 type="url" 时有效（网页标题）
        public String url;     // 仅 type="url" 时有效
        public String browserPkg; // 可选，指定浏览器包名

        // 缓存字段（仅 type="app" 时使用）
        public android.graphics.drawable.Drawable cachedIcon;
        public String cachedLabel;
    }

    private final Context ctx;
    private final List<AppItem> list;
    private final OnClick listener;

    public interface OnClick {
        void onItemClick(AppItem item);
        void onItemLongClick(AppItem item, int pos);
    }

    public AppAdapter(Context c, List<AppItem> items, OnClick l) {
        ctx = c;
        list = items;
        listener = l;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_app, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int pos) {
        AppItem item = list.get(pos);
        switch (item.type) {
            case "app":
                // 直接使用缓存的图标和名称，不再实时加载
                h.icon.setImageDrawable(item.cachedIcon);
                h.label.setText(item.cachedLabel);
                break;
            case "url":
                h.icon.setImageResource(R.drawable.baseline_language_24);
                h.label.setText(item.title);
                break;
            case "add":
                h.icon.setImageResource(R.drawable.baseline_add_circle_outline_24);
                h.label.setText("添加网页");
                break;
        }

        // 设置点击事件（注意：避免每次绑定都创建新匿名类，但这里数量不大，可接受）
        h.itemView.setOnClickListener(v -> listener.onItemClick(item));
        h.itemView.setOnLongClickListener(v -> {
            listener.onItemLongClick(item, pos);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class Holder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView label;

        public Holder(View v) {
            super(v);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
        }
    }
}