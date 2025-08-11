package com.example.nodeapp;

import org.json.JSONArray;
import org.json.JSONObject;

public class Fixture {
    public String homeName, awayName;
    public String homeLogo, awayLogo;
    public int homeScore, awayScore;
    public int id;
    public String status, matchTime;
    public String matchUrl;

    public static Fixture fromJson(JSONObject obj) {
        Fixture m = new Fixture();
        try {
            m.matchUrl = obj.getString("match_url");
            m.status = obj.getString("match_status_display");
            m.matchTime = obj.getString("match_time");
            m.id = obj.getInt("id");

            JSONArray teams = obj.getJSONArray("match_teams");
            JSONObject home = teams.getJSONObject(0);
            JSONObject away = teams.getJSONObject(1);

            m.homeName = home.getJSONObject("team").getString("name");
            m.homeLogo = home.getJSONObject("team").getString("logo");
            m.homeScore = home.getInt("goals");

            m.awayName = away.getJSONObject("team").getString("name");
            m.awayLogo = away.getJSONObject("team").getString("logo");
            m.awayScore = away.getInt("goals");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return m;
    }
}
