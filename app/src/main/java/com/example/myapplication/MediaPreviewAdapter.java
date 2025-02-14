package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.List;

public class MediaPreviewAdapter extends RecyclerView.Adapter<MediaPreviewAdapter.ViewHolder> {
    private List<Uri> mediaList;
    private Context context;

    public MediaPreviewAdapter(Context context, List<Uri> mediaList) {
        this.context = context;
        this.mediaList = mediaList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_media_preview, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Uri mediaUri = mediaList.get(position);
        String mimeType = context.getContentResolver().getType(mediaUri);
        boolean isVideo = mimeType != null && mimeType.startsWith("video");

        if (isVideo) {
            holder.playIcon.setVisibility(View.VISIBLE);
            // Load video thumbnail
            Glide.with(context)
                    .load(mediaUri)
                    .thumbnail(0.1f)
                    .into(holder.mediaPreview);
        } else {
            holder.playIcon.setVisibility(View.GONE);
            // Load image
            Glide.with(context)
                    .load(mediaUri)
                    .into(holder.mediaPreview);
        }

        // Add click listener for remove button
        holder.removeButton.setOnClickListener(v -> {
            int adapterPosition = holder.getAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION) {
                mediaList.remove(adapterPosition);
                notifyItemRemoved(adapterPosition);
                // Update status text if needed
                if (context instanceof MainActivity) {
                    ((MainActivity) context).updateStatus(mediaList.size() + " files selected");
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return mediaList.size();
    }

    public void updateMedia(List<Uri> newMediaList) {
        this.mediaList.clear();
        this.mediaList.addAll(newMediaList);
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView mediaPreview;
        ImageView playIcon;
        ImageView removeButton;

        ViewHolder(View itemView) {
            super(itemView);
            mediaPreview = itemView.findViewById(R.id.mediaPreview);
            playIcon = itemView.findViewById(R.id.playIcon);
            removeButton = itemView.findViewById(R.id.removeButton);
        }
    }
} 