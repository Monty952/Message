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
import com.example.message.database.ScheduledMessageEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ViewHolder> {

    private final Context context;
    private List<ScheduledMessageEntity> list = new ArrayList<>();
    private final OnScheduleActionListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy 'at' hh:mm a", Locale.getDefault());

    public interface OnScheduleActionListener {
        void onDelete(ScheduledMessageEntity entity);
    }

    public ScheduleAdapter(Context context, OnScheduleActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setList(List<ScheduledMessageEntity> list) {
        this.list = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_schedule_content, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ScheduledMessageEntity item = list.get(position);

        String recipient = !TextUtils.isEmpty(item.getName()) ? item.getName() : item.getAddress();
        holder.tvRecipient.setText(recipient);
        holder.tvMessage.setText(item.getMessage());

        String initial = !TextUtils.isEmpty(recipient) ? String.valueOf(recipient.charAt(0)).toUpperCase() : "?";
        holder.tvAvatar.setText(initial);

        holder.tvScheduledTime.setText(holder.itemView.getContext().getString(R.string.sends_format, dateFormat.format(new Date(item.getScheduledTimestamp()))));

        holder.ivDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(item);
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvAvatar, tvRecipient, tvMessage, tvScheduledTime;
        ImageView ivDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAvatar = itemView.findViewById(R.id.tvAvatar);
            tvRecipient = itemView.findViewById(R.id.tvRecipient);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvScheduledTime = itemView.findViewById(R.id.tvScheduledTime);
            ivDelete = itemView.findViewById(R.id.ivDelete);
        }
    }
}
