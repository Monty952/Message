package com.example.message.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.message.R;
import com.example.message.models.Conversation;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {

    private static final int[] AVATAR_COLORS = {
            0xFF1ABC9C, 0xFF2ECC71, 0xFF3498DB, 0xFF9B59B6, 0xFF16A085,
            0xFF27AE60, 0xFF2980B9, 0xFF8E44AD, 0xFFF39C12, 0xFFE74C3C,
            0xFF1A237E, 0xFF006064, 0xFF4E342E, 0xFF37474F, 0xFF558B2F
    };

    private final Context context;
    private List<Conversation> conversations = new ArrayList<>();
    private OnConversationClickListener listener;

    // Multi-select state
    private boolean isSelectionMode = false;
    private final Set<Long> selectedIds = new HashSet<>();

    public interface OnConversationClickListener {
        void onClick(Conversation conversation);
        void onLongClick(Conversation conversation);
    }

    public ConversationAdapter(Context context) {
        this.context = context;
    }

    public void setListener(OnConversationClickListener listener) {
        this.listener = listener;
    }

    public void setConversations(List<Conversation> conversations) {
        final List<Conversation> oldList = this.conversations;
        final List<Conversation> newList = conversations != null ? conversations : new ArrayList<>();

        if (oldList == newList) {
            notifyDataSetChanged();
            return;
        }

        // Rebind only conversations that were added, removed, moved or changed. notifyDataSetChanged()
        // redrew and reloaded the avatars of every visible row on each refresh, even when nothing changed.
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldList.size();
            }

            @Override
            public int getNewListSize() {
                return newList.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldList.get(oldItemPosition).getThreadId() == newList.get(newItemPosition).getThreadId();
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return isSameContent(oldList.get(oldItemPosition), newList.get(newItemPosition));
            }
        });

        this.conversations = newList;
        diff.dispatchUpdatesTo(this);
    }

    private static boolean isSameContent(Conversation a, Conversation b) {
        return a.getDate() == b.getDate()
                && a.isRead() == b.isRead()
                && a.getMessageCount() == b.getMessageCount()
                && Objects.equals(a.getAddress(), b.getAddress())
                && Objects.equals(a.getContactName(), b.getContactName())
                && Objects.equals(a.getSnippet(), b.getSnippet())
                && Objects.equals(a.getContactPhotoUri(), b.getContactPhotoUri());
    }

    public List<Conversation> getConversations() {
        return conversations;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_conversation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Conversation conv = conversations.get(position);

        // Name: prefer contactName, fall back to address
        String name = (conv.getContactName() != null && !conv.getContactName().isEmpty())
                ? conv.getContactName()
                : conv.getAddress();
        holder.tvName.setText(name);

        // Preview snippet
        holder.tvPreview.setText(conv.getSnippet() != null ? conv.getSnippet() : "");

        // Date formatting
        holder.tvDate.setText(formatDate(conv.getDate()));

        // Unread badge
        int unreadCount = conv.getUnreadCount();
        if (unreadCount > 0) {
            holder.tvUnreadCount.setVisibility(View.VISIBLE);
            holder.tvUnreadCount.setText(unreadCount > 99 ? "99+" : String.valueOf(unreadCount));
            holder.unreadDot.setVisibility(View.VISIBLE);
        } else {
            holder.tvUnreadCount.setVisibility(View.GONE);
            holder.unreadDot.setVisibility(View.GONE);
        }

        // Avatar initials + background color
        String initial = name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase();
        holder.tvInitials.setText(initial);
        int colorIndex = Math.abs(name.hashCode()) % AVATAR_COLORS.length;
        holder.tvInitials.getBackground().setTint(AVATAR_COLORS[colorIndex]);

        // Contact photo (if available via Glide)
        if (conv.getContactPhotoUri() != null && !conv.getContactPhotoUri().isEmpty()) {
            holder.ivAvatar.setVisibility(View.VISIBLE);
            holder.tvInitials.setVisibility(View.GONE);
            try {
                com.bumptech.glide.Glide.with(context)
                        .load(conv.getContactPhotoUri())
                        .circleCrop()
                        .placeholder(R.drawable.ic_round_bg)
                        .into(holder.ivAvatar);
            } catch (Exception e) {
                holder.ivAvatar.setVisibility(View.GONE);
                holder.tvInitials.setVisibility(View.VISIBLE);
            }
        } else {
            holder.ivAvatar.setVisibility(View.GONE);
            holder.tvInitials.setVisibility(View.VISIBLE);
        }

        // Selection overlay
        boolean isSelected = selectedIds.contains(conv.getThreadId());
        holder.ivSelected.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        holder.itemView.setActivated(isSelected);

        // Click listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(conv);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onLongClick(conv);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    // ── Selection helpers ────────────────────────────────────────────────────

    public void toggleSelection(long threadId) {
        if (selectedIds.contains(threadId)) {
            selectedIds.remove(threadId);
        } else {
            selectedIds.add(threadId);
        }
        notifyDataSetChanged();
    }

    public void setSelectionMode(boolean enabled) {
        isSelectionMode = enabled;
        if (!enabled) {
            selectedIds.clear();
        }
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    public Set<Long> getSelectedIds() {
        return new HashSet<>(selectedIds);
    }

    public int getSelectedCount() {
        return selectedIds.size();
    }

    public void clearSelection() {
        selectedIds.clear();
        isSelectionMode = false;
        notifyDataSetChanged();
    }

    // ── Date formatting ──────────────────────────────────────────────────────

    private String formatDate(long timestamp) {
        if (timestamp <= 0) return "";

        Calendar now = Calendar.getInstance();
        Calendar msgCal = Calendar.getInstance();
        msgCal.setTimeInMillis(timestamp);

        boolean isToday = now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR);

        if (isToday) {
            // Show time HH:mm
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(timestamp));
        }

        // Check if same week
        Calendar weekStart = Calendar.getInstance();
        weekStart.set(Calendar.DAY_OF_WEEK, weekStart.getFirstDayOfWeek());
        weekStart.set(Calendar.HOUR_OF_DAY, 0);
        weekStart.set(Calendar.MINUTE, 0);
        weekStart.set(Calendar.SECOND, 0);
        weekStart.set(Calendar.MILLISECOND, 0);

        if (msgCal.getTimeInMillis() >= weekStart.getTimeInMillis()) {
            // Show day name: Mon, Tue, etc.
            return new SimpleDateFormat("EEE", Locale.getDefault()).format(new Date(timestamp));
        }

        // Show date: dd/MM/yy
        return new SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(new Date(timestamp));
    }

    // ── ViewHolder ───────────────────────────────────────────────────────────

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar, ivSelected;
        TextView tvInitials, tvName, tvPreview, tvDate, tvUnreadCount;
        View unreadDot;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            ivSelected = itemView.findViewById(R.id.ivSelected);
            tvInitials = itemView.findViewById(R.id.tvInitials);
            tvName = itemView.findViewById(R.id.tvName);
            tvPreview = itemView.findViewById(R.id.tvPreview);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvUnreadCount = itemView.findViewById(R.id.tvUnreadCount);
            unreadDot = itemView.findViewById(R.id.unread_dot);
        }
    }
}
