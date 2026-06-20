/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.lineage.trust;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.R;

import java.util.ArrayList;
import java.util.List;

class HiddenDrawerAppsAdapter extends RecyclerView.Adapter<HiddenDrawerAppsAdapter.ViewHolder> {

    interface Listener {
        void onItemClicked(@NonNull HiddenDrawerItem item);
    }

    private final List<HiddenDrawerItem> mList = new ArrayList<>();
    private final Listener mListener;

    HiddenDrawerAppsAdapter(@NonNull Listener listener) {
        mListener = listener;
    }

    void setItems(@NonNull List<HiddenDrawerItem> items) {
        mList.clear();
        mList.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_hidden_drawer_app, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HiddenDrawerItem item = mList.get(position);
        holder.icon.setImageDrawable(item.getIcon());
        holder.title.setText(item.getLabel());
        holder.itemView.setOnClickListener(v -> mListener.onItemClicked(item));
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.hidden_drawer_item_icon);
            title = itemView.findViewById(R.id.hidden_drawer_item_title);
        }
    }
}
