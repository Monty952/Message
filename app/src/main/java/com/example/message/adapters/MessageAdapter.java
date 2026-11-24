package com.example.message.adapters;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Telephony;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.message.R;
import com.example.message.models.MessageItem;
import com.example.message.utils.MmsHelper;
import com.example.message.utils.OtpHelper;

import java.io.File;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_INCOMING = 0;
    private static final int TYPE_OUTGOING = 1;
    private static final int TYPE_DATE_HEADER = 2;

    private final Context context;
    private final List<MessageItem> messages;
    private OnMessageActionListener actionListener;

    public interface OnMessageActionListener {
        void onCopy(MessageItem item);
        void onForward(MessageItem item);
        void onDelete(MessageItem item);
        void onRetry(MessageItem item);
        void onDownloadMms(MessageItem item);
    }

    public MessageAdapter(Context context, List<MessageItem> messages) {
        this.context = context;
        this.messages = messages != null ? messages : new ArrayList<>();
    }

    public void setActionListener(OnMessageActionListener listener) {
        this.actionListener = listener;
    }

    public void setMessages(List<MessageItem> newMessages) {
        final List<MessageItem> oldList = new ArrayList<>(messages);
        final List<MessageItem> newList = newMessages != null ? new ArrayList<>(newMessages) : new ArrayList<>();

        // Apply only the real differences (new, deleted or changed messages). Every refresh, including the ones
        // triggered by the SMS observer, used to redraw the whole conversation with notifyDataSetChanged().
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
                MessageItem o = oldList.get(oldItemPosition);
                MessageItem n = newList.get(newItemPosition);
                if (o.isDateHeader() || n.isDateHeader()) {
                    return o.isDateHeader() && n.isDateHeader() && Objects.equals(o.getBody(), n.getBody());
                }
                // SMS and MMS ids are separate sequences and can collide
                return o.getId() == n.getId() && o.isMms() == n.isMms();
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                MessageItem o = oldList.get(oldItemPosition);
                MessageItem n = newList.get(newItemPosition);
                return o.getType() == n.getType()
                        && o.getDate() == n.getDate()
                        && o.getStatus() == n.getStatus()
                        && Objects.equals(o.getBody(), n.getBody())
                        && Objects.equals(o.getAttachmentUri(), n.getAttachmentUri())
                        && o.isDownloadPending() == n.isDownloadPending();
            }
        });

        messages.clear();
        messages.addAll(newList);
        diff.dispatchUpdatesTo(this);
    }

    public void addMessage(MessageItem item) {
        messages.add(item);
        notifyItemInserted(messages.size() - 1);
    }

    @Override
    public int getItemViewType(int position) {
        MessageItem item = messages.get(position);
        if (item.isDateHeader()) return TYPE_DATE_HEADER;
        return item.isIncoming() ? TYPE_INCOMING : TYPE_OUTGOING;
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        switch (viewType) {
            case TYPE_INCOMING:
                return new IncomingViewHolder(inflater.inflate(R.layout.item_message_in, parent, false));
            case TYPE_OUTGOING:
                return new OutgoingViewHolder(inflater.inflate(R.layout.item_message_out, parent, false));
            default:
                return new DateHeaderViewHolder(inflater.inflate(R.layout.item_date_header, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MessageItem item = messages.get(position);
        if (holder instanceof IncomingViewHolder) {
            bindIncoming((IncomingViewHolder) holder, item);
        } else if (holder instanceof OutgoingViewHolder) {
            bindOutgoing((OutgoingViewHolder) holder, item);
        } else if (holder instanceof DateHeaderViewHolder) {
            ((DateHeaderViewHolder) holder).tvDateHeader.setText(item.getBody());
        }
    }

    private void bindIncoming(IncomingViewHolder vh, MessageItem item) {
        renderMediaAttachment(vh.bubbleIn, vh.tvMessageIn, item);
        vh.tvTimeIn.setText(formatTime(item.getDate()));
        detectOtp(item.getBody(), vh.tvOtpPill);
        setupLongPress(vh.itemView, item);
    }

    private void bindOutgoing(OutgoingViewHolder vh, MessageItem item) {
        renderMediaAttachment(vh.bubbleOut, vh.tvMessageOut, item);
        vh.tvTimeOut.setText(formatTime(item.getDate()));
        detectOtp(item.getBody(), vh.tvOtpPillOut);
        setupLongPress(vh.itemView, item);

        if (item.getType() == Telephony.Sms.MESSAGE_TYPE_FAILED) {
            vh.ivStatus.setImageResource(R.drawable.ic_msg_fail);
            vh.ivStatus.setImageAlpha(255);
            vh.ivStatus.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onRetry(item);
            });
            vh.itemView.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onRetry(item);
            });
        } else if (item.getType() == Telephony.Sms.MESSAGE_TYPE_OUTBOX) {
            vh.ivStatus.setImageResource(R.drawable.ic_msg_sent);
            vh.ivStatus.setImageAlpha(128);
            vh.ivStatus.setOnClickListener(null);
            vh.itemView.setOnClickListener(null);
        } else {
            vh.ivStatus.setImageResource(R.drawable.ic_msg_sent);
            vh.ivStatus.setImageAlpha(255);
            vh.ivStatus.setOnClickListener(null);
            vh.itemView.setOnClickListener(null);
        }
    }

    private void renderMediaAttachment(FrameLayout bubble, TextView tvMessage, MessageItem item) {
        ImageView ivMedia = bubble.findViewWithTag("mms_media_image");
        if (ivMedia == null) {
            ivMedia = new ImageView(context);
            ivMedia.setTag("mms_media_image");
            float density = context.getResources().getDisplayMetrics().density;
            int width = (int) (220 * density);
            int height = (int) (180 * density);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height);
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            lp.bottomMargin = (int) (2 * density);
            ivMedia.setLayoutParams(lp);
            ivMedia.setScaleType(ImageView.ScaleType.CENTER_CROP);
            ivMedia.setClipToOutline(true);
            bubble.addView(ivMedia, 0);
        }

        if (item.hasAttachment()) {
            Log.d("MMS_DEBUG", "MessageAdapter: Binding MMS attachment for message id=" + item.getId()
                    + ", URI=" + item.getAttachmentUri() + ", type=" + item.getAttachmentType());
            ivMedia.setVisibility(View.VISIBLE);
            MmsHelper.loadMmsImage(context, item.getAttachmentUri(), ivMedia);

            final String attUri = item.getAttachmentUri();
            final String attType = item.getAttachmentType();
            ivMedia.setOnClickListener(v -> {
                try {
                    Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                    Uri uriToOpen;
                    File cachedFile = MmsHelper.getCachedPartFile(context, attUri);
                    if (cachedFile != null && cachedFile.exists()) {
                        uriToOpen = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", cachedFile);
                    } else if (attUri.startsWith("file://") || attUri.startsWith("/")) {
                        File f = attUri.startsWith("file://") ? new File(Uri.parse(attUri).getPath()) : new File(attUri);
                        uriToOpen = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", f);
                    } else {
                        uriToOpen = Uri.parse(attUri);
                    }
                    viewIntent.setDataAndType(uriToOpen, attType != null ? attType : "image/*");
                    viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(viewIntent);
                } catch (Exception e) {
                    Toast.makeText(context, R.string.not_available, Toast.LENGTH_SHORT).show();
                }
            });

            String body = item.getBody();
            String placeholder = context.getString(R.string.mms_message);
            String photoStr = context.getString(R.string.mms_photo);
            String videoStr = context.getString(R.string.mms_video);
            String audioStr = context.getString(R.string.mms_audio);

            if (body == null || body.trim().isEmpty()
                    || body.equalsIgnoreCase(placeholder)
                    || body.equalsIgnoreCase(photoStr)
                    || body.equalsIgnoreCase(videoStr)
                    || body.equalsIgnoreCase(audioStr)) {
                tvMessage.setVisibility(View.GONE);
            } else {
                tvMessage.setVisibility(View.VISIBLE);
                tvMessage.setText(body);
                FrameLayout.LayoutParams tLp = (FrameLayout.LayoutParams) tvMessage.getLayoutParams();
                tLp.topMargin = (int) (180 * context.getResources().getDisplayMetrics().density);
                tvMessage.setLayoutParams(tLp);
            }

        } else if (item.isDownloadPending()) {
            Log.d("MMS_DEBUG", "MessageAdapter: MMS message id=" + item.getId() + " is pending download");
            ivMedia.setVisibility(View.GONE);
            tvMessage.setVisibility(View.VISIBLE);
            tvMessage.setText(R.string.tap_to_download_mms);
            FrameLayout.LayoutParams tLp = (FrameLayout.LayoutParams) tvMessage.getLayoutParams();
            tLp.topMargin = 0;
            tvMessage.setLayoutParams(tLp);
            tvMessage.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onDownloadMms(item);
                }
            });

        } else {
            ivMedia.setVisibility(View.GONE);
            tvMessage.setVisibility(View.VISIBLE);
            tvMessage.setText(item.getBody());
            FrameLayout.LayoutParams tLp = (FrameLayout.LayoutParams) tvMessage.getLayoutParams();
            tLp.topMargin = 0;
            tvMessage.setLayoutParams(tLp);
            tvMessage.setOnClickListener(null);
        }
    }

    private void detectOtp(String body, TextView otpPill) {
        if (body == null) {
            otpPill.setVisibility(View.GONE);
            return;
        }
        String otp = OtpHelper.extractOtp(body);
        if (otp != null && !otp.isEmpty()) {
            otpPill.setText(context.getString(R.string.copy_otp_format, otp));
            otpPill.setVisibility(View.VISIBLE);
            otpPill.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("OTP", otp));
                    Toast.makeText(context, context.getString(R.string.otp_copied, otp), Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            otpPill.setVisibility(View.GONE);
        }
    }

    private void setupLongPress(View view, MessageItem item) {
        view.setOnLongClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, v);
            popup.getMenu().add(0, 0, 0, "Copy");
            popup.getMenu().add(0, 1, 1, "Forward");
            popup.getMenu().add(0, 2, 2, "Delete");
            popup.setOnMenuItemClickListener(menuItem -> {
                if (actionListener == null) return false;
                switch (menuItem.getItemId()) {
                    case 0: actionListener.onCopy(item); return true;
                    case 1: actionListener.onForward(item); return true;
                    case 2: actionListener.onDelete(item); return true;
                }
                return false;
            });
            popup.show();
            return true;
        });
    }

    private String formatTime(long millis) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    // --- ViewHolders ---

    static class IncomingViewHolder extends RecyclerView.ViewHolder {
        FrameLayout bubbleIn;
        TextView tvOtpPill, tvMessageIn, tvTimeIn;
        IncomingViewHolder(View v) {
            super(v);
            bubbleIn    = v.findViewById(R.id.bubbleIn);
            tvOtpPill   = v.findViewById(R.id.tvOtpPill);
            tvMessageIn = v.findViewById(R.id.tvMessageIn);
            tvTimeIn    = v.findViewById(R.id.tvTimeIn);
        }
    }

    static class OutgoingViewHolder extends RecyclerView.ViewHolder {
        FrameLayout bubbleOut;
        TextView tvOtpPillOut, tvMessageOut, tvTimeOut;
        ImageView ivStatus;
        OutgoingViewHolder(View v) {
            super(v);
            bubbleOut    = v.findViewById(R.id.bubbleOut);
            tvOtpPillOut = v.findViewById(R.id.tvOtpPillOut);
            tvMessageOut = v.findViewById(R.id.tvMessageOut);
            tvTimeOut    = v.findViewById(R.id.tvTimeOut);
            ivStatus     = v.findViewById(R.id.ivStatus);
        }
    }

    static class DateHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvDateHeader;
        DateHeaderViewHolder(View v) {
            super(v);
            tvDateHeader = v.findViewById(R.id.tvDateHeader);
        }
    }
}
