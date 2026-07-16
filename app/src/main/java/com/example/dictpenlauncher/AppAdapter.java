package com.example.dictpenlauncher;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * 应用列表适配器（普通模式 + 编辑模式合一）
 *
 * ViewType:
 *   TYPE_HEADER (0) - 首字母分组标题行（占满整行）
 *   TYPE_APP    (1) - 普通应用/功能格子
 *
 * 编辑模式：editMode=true 时，每个格子左上显示"加入主屏"按钮，右上显示"卸载"按钮
 * 两个按钮直接叠加在图标上方，不改变 item 高度，不重建 Adapter，无跳动
 */
public class AppAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_APP    = 1;

    // ---- 数据模型 ----
    public static class AppItem {
        public String type;  // "app","url","add_web","add_app","cc_settings","about","custom_app","letter_header"
        public android.content.pm.ResolveInfo info;
        public String title;
        public String url;
        public String browserPkg;
        public String packageName;
        public String className;
        public android.graphics.drawable.Drawable cachedIcon;
        public String cachedLabel;
        // 字母头专用
        public String headerLetter;
    }

    // ---- 编辑回调 ----
    public interface EditCallback {
        void onAddToHome(AppItem item);
        void onUninstall(AppItem item);
        boolean isSystemApp(AppItem item);
    }

    // ---- 普通点击回调 ----
    public interface OnClick {
        void onItemClick(AppItem item);
        void onItemLongClick(AppItem item, int adapterPos);
    }

    private final Context ctx;
    private final List<AppItem> list;
    private final boolean isMainScreen;
    private final OnClick clickListener;
    private EditCallback editCallback;

    /** 当前是否处于编辑模式 */
    private boolean editMode = false;
    private int spanCount = 4;

    public AppAdapter(Context c, List<AppItem> items, boolean isMain, OnClick l) {
        ctx = c;
        list = items;
        isMainScreen = isMain;
        clickListener = l;
    }

    public void setEditCallback(EditCallback cb) {
        this.editCallback = cb;
    }

    /** 切换编辑模式，刷新列表（无动画，不重建Adapter） */
    public void setEditMode(boolean edit) {
        if (this.editMode == edit) return;
        this.editMode = edit;
        notifyDataSetChanged();
    }

    public boolean isEditMode() {
        return editMode;
    }

    @Override
    public int getItemViewType(int position) {
        return "letter_header".equals(list.get(position).type) ? TYPE_HEADER : TYPE_APP;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_header_letter, parent, false);
            return new HeaderHolder(v);
        }
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_app, parent, false);
        return new NormalHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        AppItem item = list.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).letter.setText(item.headerLetter);
            return;
        }
        bindNormal((NormalHolder) holder, item, position);
    }

    private void bindNormal(final NormalHolder h, final AppItem item, final int position) {
        // 绑定图标和标签
        switch (item.type) {
            case "app":
                h.icon.setImageDrawable(item.cachedIcon);
                h.label.setText(item.cachedLabel);
                break;
            case "url":
                h.icon.setImageResource(R.drawable.baseline_language_24);
                h.label.setText(item.title);
                break;
            case "add_web":
                h.icon.setImageResource(R.drawable.baseline_add_circle_outline_24);
                h.label.setText("添加网页");
                break;
            case "add_app":
                h.icon.setImageResource(R.drawable.baseline_apps_24);
                h.label.setText("添加应用");
                break;
            case "cc_settings":
                h.icon.setImageResource(R.drawable.baseline_settings_24);
                h.label.setText("控制中心设置");
                break;
            case "about":
                h.icon.setImageResource(R.drawable.baseline_info_24);
                h.label.setText("关于");
                break;
            case "custom_app":
                h.icon.setImageResource(R.drawable.baseline_android_24);
                h.label.setText(item.title);
                break;
        }

        // 编辑模式：只有 "app" 类型才显示操作按钮
        boolean canEdit = editMode && "app".equals(item.type) && !isMainScreen;

        if (h.btnAdd != null) {
            h.btnAdd.setVisibility(canEdit ? View.VISIBLE : View.GONE);
        }
        if (h.btnUninstall != null) {
            h.btnUninstall.setVisibility(canEdit ? View.VISIBLE : View.GONE);
        }

        if (canEdit && editCallback != null) {
            boolean isSystem = editCallback.isSystemApp(item);

            h.btnAdd.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (editCallback != null) editCallback.onAddToHome(item);
                }
            });

            if (isSystem) {
                h.btnUninstall.setAlpha(0.3f);
                h.btnUninstall.setEnabled(false);
                h.btnUninstall.setOnClickListener(null);
            } else {
                h.btnUninstall.setAlpha(1f);
                h.btnUninstall.setEnabled(true);
                h.btnUninstall.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (editCallback != null) editCallback.onUninstall(item);
                    }
                });
            }
        }

        // 点击：编辑模式下普通点击无效
        h.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!editMode && clickListener != null) {
                    clickListener.onItemClick(item);
                }
            }
        });

        // 长按：主屏应用长按移除，全部应用长按进入编辑模式
        h.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (clickListener != null) {
                    clickListener.onItemLongClick(item, position);
                }
                return true;
            }
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public void attachToRecyclerView(RecyclerView rv) {
        if (rv.getLayoutManager() instanceof GridLayoutManager) {
            GridLayoutManager glm = (GridLayoutManager) rv.getLayoutManager();
            spanCount = glm.getSpanCount();
            glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    if (position < list.size() && getItemViewType(position) == TYPE_HEADER) {
                        return spanCount;
                    }
                    return 1;
                }
            });
        }
    }

    // ---- ViewHolder ----

    static class HeaderHolder extends RecyclerView.ViewHolder {
        TextView letter;
        HeaderHolder(View v) {
            super(v);
            letter = v.findViewById(R.id.header_letter);
        }
    }

    static class NormalHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView  label;
        ImageView btnAdd;
        ImageView btnUninstall;

        NormalHolder(View v) {
            super(v);
            icon         = v.findViewById(R.id.icon);
            label        = v.findViewById(R.id.label);
            btnAdd       = v.findViewById(R.id.btn_add_to_home);
            btnUninstall = v.findViewById(R.id.btn_uninstall);
        }
    }
}
