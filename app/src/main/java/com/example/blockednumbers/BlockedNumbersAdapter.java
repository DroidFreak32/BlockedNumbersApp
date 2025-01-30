package com.example.blockednumbers;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageButton;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class BlockedNumbersAdapter extends RecyclerView.Adapter<BlockedNumbersAdapter.ViewHolder> {
    private final List<String> blockedNumbers;
    private final OnDeleteClickListener deleteClickListener;

    public interface OnDeleteClickListener {
        void onDeleteClick(int position);
    }

    public BlockedNumbersAdapter(List<String> blockedNumbers, OnDeleteClickListener listener) {
        this.blockedNumbers = blockedNumbers;
        this.deleteClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_blocked_number, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String number = blockedNumbers.get(position);
        holder.tvNumber.setText(number);

        holder.btnDelete.setOnClickListener(v -> {
            if (deleteClickListener != null) {
                deleteClickListener.onDeleteClick(holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return blockedNumbers.size();
    }

    // Update ViewHolder class
    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNumber;
        ImageButton btnDelete;

        public ViewHolder(View view) {
            super(view);
            tvNumber = view.findViewById(R.id.tvNumber);
            btnDelete = view.findViewById(R.id.btnDelete);
        }
    }
}