package com.example.message.adapters;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.message.R;
import com.example.message.models.ContactItem;

import java.util.ArrayList;
import java.util.List;

public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ViewHolder> {

    private final Context context;
    private List<ContactItem> contactList = new ArrayList<>();
    private OnContactClickListener listener;

    public interface OnContactClickListener {
        void onContactClick(ContactItem contact);
    }

    public ContactAdapter(Context context, OnContactClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setContacts(List<ContactItem> list) {
        this.contactList = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_contact_number, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ContactItem item = contactList.get(position);
        holder.tvContactName.setText(item.getName());
        holder.tvPhoneNumber.setText(item.getNumber());

        String initial = (!TextUtils.isEmpty(item.getName())) ? String.valueOf(item.getName().charAt(0)).toUpperCase() : "?";
        holder.tvAvatar.setText(initial);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onContactClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return contactList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvAvatar, tvContactName, tvPhoneNumber;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAvatar = itemView.findViewById(R.id.tvAvatar);
            tvContactName = itemView.findViewById(R.id.tvContactName);
            tvPhoneNumber = itemView.findViewById(R.id.tvPhoneNumber);
        }
    }
}
