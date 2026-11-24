package com.example.message.adapters;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.message.R;
import com.example.message.database.RecycleBinEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecycleBinAdapter extends RecyclerView.Adapter<RecycleBinAdapter.ViewHolder> {

    private final Context context;
    private List<RecycleBinEntity> list = new ArrayList<>();
    private final OnBinActionListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

    public interface OnBinActionListener {
        void onRestore(RecycleBinEntity entity);
        void onDeletePermanently(RecycleBinEntity entity);
    }

    public RecycleBinAdapter(Context context, OnBinActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setList(List<RecycleBinEntity> list) {
        this.list = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_conversation_bin, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RecycleBinEntity item = list.get(position);

        String title = !TextUtils.isEmpty(item.getName()) ? item.getName() : item.getAddress();
        holder.tvName.setText(title);
        holder.tvSnippet.setText(item.getSnippet() != null ? item.getSnippet() : "");

        String initial = !TextUtils.isEmpty(title) ? String.valueOf(title.charAt(0)).toUpperCase() : "?";
        holder.tvAvatar.setText(initial);

        long daysAgo = (System.currentTimeMillis() - item.getDeletedAt()) / (1000 * 60 * 60 * 24);
        Context ctx = holder.itemView.getContext();
        String timeAgo = daysAgo == 0 ? ctx.getString(R.string.today) : ctx.getString(R.string.days_ago_format, daysAgo);
        holder.tvDeletedAt.setText(ctx.getString(R.string.deleted_at_format, timeAgo, dateFormat.format(new Date(item.getDeletedAt()))));

        holder.ivRestore.setOnClickListener(v -> {
            if (listener != null) listener.onRestore(item);
        });

        holder.ivDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDeletePermanently(item);
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvAvatar, tvName, tvSnippet, tvDeletedAt;
        ImageView ivRestore, ivDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAvatar = itemView.findViewById(R.id.tvAvatar);
            tvName = itemView.findViewById(R.id.tvName);
            tvSnippet = itemView.findViewById(R.id.tvSnippet);
            tvDeletedAt = itemView.findViewById(R.id.tvDeletedAt);
            ivRestore = itemView.findViewById(R.id.ivRestore);
            ivDelete = itemView.findViewById(R.id.ivDelete);
        }
    }
}
