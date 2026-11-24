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
import com.example.message.database.BlockedNumberEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BlockedNumberAdapter extends RecyclerView.Adapter<BlockedNumberAdapter.ViewHolder> {

    private final Context context;
    private List<BlockedNumberEntity> list = new ArrayList<>();
    private final OnUnblockClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

    public interface OnUnblockClickListener {
        void onUnblock(BlockedNumberEntity entity);
    }

    public BlockedNumberAdapter(Context context, OnUnblockClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setList(List<BlockedNumberEntity> list) {
        this.list = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_block_number, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BlockedNumberEntity item = list.get(position);
        String title = !TextUtils.isEmpty(item.getContactName()) ? item.getContactName() + " (" + item.getNumber() + ")" : item.getNumber();
        holder.tvNumber.setText(title);

        String initial = !TextUtils.isEmpty(item.getContactName()) ? String.valueOf(item.getContactName().charAt(0)).toUpperCase() : (!TextUtils.isEmpty(item.getNumber()) ? String.valueOf(item.getNumber().charAt(0)) : "#");
        holder.tvAvatar.setText(initial);

        holder.tvBlockedDate.setText(holder.itemView.getContext().getString(R.string.blocked_on_date, dateFormat.format(new Date(item.getBlockedAt()))));

        holder.ivUnblock.setOnClickListener(v -> {
            if (listener != null) {
                listener.onUnblock(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvAvatar, tvNumber, tvBlockedDate;
        ImageView ivUnblock;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAvatar = itemView.findViewById(R.id.tvAvatar);
            tvNumber = itemView.findViewById(R.id.tvNumber);
            tvBlockedDate = itemView.findViewById(R.id.tvBlockedDate);
            ivUnblock = itemView.findViewById(R.id.ivUnblock);
        }
    }
}
