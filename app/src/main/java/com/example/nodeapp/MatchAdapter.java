package com.example.nodeapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import java.util.List;

import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.ZoneId;

public class MatchAdapter extends RecyclerView.Adapter<MatchAdapter.MatchViewHolder> {

    public interface OnMatchClickListener {
        void onMatchClick(Fixture match);
    }

    private List<Fixture> matches;
    private OnMatchClickListener listener;

    public MatchAdapter(List<Fixture> matches, OnMatchClickListener listener) {
        this.matches = matches;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MatchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_match, parent, false);
        return new MatchViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull MatchViewHolder holder, int position) {
        Fixture match = matches.get(position);
        holder.nameHome.setText(match.homeName);
        holder.nameAway.setText(match.awayName);
        holder.score.setText(match.homeScore + " - " + match.awayScore);
        holder.matchStatus.setText(match.status);
        holder.matchTime.setText(convertToLocalTime(match.matchTime));

        if (match.homeLogo != null && !match.homeLogo.isEmpty()) {
            Picasso.get()
                    .load(match.homeLogo)
                    .placeholder(R.drawable.ic_launcher)
                    .error(R.drawable.ic_launcher)
                    .into(holder.logoHome);
        } else {
            holder.logoHome.setImageResource(R.drawable.ic_launcher);
        }

        // Away logo
        if (match.awayLogo != null && !match.awayLogo.isEmpty()) {
            Picasso.get()
                    .load(match.awayLogo)
                    .placeholder(R.drawable.ic_launcher)
                    .error(R.drawable.ic_launcher)
                    .into(holder.logoAway);
        } else {
            holder.logoAway.setImageResource(R.drawable.ic_launcher);
        }

        holder.itemView.setOnClickListener(v -> listener.onMatchClick(match));
    }

    @Override
    public int getItemCount() {
        return matches.size();
    }

    static class MatchViewHolder extends RecyclerView.ViewHolder {
        ImageView logoHome, logoAway;
        TextView nameHome, nameAway, score, matchStatus, matchTime;

        public MatchViewHolder(@NonNull View itemView) {
            super(itemView);
            logoHome = itemView.findViewById(R.id.logoHome);
            logoAway = itemView.findViewById(R.id.logoAway);
            nameHome = itemView.findViewById(R.id.nameHome);
            nameAway = itemView.findViewById(R.id.nameAway);
            score = itemView.findViewById(R.id.score);
            matchStatus = itemView.findViewById(R.id.matchStatus);
            matchTime = itemView.findViewById(R.id.matchTime);
        }
    }

    public String convertToLocalTime(String utcTime) {
        ZonedDateTime utcDateTime = ZonedDateTime.parse(utcTime); // parses UTC ISO 8601
        ZoneId localZone = ZoneId.systemDefault(); // detects phone's timezone automatically
        ZonedDateTime localDateTime = utcDateTime.withZoneSameInstant(localZone);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        return localDateTime.format(formatter);
    }
}
