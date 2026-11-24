package com.example.message.adapters;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.message.R;
import com.example.message.models.LanguageItem;

import java.util.List;

public class LanguageAdapter extends RecyclerView.Adapter<LanguageAdapter.ViewHolder> {

    public interface OnLanguageSelectedListener {
        void onSelected(LanguageItem item);
    }

    private final Context context;
    private final List<LanguageItem> items;
    private int selectedPos;
    private final OnLanguageSelectedListener listener;

    public LanguageAdapter(Context context, List<LanguageItem> items, int selectedPos, OnLanguageSelectedListener listener) {
        this.context = context;
        this.items = items;
        this.selectedPos = selectedPos;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_language, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LanguageItem item = items.get(position);
        holder.tvLanguage.setText(item.name);
        holder.tvNative.setText(item.nativeName);

        if (item.flagResId != 0) {
            holder.ivFlag.setImageResource(item.flagResId);
        }

        holder.ivCheck.clearColorFilter();
        holder.ivCheck.setAlpha(1f);
        if (position == selectedPos) {
            holder.ivCheck.setImageResource(R.drawable.ic_select);
        } else {
            holder.ivCheck.setImageResource(R.drawable.ic_unselect);
        }

        holder.itemView.setOnClickListener(v -> {
            int prev = selectedPos;
            selectedPos = holder.getAdapterPosition();
            notifyDataSetChanged();
            if (listener != null) {
                listener.onSelected(items.get(selectedPos));
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public int getSelectedPos() {
        return selectedPos;
    }

    public LanguageItem getSelectedItem() {
        if (selectedPos >= 0 && selectedPos < items.size()) {
            return items.get(selectedPos);
        }
        return null;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivFlag, ivCheck;
        TextView tvLanguage, tvNative;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivFlag = itemView.findViewById(R.id.ivFlag);
            ivCheck = itemView.findViewById(R.id.ivCheck);
            tvLanguage = itemView.findViewById(R.id.tvLanguage);
            tvNative = itemView.findViewById(R.id.tvNative);
        }
    }
}
