package com.team8.splitsmart.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** A RecyclerView adapter for one row layout; each screen supplies how a row is bound and clicked. */
public class BindingAdapter<T> extends RecyclerView.Adapter<BindingAdapter.Holder> {

    public interface Binder<T> {
        void bind(View row, T item);
    }

    public interface OnItemClick<T> {
        void onClick(T item);
    }

    private final int layoutRes;
    private final Binder<T> binder;
    private final OnItemClick<T> onClick;
    private final List<T> items = new ArrayList<>();

    public BindingAdapter(int layoutRes, Binder<T> binder, OnItemClick<T> onClick) {
        this.layoutRes = layoutRes;
        this.binder = binder;
        this.onClick = onClick;
    }

    public void setItems(List<T> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        T item = items.get(position);
        binder.bind(holder.itemView, item);
        if (onClick != null) {
            holder.itemView.setOnClickListener(v -> onClick.onClick(item));
        } else {
            holder.itemView.setOnClickListener(null);
            holder.itemView.setClickable(false);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        Holder(View itemView) {
            super(itemView);
        }
    }
}
