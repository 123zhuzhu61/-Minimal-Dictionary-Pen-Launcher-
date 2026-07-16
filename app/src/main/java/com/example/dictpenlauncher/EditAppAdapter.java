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
 * 编辑模式适配器
 * 同样支持字母分组头（TYPE_HEADER / TYPE_APP）
 */
public class EditAppAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_APP    = 1;

    private final Context ctx;
    private final List<AppAdapter.AppItem> appList;
    private final EditCallback callback;
    private int spanCount = 4;

    public interface EditCallback {
        void onAddToHome(AppAdapter.AppItem item);
        void onUninstall(AppAdapter.AppItem item);
        boolean isSystemApp(AppAdapter.AppItem item);
    }

    public EditAppAdapter(Context c, List<AppAdapter.AppItem> list, EditCallback cb) {
        ctx = c;
        appList = list;
        callback = cb;
    }

    @Override
    public int getItemViewType(int position) {
        return "letter_header".equals(appList.get(position).type) ? TYPE_HEADER : TYPE_APP;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_header_letter, parent, false);
            return new HeaderHolder(v);
        }
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_edit_app, parent, false);
        return new EditHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        AppAdapter.AppItem item = appList.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).letter.setText(item.headerLetter);
            return;
        }
        EditHolder eh = (EditHolder) holder;
        eh.icon.setImageDrawable(item.cachedIcon);
        eh.label.setText(item.cachedLabel);

        boolean isSystem = callback.isSystemApp(item);

        eh.btnAdd.setOnClickListener(v -> callback.onAddToHome(item));

        if (isSystem) {
            eh.btnUninstall.setEnabled(false);
            eh.btnUninstall.setAlpha(0.4f);
        } else {
            eh.btnUninstall.setEnabled(true);
            eh.btnUninstall.setAlpha(1.0f);
            eh.btnUninstall.setOnClickListener(v -> callback.onUninstall(item));
        }
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    public void attachToRecyclerView(RecyclerView rv) {
        if (rv.getLayoutManager() instanceof GridLayoutManager) {
            GridLayoutManager glm = (GridLayoutManager) rv.getLayoutManager();
            spanCount = glm.getSpanCount();
            glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    if (position < appList.size()
                            && getItemViewType(position) == TYPE_HEADER) {
                        return spanCount;
                    }
                    return 1;
                }
            });
        }
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        TextView letter;
        HeaderHolder(View v) {
            super(v);
            letter = v.findViewById(R.id.header_letter);
        }
    }

    static class EditHolder extends RecyclerView.ViewHolder {
        ImageView icon, btnAdd, btnUninstall;
        TextView label;
        EditHolder(View v) {
            super(v);
            icon         = v.findViewById(R.id.edit_icon);
            label        = v.findViewById(R.id.edit_label);
            btnAdd       = v.findViewById(R.id.btn_add_to_home);
            btnUninstall = v.findViewById(R.id.btn_uninstall);
        }
    }
}
