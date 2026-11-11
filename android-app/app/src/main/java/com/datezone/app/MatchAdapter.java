package com.datezone.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MatchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnMatchActionListener {
        void onCardClick(MatchItem item);
        void onPassClick(MatchItem item, int position);
        void onLikeClick(MatchItem item, int position);
    }

    private final List<MatchItem> items;
    private final OnMatchActionListener actionListener;

    public MatchAdapter(List<MatchItem> items, OnMatchActionListener actionListener) {
        this.items = items;
        this.actionListener = actionListener;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == MatchItem.TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_match_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_match_card, parent, false);
            return new CardViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MatchItem item = items.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).tvHeader.setText(item.getHeaderTitle());
        } else if (holder instanceof CardViewHolder) {
            CardViewHolder cardHolder = (CardViewHolder) holder;
            cardHolder.tvName.setText(item.getName());
            if (item.getPhotoUrl() != null) com.datezone.app.api.RemoteImages.load(cardHolder.ivPhoto, item.getPhotoUrl());
            else cardHolder.ivPhoto.setImageResource(item.getPhotoResId());

            cardHolder.itemView.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onCardClick(item);
                }
            });

            cardHolder.btnPass.setOnClickListener(v -> {
                if (actionListener != null) {
                    int pos = cardHolder.getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        actionListener.onPassClick(item, pos);
                    }
                }
            });

            cardHolder.btnLike.setOnClickListener(v -> {
                if (actionListener != null) {
                    int pos = cardHolder.getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        actionListener.onLikeClick(item, pos);
                    }
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvHeader;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvHeader = itemView.findViewById(R.id.tvMatchHeaderTitle);
        }
    }

    public static class CardViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPhoto;
        TextView tvName;
        View btnPass;
        View btnLike;

        public CardViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPhoto = itemView.findViewById(R.id.ivMatchPhoto);
            tvName = itemView.findViewById(R.id.tvMatchName);
            btnPass = itemView.findViewById(R.id.btnMatchPass);
            btnLike = itemView.findViewById(R.id.btnMatchLike);
        }
    }
}
