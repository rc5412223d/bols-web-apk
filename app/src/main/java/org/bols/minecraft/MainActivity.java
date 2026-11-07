package org.bols.minecraft;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;


public class MainActivity extends Activity {
    private static final int BG=Color.rgb(8,16,18), PANEL=Color.rgb(18,34,34);
    private static final int PANEL2=Color.rgb(25,47,43), MINT=Color.rgb(125,238,183);
    private static final int WHITE=Color.rgb(239,248,244), MUTED=Color.rgb(151,177,168);
    private static final String AUTH="https://auth.bols.dpdns.org/login?client=android";
    private static final String API="https://auth.bols.dpdns.org";
    private LinearLayout root, body, nav;
    private String active="首頁";
    private String userName="";
    private String session="";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        session=getPreferences(MODE_PRIVATE).getString("session","");
        userName=getPreferences(MODE_PRIVATE).getString("name","");
        buildShell();
        showTab("首頁");
        askNotificationPermission();
        if(!session.isEmpty()) registerCurrentPushToken();
    }

    private void buildShell() {
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(10),dp(18),dp(12)); root.setBackgroundColor(BG);
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo=new TextView(this); logo.setText("B"); logo.setTextColor(BG);
        logo.setTextSize(22); logo.setTypeface(Typeface.DEFAULT,Typeface.BOLD); logo.setGravity(Gravity.CENTER);
        logo.setBackground(round(MINT,dp(15))); header.addView(logo,new LinearLayout.LayoutParams(dp(46),dp(46)));
        LinearLayout brand=new LinearLayout(this); brand.setOrientation(LinearLayout.VERTICAL); brand.setPadding(dp(12),0,0,0);
        TextView title=txt("無垠伺服器",19,WHITE,true); TextView sub=txt("BOLS COMMUNITY  ·  MINECRAFT",9,MUTED,false);
        brand.addView(title); brand.addView(sub); header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView status=txt(session.isEmpty()?"未登入":"已登入",11,MINT,true);
        status.setPadding(dp(11),dp(8),dp(11),dp(8)); status.setBackground(round(PANEL2,dp(30))); header.addView(status);
        root.addView(header);
        View line=new View(this); line.setBackgroundColor(Color.rgb(38,65,59));
        LinearLayout.LayoutParams lineLp=new LinearLayout.LayoutParams(-1,dp(1)); lineLp.setMargins(0,dp(13),0,dp(12)); root.addView(line,lineLp);
        body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        nav=new LinearLayout(this); nav.setGravity(Gravity.CENTER); nav.setPadding(dp(5),dp(7),dp(5),dp(7)); nav.setBackground(round(PANEL,dp(22)));
        root.addView(nav,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);
        buildNav();
    }

    private void buildNav() {
        nav.removeAllViews();
        String[][] items={{"首頁","⌂"},{"聊天室","▤"},{"公告","♢"},{"我的","●"}};
        for(String[] item:items){
            LinearLayout cell=new LinearLayout(this); cell.setGravity(Gravity.CENTER); cell.setOrientation(LinearLayout.VERTICAL);
            cell.setPadding(dp(3),dp(6),dp(3),dp(5));
            boolean selected=active.equals(item[0]); cell.setBackground(round(selected?PANEL2:Color.TRANSPARENT,dp(15)));
            TextView ico=txt(item[1],22,selected?MINT:MUTED,true); ico.setGravity(Gravity.CENTER);
            TextView label=txt(item[0],10,selected?WHITE:MUTED,selected); label.setGravity(Gravity.CENTER);
            cell.addView(ico); cell.addView(label); cell.setOnClickListener(v->showTab(item[0]));
            nav.addView(cell,new LinearLayout.LayoutParams(0,dp(55),1));
        }
    }

    private void showTab(String tab) {
        active=tab; buildNav(); body.removeAllViews();
        if(tab.equals("首頁")) {
            addSpace(8);
            TextView welcome=txt(userName.isEmpty()?"歡迎來到無垠":"歡迎回來，"+userName,25,WHITE,true); body.addView(welcome);
            body.addView(txt("你的 Minecraft 社群入口",13,MUTED,false));
            addSpace(20);
            LinearLayout hero=panel(); hero.setPadding(dp(20),dp(21),dp(20),dp(21));
            TextView eyebrow=txt("MINECRAFT COMMUNITY",10,MINT,true);
            hero.addView(eyebrow); hero.addView(txt("一起打造無垠世界。",23,WHITE,true));
            hero.addView(txt("聊天、公告與社群資訊都集中在這裡。",12,MUTED,false));
            body.addView(hero);
            addSpace(16);
            body.addView(txt("社群功能",16,WHITE,true)); addSpace(8);
            addFeature("聊天室","與伺服器成員即時交流","▤",()->showTab("聊天室"));
            addFeature("伺服器公告","查看更新與重要消息","♢",()->showTab("公告"));
            addFeature("個人中心","管理帳號與通知設定","●",()->showTab("我的"));
            addSpace(15);
            if(session.isEmpty()) {
                Button login=button("使用 Google 登入"); body.addView(login);
                login.setOnClickListener(v->startGoogleLogin());
            } else body.addView(txt("目前已登入。",12,MINT,true));
        } else if(tab.equals("聊天室")) {
            body.addView(txt("聊天室",24,WHITE,true)); body.addView(txt("伺服器聊天頻道",12,MUTED,false)); addSpace(14);
            if(session.isEmpty()) requireLogin();
            else {
                LinearLayout tools=new LinearLayout(this); tools.setGravity(Gravity.CENTER_VERTICAL);
                TextView hint=txt("伺服器聊天",13,MUTED,false); tools.addView(hint,new LinearLayout.LayoutParams(0,-2,1));
                Button refresh=button("重新整理"); tools.addView(refresh); body.addView(tools);
                LinearLayout messages=new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL);
                body.addView(messages,new LinearLayout.LayoutParams(-1,0,1));
                LinearLayout composer=new LinearLayout(this); composer.setGravity(Gravity.CENTER_VERTICAL);
                EditText input=new EditText(this); input.setSingleLine(true); input.setTextColor(WHITE);
                input.setHintTextColor(MUTED); input.setHint("輸入訊息…"); input.setTextSize(14);
                input.setBackground(round(PANEL,dp(13))); input.setPadding(dp(12),dp(8),dp(12),dp(8));
                composer.addView(input,new LinearLayout.LayoutParams(0,dp(48),1));
                Button send=button("傳送"); LinearLayout.LayoutParams sendLp=new LinearLayout.LayoutParams(-2,dp(48)); sendLp.leftMargin=dp(8); composer.addView(send,sendLp);
                LinearLayout.LayoutParams composerLp=new LinearLayout.LayoutParams(-1,-2); composerLp.topMargin=dp(8); body.addView(composer,composerLp);
                refresh.setOnClickListener(v->loadChat("server",messages));
                send.setOnClickListener(v->{String value=input.getText().toString().trim(); if(!value.isEmpty()){input.setText(""); postChat(value,messages);}});
                loadChat("server",messages);
            }
        } else if(tab.equals("公告")) {
            body.addView(txt("伺服器公告",24,WHITE,true)); body.addView(txt("更新、活動與重要通知",12,MUTED,false)); addSpace(14);
            if(session.isEmpty()) requireLogin();
            else {
                LinearLayout messages=new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL);
                body.addView(messages,new LinearLayout.LayoutParams(-1,-1));
                loadChat("announcement",messages);
            }
        } else {
            body.addView(txt("個人中心",24,WHITE,true)); body.addView(txt("帳號與應用程式設定",12,MUTED,false)); addSpace(14);
            LinearLayout p=panel();
            p.addView(txt(userName.isEmpty()?"尚未登入":userName,18,WHITE,true));
            p.addView(txt(session.isEmpty()?"登入後可使用聊天室與個人功能。":"已連結無垠伺服器帳號",12,MUTED,false));
            Button action=button(session.isEmpty()?"使用 Google 登入":"登出"); p.addView(action);
            action.setOnClickListener(v->{if(session.isEmpty())startGoogleLogin();else logout();}); body.addView(p);
        }
    }

    private void addFeature(String title,String desc,String symbol,Runnable click) {
        LinearLayout card=panel(); card.setOrientation(LinearLayout.HORIZONTAL); card.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon=txt(symbol,23,MINT,true); icon.setGravity(Gravity.CENTER); icon.setBackground(round(PANEL2,dp(14)));
        card.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout words=new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL); words.setPadding(dp(12),0,0,0);
        words.addView(txt(title,15,WHITE,true)); words.addView(txt(desc,11,MUTED,false));
        card.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=txt("›",25,MUTED,false); card.addView(arrow);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(9); body.addView(card,lp);
        card.setOnClickListener(v->click.run());
    }

    private void requireLogin() {
        LinearLayout p=panel(); p.addView(txt("需要先登入",17,WHITE,true));
        p.addView(txt("登入後即可使用這項社群功能。",12,MUTED,false));
        Button b=button("使用 Google 登入"); p.addView(b); b.setOnClickListener(v->startGoogleLogin()); body.addView(p);
    }

    private void startGoogleLogin() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(AUTH))); }
        catch(Exception e){ Toast.makeText(this,"無法開啟 Google 登入。",Toast.LENGTH_LONG).show(); }
    }




    private void registerCurrentPushToken() {
        try {
            if(FirebaseApp.getApps(this).isEmpty()) return;
            FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
                if(token==null || token.isEmpty() || session.isEmpty()) return;
                new Thread(() -> {
                    HttpURLConnection connection=null;
                    try {
                        connection=(HttpURLConnection)new URL(API+"/api/push/device/register").openConnection();
                        connection.setRequestMethod("POST"); connection.setConnectTimeout(12000); connection.setReadTimeout(12000);
                        connection.setDoOutput(true); connection.setRequestProperty("Content-Type","application/json; charset=UTF-8");
                        connection.setRequestProperty("Authorization","Bearer "+session);
                        JSONObject payload=new JSONObject(); payload.put("token",token);
                        try(OutputStream os=connection.getOutputStream()){os.write(payload.toString().getBytes("UTF-8"));}
                        int status=connection.getResponseCode();
                        if(status<200||status>=300) android.util.Log.w("BolsPush","Token registration HTTP "+status);
                    } catch(Exception e){android.util.Log.w("BolsPush","Token registration failed",e);}
                    finally{if(connection!=null)connection.disconnect();}
                }).start();
            });
        } catch(Exception e){android.util.Log.w("BolsPush","Firebase is not ready",e);}
    }

    private void loadChat(String channel, LinearLayout target) {
        target.removeAllViews();
        target.addView(txt("載入中…",12,MUTED,false));
        new Thread(() -> {
            HttpURLConnection connection=null;
            try {
                URL url=new URL(API+"/api/chat?channel="+channel);
                connection=(HttpURLConnection)url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(12000); connection.setReadTimeout(12000);
                connection.setRequestProperty("Authorization","Bearer "+session);
                int status=connection.getResponseCode();
                BufferedReader reader=new BufferedReader(new InputStreamReader(status>=200&&status<300?connection.getInputStream():connection.getErrorStream(),"UTF-8"));
                StringBuilder raw=new StringBuilder(); String line; while((line=reader.readLine())!=null)raw.append(line); reader.close();
                if(status<200||status>=300)throw new Exception("HTTP "+status);
                JSONObject data=new JSONObject(raw.toString()); JSONArray items=data.optJSONArray("messages");
                runOnUiThread(() -> {
                    target.removeAllViews();
                    if(items==null||items.length()==0){target.addView(txt("目前還沒有訊息。",13,MUTED,false));return;}
                    for(int i=0;i<items.length();i++){
                        JSONObject m=items.optJSONObject(i); if(m==null)continue;
                        JSONObject u=m.optJSONObject("user"); String who=u==null?"玩家":u.optString("name","玩家");
                        String message=m.optString("text","");
                        LinearLayout card=panel(); card.addView(txt(who,12,MINT,true));
                        card.addView(txt(message,14,WHITE,false));
                        long time=m.optLong("createdAt",0);
                        if(time>0)card.addView(txt(new SimpleDateFormat("MM/dd HH:mm",Locale.getDefault()).format(new Date(time)),10,MUTED,false));
                        target.addView(card);
                    }
                });
            } catch(Exception e) {
                runOnUiThread(() -> {target.removeAllViews();target.addView(txt("讀取失敗："+e.getMessage(),12,Color.rgb(255,150,150),false));});
            } finally {if(connection!=null)connection.disconnect();}
        }).start();
    }

    private void postChat(String message, LinearLayout target) {
        new Thread(() -> {
            HttpURLConnection connection=null;
            try {
                URL url=new URL(API+"/api/chat");
                connection=(HttpURLConnection)url.openConnection();
                connection.setRequestMethod("POST"); connection.setConnectTimeout(12000); connection.setReadTimeout(12000);
                connection.setDoOutput(true); connection.setRequestProperty("Content-Type","application/json; charset=UTF-8");
                connection.setRequestProperty("Authorization","Bearer "+session);
                JSONObject payload=new JSONObject(); payload.put("channel","server"); payload.put("text",message);
                try(OutputStream os=connection.getOutputStream()){os.write(payload.toString().getBytes("UTF-8"));}
                int status=connection.getResponseCode();
                if(status<200||status>=300)throw new Exception("HTTP "+status);
                runOnUiThread(() -> loadChat("server",target));
            } catch(Exception e) {runOnUiThread(() -> Toast.makeText(this,"傳送失敗："+e.getMessage(),Toast.LENGTH_LONG).show());}
            finally {if(connection!=null)connection.disconnect();}
        }).start();
    }

    private void exchangeAppLoginCode(String code) {
        new Thread(() -> {
            HttpURLConnection connection=null;
            try {
                URL url=new URL(API+"/api/app-auth/exchange");
                connection=(HttpURLConnection)url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type","application/json; charset=UTF-8");
                JSONObject payload=new JSONObject(); payload.put("code",code);
                try(OutputStream os=connection.getOutputStream()) {
                    os.write(payload.toString().getBytes("UTF-8"));
                }
                int status=connection.getResponseCode();
                BufferedReader reader=new BufferedReader(new InputStreamReader(
                    status>=200 && status<300 ? connection.getInputStream() : connection.getErrorStream(),"UTF-8"));
                StringBuilder response=new StringBuilder(); String line;
                while((line=reader.readLine())!=null) response.append(line);
                reader.close();
                if(status<200 || status>=300) throw new Exception("HTTP "+status);
                JSONObject data=new JSONObject(response.toString());
                if(!data.optBoolean("ok")) throw new Exception(data.optString("error","登入交換失敗"));
                String token=data.optString("session","");
                JSONObject user=data.optJSONObject("user");
                if(token.isEmpty() || user==null) throw new Exception("伺服器回傳資料不完整");
                String name=user.optString("name",user.optString("email","玩家"));
                getPreferences(MODE_PRIVATE).edit().putString("session",token).putString("name",name).apply();
                runOnUiThread(() -> {
                    session=token; userName=name; buildShell(); showTab("首頁"); registerCurrentPushToken();
                    Toast.makeText(this,"登入成功，歡迎回來！",Toast.LENGTH_LONG).show();
                });
            } catch(Exception e) {
                runOnUiThread(() -> Toast.makeText(this,"登入交換失敗："+e.getMessage(),Toast.LENGTH_LONG).show());
            } finally { if(connection!=null) connection.disconnect(); }
        }).start();
    }

    private void logout() {
        session=""; userName="";
        getPreferences(MODE_PRIVATE).edit().remove("session").remove("name").apply();
        showTab("我的"); Toast.makeText(this,"已從 App 登出。",Toast.LENGTH_SHORT).show();
    }

    private LinearLayout panel() {
        LinearLayout p=new LinearLayout(this); p.setOrientation(LinearLayout.VERTICAL); p.setPadding(dp(16),dp(15),dp(16),dp(15));
        p.setBackground(round(PANEL,dp(19))); return p;
    }
    private TextView txt(String s,int size,int color,boolean bold) {
        TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setPadding(0,dp(2),0,dp(2)); return t;
    }
    private Button button(String s) {
        Button b=new Button(this); b.setText(s); b.setTextColor(BG); b.setAllCaps(false); b.setTextSize(14);
        b.setBackground(round(MINT,dp(14))); return b;
    }
    private GradientDrawable round(int color,int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d; }
    private void addSpace(int h) { View v=new View(this); body.addView(v,new LinearLayout.LayoutParams(1,dp(h))); }
    private int dp(float v) { return (int)(v*getResources().getDisplayMetrics().density+0.5f); }

    private void askNotificationPermission() {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1202);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        Uri data=intent.getData();
        if(data!=null && "bolsminecraft".equals(data.getScheme()) && "auth".equals(data.getHost())) {
            String code=data.getQueryParameter("code");
            if(code==null || code.isEmpty()) {
                Toast.makeText(this,"登入回傳資料不完整。",Toast.LENGTH_LONG).show();
            } else {
                exchangeAppLoginCode(code);
            }
        }
    }
}
