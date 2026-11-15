package org.bols.minecraft;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.util.Linkify;
import android.text.method.LinkMovementMethod;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;


public class MainActivity extends Activity {
    private static final int BG=Color.rgb(10,13,25), PANEL=Color.rgb(20,26,44);
    private static final int PANEL2=Color.rgb(31,40,64), MINT=Color.rgb(111,231,218);
    private static final int WHITE=Color.rgb(243,246,255), MUTED=Color.rgb(155,166,193);
    private static final int ACCENT=Color.rgb(164,137,255), BORDER=Color.rgb(48,59,88);
    private static final String AUTH="https://auth.bols.dpdns.org/login?client=android";
    private static final String API="https://auth.bols.dpdns.org";
    private LinearLayout root, body, nav;
    private FrameLayout contentFrame;
    private ScrollView chatScroll;
    private LinearLayout activeChatMessages;
    private TextView voiceStatusView, voiceParticipantsView;
    private Button voiceJoinButton, voiceMuteButton, voiceSpeakerButton, voiceLeaveButton;
    private OkHttpClient voiceHttpClient;
    private WebSocket voiceSocket;
    private PeerConnectionFactory peerFactory;
    private AudioSource voiceAudioSource;
    private AudioTrack localAudioTrack;
    private final Map<String, PeerConnection> voicePeers = new HashMap<>();
    private final Map<String, String> voiceUsers = new LinkedHashMap<>();
    private final Map<String, List<IceCandidate>> pendingVoiceIce = new HashMap<>();
    private AudioManager audioManager;
    private boolean voiceJoined = false;
    private boolean voiceConnecting = false;
    private boolean voiceMuted = false;
    private boolean pendingVoiceJoin = false;
    private boolean speakerEnabled = true;
    private String voicePeerId = "";
    private String voiceStatusMessage = "尚未加入語音";
    private String pushFirebaseStatus = "尚未檢查";
    private String pushTokenStatus = "尚未取得";
    private String pushRegistrationStatus = "尚未註冊";
    private AlertDialog pushDiagnosticDialog;
    private int previousAudioMode = AudioManager.MODE_NORMAL;
    private boolean previousSpeakerState = false;
    private static boolean webRtcInitialized = false;
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
        handleAuthIntent(getIntent());
    }

    private void buildShell() {
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(12),dp(18),dp(10)); root.setBackgroundColor(BG);
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo=new TextView(this); logo.setText("B"); logo.setTextColor(BG); logo.setTextSize(22);
        logo.setTypeface(Typeface.DEFAULT,Typeface.BOLD); logo.setGravity(Gravity.CENTER);
        logo.setBackground(gradient(MINT,ACCENT,dp(15))); header.addView(logo,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout brand=new LinearLayout(this); brand.setOrientation(LinearLayout.VERTICAL); brand.setPadding(dp(12),0,0,0);
        brand.addView(txt("無垠伺服器",18,WHITE,true));
        TextView sub=txt("COMMUNITY  /  MINECRAFT",9,MUTED,true); sub.setLetterSpacing(0.08f); brand.addView(sub);
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout pill=new LinearLayout(this); pill.setGravity(Gravity.CENTER); pill.setPadding(dp(11),dp(8),dp(11),dp(8)); pill.setBackground(outline(PANEL2,BORDER,dp(30)));
        View dot=new View(this); dot.setBackground(round(session.isEmpty()?MUTED:MINT,dp(8)));
        LinearLayout.LayoutParams dotLp=new LinearLayout.LayoutParams(dp(7),dp(7)); dotLp.rightMargin=dp(6); pill.addView(dot,dotLp);
        pill.addView(txt(session.isEmpty()?"訪客模式":"已登入",10,session.isEmpty()?MUTED:MINT,true)); header.addView(pill);
        root.addView(header);
        View line=new View(this); line.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams lineLp=new LinearLayout.LayoutParams(-1,dp(1)); lineLp.setMargins(0,dp(12),0,dp(10)); root.addView(line,lineLp);
        contentFrame=new FrameLayout(this); root.addView(contentFrame,new LinearLayout.LayoutParams(-1,0,1));
        nav=new LinearLayout(this); nav.setGravity(Gravity.CENTER); nav.setPadding(dp(5),dp(5),dp(5),dp(5));
        nav.setBackground(outline(PANEL,Color.rgb(39,48,72),dp(22))); nav.setElevation(dp(8));
        LinearLayout.LayoutParams navLp=new LinearLayout.LayoutParams(-1,-2); navLp.topMargin=dp(8); root.addView(nav,navLp);
        setContentView(root); buildNav();
    }

    private void buildNav() {
        nav.removeAllViews();
        String[][] items={{"首頁","⌂"},{"聊天室","▤"},{"語音","◉"},{"公告","◇"},{"我的","●"}};
        for(String[] item:items) {
            boolean selected=active.equals(item[0]);
            LinearLayout cell=new LinearLayout(this); cell.setGravity(Gravity.CENTER); cell.setOrientation(LinearLayout.VERTICAL);
            cell.setPadding(dp(2),dp(7),dp(2),dp(6)); cell.setBackground(round(selected?PANEL2:Color.TRANSPARENT,dp(15)));
            TextView icon=txt(item[1],20,selected?MINT:MUTED,true); icon.setGravity(Gravity.CENTER);
            TextView label=txt(item[0],10,selected?WHITE:MUTED,selected); label.setGravity(Gravity.CENTER);
            cell.addView(icon); cell.addView(label); cell.setOnClickListener(v->showTab(item[0]));
            nav.addView(cell,new LinearLayout.LayoutParams(0,dp(57),1));
        }
    }

    private void showTab(String tab) {
        active=tab; buildNav(); contentFrame.removeAllViews();
        chatScroll=null; activeChatMessages=null; voiceStatusView=null; voiceParticipantsView=null;
        voiceJoinButton=null; voiceMuteButton=null; voiceSpeakerButton=null; voiceLeaveButton=null;
        if(tab.equals("聊天室")) { showChatScreen(); return; }
        if(tab.equals("語音")) { showVoiceScreen(); return; }

        body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(3),dp(10),dp(3),dp(18));
        ScrollView pageScroll=new ScrollView(this); pageScroll.setFillViewport(true);
        pageScroll.setVerticalScrollBarEnabled(false); pageScroll.addView(body);
        contentFrame.addView(pageScroll,new FrameLayout.LayoutParams(-1,-1));

        if(tab.equals("首頁")) {
            LinearLayout greeting=new LinearLayout(this); greeting.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout greetingText=new LinearLayout(this); greetingText.setOrientation(LinearLayout.VERTICAL);
            greetingText.addView(txt(session.isEmpty()?"歡迎來到無垠":"歡迎回來",12,MUTED,true));
            TextView name=txt(session.isEmpty()?"探索你的社群入口":userName,23,WHITE,true);
            name.setMaxLines(1); name.setEllipsize(android.text.TextUtils.TruncateAt.END); greetingText.addView(name);
            greeting.addView(greetingText,new LinearLayout.LayoutParams(0,-2,1));
            TextView avatar=txt(session.isEmpty()?"B":(userName.isEmpty()?"U":userName.substring(0,1).toUpperCase(Locale.getDefault())),18,BG,true);
            avatar.setGravity(Gravity.CENTER); avatar.setBackground(gradient(MINT,ACCENT,dp(18)));
            greeting.addView(avatar,new LinearLayout.LayoutParams(dp(46),dp(46))); body.addView(greeting);
            addSpace(17);

            LinearLayout hero=new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL);
            hero.setPadding(dp(20),dp(21),dp(20),dp(20));
            hero.setBackground(gradient(Color.rgb(34,48,78),Color.rgb(39,35,76),dp(24)));
            TextView eyebrow=txt("BOLS  /  COMMUNITY HUB",10,MINT,true); eyebrow.setLetterSpacing(0.12f); hero.addView(eyebrow);
            addSpaceTo(hero,12); hero.addView(txt("把世界連在一起。",25,WHITE,true));
            TextView heroSub=txt("聊天、語音與最新公告，都在你的掌握之中。",13,Color.rgb(204,213,238),false);
            heroSub.setLineSpacing(dp(3),1.0f); LinearLayout.LayoutParams subLp=new LinearLayout.LayoutParams(-1,-2); subLp.topMargin=dp(7); hero.addView(heroSub,subLp);
            LinearLayout footer=new LinearLayout(this); footer.setGravity(Gravity.CENTER_VERTICAL);
            TextView tag=txt("✦  YOUR COMMUNITY",10,WHITE,true); tag.setPadding(dp(10),dp(7),dp(10),dp(7));
            tag.setBackground(round(Color.argb(42,255,255,255),dp(30))); footer.addView(tag,new LinearLayout.LayoutParams(0,-2,1));
            TextView mark=txt("BOLS",12,MINT,true); mark.setLetterSpacing(0.16f); footer.addView(mark);
            LinearLayout.LayoutParams footerLp=new LinearLayout.LayoutParams(-1,-2); footerLp.topMargin=dp(20); hero.addView(footer,footerLp);
            body.addView(hero);

            addSpace(23); addSectionHeader("快速前往","QUICK ACCESS"); addSpace(10);
            addFeature("聊天室","與成員即時交流","▤",()->showTab("聊天室"));
            addFeature("語音大廳","加入語音，和朋友聊天","◉",()->showTab("語音"));
            addFeature("伺服器公告","掌握更新與重要消息","◇",()->showTab("公告"));
            if(session.isEmpty()) {
                addSpace(8); LinearLayout loginCard=panel();
                loginCard.addView(txt("登入後解鎖完整功能",16,WHITE,true));
                loginCard.addView(txt("使用 Google 帳號登入，即可加入聊天室與語音大廳。",12,MUTED,false));
                Button login=button("使用 Google 登入"); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48)); lp.topMargin=dp(12);
                loginCard.addView(login,lp); login.setOnClickListener(v->startGoogleLogin()); body.addView(loginCard);
            }
        } else if(tab.equals("公告")) {
            addSectionHeader("伺服器公告","NEWS & UPDATES");
            TextView subtitle=txt("更新、活動與重要通知都會整理在這裡。",12,MUTED,false);
            LinearLayout.LayoutParams subLp=new LinearLayout.LayoutParams(-1,-2); subLp.topMargin=dp(5); body.addView(subtitle,subLp);
            addSpace(14);
            if(session.isEmpty()) requireLogin();
            else { LinearLayout messages=new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL); body.addView(messages,new LinearLayout.LayoutParams(-1,-1)); loadChat("announcement",messages); }
        } else {
            addSectionHeader("個人中心","YOUR ACCOUNT");
            TextView subtitle=txt("帳號、通知與應用程式設定。",12,MUTED,false);
            LinearLayout.LayoutParams subLp=new LinearLayout.LayoutParams(-1,-2); subLp.topMargin=dp(5); body.addView(subtitle,subLp);
            addSpace(16);
            LinearLayout profile=panel(); profile.setPadding(dp(18),dp(18),dp(18),dp(18));
            LinearLayout identity=new LinearLayout(this); identity.setGravity(Gravity.CENTER_VERTICAL);
            TextView avatar=txt(session.isEmpty()?"?":(userName.isEmpty()?"U":userName.substring(0,1).toUpperCase(Locale.getDefault())),21,BG,true);
            avatar.setGravity(Gravity.CENTER); avatar.setBackground(gradient(MINT,ACCENT,dp(19)));
            identity.addView(avatar,new LinearLayout.LayoutParams(dp(54),dp(54)));
            LinearLayout details=new LinearLayout(this); details.setOrientation(LinearLayout.VERTICAL); details.setPadding(dp(13),0,0,0);
            details.addView(txt(session.isEmpty()?"尚未登入":userName,17,WHITE,true));
            details.addView(txt(session.isEmpty()?"訪客帳號":"已連結無垠伺服器帳號",11,MUTED,false));
            identity.addView(details,new LinearLayout.LayoutParams(0,-2,1)); profile.addView(identity);
            Button action=button(session.isEmpty()?"使用 Google 登入":"登出帳號");
            LinearLayout.LayoutParams actionLp=new LinearLayout.LayoutParams(-1,dp(48)); actionLp.topMargin=dp(17); profile.addView(action,actionLp);
            action.setOnClickListener(v->{if(session.isEmpty())startGoogleLogin();else logout();}); body.addView(profile);

            addSpace(18); addSectionHeader("應用程式","APP TOOLS"); addSpace(9);
            LinearLayout diagnostic=panel(); diagnostic.setOrientation(LinearLayout.HORIZONTAL); diagnostic.setGravity(Gravity.CENTER_VERTICAL);
            TextView bell=txt("♧",22,MINT,true); bell.setGravity(Gravity.CENTER); bell.setBackground(round(PANEL2,dp(13)));
            diagnostic.addView(bell,new LinearLayout.LayoutParams(dp(44),dp(44)));
            LinearLayout diagnosticText=new LinearLayout(this); diagnosticText.setOrientation(LinearLayout.VERTICAL); diagnosticText.setPadding(dp(12),0,dp(5),0);
            diagnosticText.addView(txt("通知診斷",14,WHITE,true)); diagnosticText.addView(txt("檢查權限、Firebase 與裝置註冊",10,MUTED,false));
            diagnostic.addView(diagnosticText,new LinearLayout.LayoutParams(0,-2,1)); diagnostic.addView(txt("›",25,MUTED,false));
            diagnostic.setOnClickListener(v->showPushDiagnostics()); body.addView(diagnostic);
            addSpace(10); body.addView(txt("提醒：推播仍需 Firebase 與 Worker 伺服器端設定完成才能送達。",11,MUTED,false));
        }
    }

    private void showChatScreen() {
        LinearLayout screen=new LinearLayout(this); screen.setOrientation(LinearLayout.VERTICAL);
        screen.setPadding(dp(3),dp(8),dp(3),0); contentFrame.addView(screen,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout heading=new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleBox=new LinearLayout(this); titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.addView(txt("聊天室",23,WHITE,true)); titleBox.addView(txt("與無垠社群成員即時交流",11,MUTED,false));
        heading.addView(titleBox,new LinearLayout.LayoutParams(0,-2,1));
        TextView live=txt("●  CHAT",10,MINT,true); live.setPadding(dp(10),dp(7),dp(10),dp(7)); live.setBackground(round(PANEL2,dp(30)));
        heading.addView(live); screen.addView(heading); addSpaceTo(screen,13);

        if(session.isEmpty()) {
            LinearLayout login=panel(); login.addView(txt("加入對話",18,WHITE,true));
            login.addView(txt("登入後即可閱讀訊息並與伺服器成員聊天。",12,MUTED,false));
            Button loginButton=button("使用 Google 登入"); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48)); lp.topMargin=dp(14);
            login.addView(loginButton,lp); loginButton.setOnClickListener(v->startGoogleLogin()); screen.addView(login); return;
        }

        LinearLayout toolbar=panel(); toolbar.setOrientation(LinearLayout.HORIZONTAL); toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(13),dp(9),dp(11),dp(9));
        TextView channelIcon=txt("#",18,MINT,true); channelIcon.setGravity(Gravity.CENTER); channelIcon.setBackground(round(PANEL2,dp(10)));
        toolbar.addView(channelIcon,new LinearLayout.LayoutParams(dp(34),dp(34)));
        LinearLayout channelText=new LinearLayout(this); channelText.setOrientation(LinearLayout.VERTICAL); channelText.setPadding(dp(10),0,0,0);
        channelText.addView(txt("伺服器聊天",12,WHITE,true)); channelText.addView(txt("公開頻道",10,MUTED,false));
        toolbar.addView(channelText,new LinearLayout.LayoutParams(0,-2,1));
        TextView refresh=txt("↻",22,MINT,true); refresh.setGravity(Gravity.CENTER); refresh.setBackground(round(PANEL2,dp(12)));
        toolbar.addView(refresh,new LinearLayout.LayoutParams(dp(38),dp(38)));
        LinearLayout.LayoutParams toolbarLp=new LinearLayout.LayoutParams(-1,-2); toolbarLp.bottomMargin=dp(9); screen.addView(toolbar,toolbarLp);

        chatScroll=new ScrollView(this); chatScroll.setFillViewport(false); chatScroll.setSmoothScrollingEnabled(true);
        chatScroll.setClipToPadding(false); chatScroll.setVerticalScrollBarEnabled(false); chatScroll.setPadding(0,dp(4),0,dp(8));
        LinearLayout messages=new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL);
        chatScroll.addView(messages,new ScrollView.LayoutParams(-1,-2)); activeChatMessages=messages;
        screen.addView(chatScroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout composer=panel(); composer.setOrientation(LinearLayout.HORIZONTAL); composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(9),dp(8),dp(8),dp(8));
        EditText input=new EditText(this); input.setSingleLine(true); input.setTextColor(WHITE); input.setHintTextColor(MUTED);
        input.setHint("傳送訊息給大家…"); input.setTextSize(14); input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(dp(9),0,dp(8),0); input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
        composer.addView(input,new LinearLayout.LayoutParams(0,dp(46),1));
        TextView send=txt("➤",18,BG,true); send.setGravity(Gravity.CENTER); send.setBackground(gradient(MINT,Color.rgb(92,198,220),dp(15)));
        composer.addView(send,new LinearLayout.LayoutParams(dp(46),dp(46)));
        LinearLayout.LayoutParams composerLp=new LinearLayout.LayoutParams(-1,-2); composerLp.topMargin=dp(8); composerLp.bottomMargin=dp(5); screen.addView(composer,composerLp);

        refresh.setOnClickListener(v->loadChat("server",messages));
        View.OnClickListener sendAction=v->{String value=input.getText().toString().trim(); if(!value.isEmpty()){input.setText("");postChat(value,messages);}};
        send.setOnClickListener(sendAction);
        input.setOnEditorActionListener((v,actionId,event)->{if(actionId==android.view.inputmethod.EditorInfo.IME_ACTION_SEND){sendAction.onClick(send);return true;}return false;});
        loadChat("server",messages);
    }

    private void scrollChatToBottom(LinearLayout target) {
        if(chatScroll==null || target!=activeChatMessages) return;
        target.post(() -> {
            ScrollView current = chatScroll;
            if(current == null || target != activeChatMessages) return;

            // Wait until the message list has completed measuring/layout before
            // calculating the scroll offset. This makes the chat open at the
            // newest message even when the first network response arrives late.
            ViewTreeObserver observer = current.getViewTreeObserver();
            observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override public void onGlobalLayout() {
                    if(current.getViewTreeObserver().isAlive()) {
                        current.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                    if(chatScroll != current || activeChatMessages != target) return;
                    View child = current.getChildAt(0);
                    if(child != null) {
                        int bottom = Math.max(0, child.getHeight() - current.getHeight());
                        current.scrollTo(0, bottom);
                    }
                }
            });
            current.requestLayout();
        });
    }

    private void showVoiceScreen() {
        ScrollView page=new ScrollView(this); page.setFillViewport(true); page.setVerticalScrollBarEnabled(false);
        LinearLayout screen=new LinearLayout(this); screen.setOrientation(LinearLayout.VERTICAL); screen.setPadding(dp(3),dp(9),dp(3),dp(18));
        page.addView(screen); contentFrame.addView(page,new FrameLayout.LayoutParams(-1,-1));
        screen.addView(txt("語音大廳",23,WHITE,true)); screen.addView(txt("與朋友即時通話，保持連線。",11,MUTED,false)); addSpaceTo(screen,15);

        if(session.isEmpty()) {
            LinearLayout login=panel(); login.addView(txt("加入語音大廳",18,WHITE,true));
            login.addView(txt("登入後即可加入頻道，與其他成員通話。",12,MUTED,false));
            Button loginButton=button("使用 Google 登入"); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48)); lp.topMargin=dp(14);
            login.addView(loginButton,lp); loginButton.setOnClickListener(v->startGoogleLogin()); screen.addView(login); return;
        }

        LinearLayout stage=new LinearLayout(this); stage.setOrientation(LinearLayout.VERTICAL); stage.setGravity(Gravity.CENTER);
        stage.setPadding(dp(20),dp(23),dp(20),dp(22)); stage.setBackground(gradient(Color.rgb(35,51,79),Color.rgb(43,37,78),dp(24)));
        TextView mic=txt("◉",34,MINT,true); mic.setGravity(Gravity.CENTER); mic.setBackground(round(Color.argb(38,111,231,218),dp(28)));
        stage.addView(mic,new LinearLayout.LayoutParams(dp(78),dp(78)));
        TextView stageTitle=txt("無垠語音頻道",19,WHITE,true); LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(-2,-2); titleLp.topMargin=dp(14); stage.addView(stageTitle,titleLp);
        voiceStatusView=txt(voiceStatusMessage,12,Color.rgb(208,220,243),false); voiceStatusView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp=new LinearLayout.LayoutParams(-1,-2); statusLp.topMargin=dp(5); stage.addView(voiceStatusView,statusLp);
        screen.addView(stage); addSpaceTo(screen,12);
        voiceJoinButton=button("加入語音大廳"); screen.addView(voiceJoinButton,new LinearLayout.LayoutParams(-1,dp(52)));

        LinearLayout controls=new LinearLayout(this); controls.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams controlsLp=new LinearLayout.LayoutParams(-1,-2); controlsLp.topMargin=dp(9); screen.addView(controls,controlsLp);
        voiceMuteButton=secondaryButton(voiceMuted?"取消靜音":"麥克風靜音");
        voiceSpeakerButton=secondaryButton(speakerEnabled?"喇叭開啟":"喇叭關閉"); voiceLeaveButton=secondaryButton("離開頻道");
        LinearLayout.LayoutParams c1=new LinearLayout.LayoutParams(0,dp(46),1); c1.setMargins(0,0,dp(5),0); controls.addView(voiceMuteButton,c1);
        LinearLayout.LayoutParams c2=new LinearLayout.LayoutParams(0,dp(46),1); c2.setMargins(dp(2),0,dp(2),0); controls.addView(voiceSpeakerButton,c2);
        LinearLayout.LayoutParams c3=new LinearLayout.LayoutParams(0,dp(46),1); c3.setMargins(dp(5),0,0,0); controls.addView(voiceLeaveButton,c3);

        addSpaceTo(screen,17);
        LinearLayout participants=panel(); LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(txt("頻道成員",15,WHITE,true),new LinearLayout.LayoutParams(0,-2,1));
        TextView badge=txt("VOICE ROOM",9,MINT,true); badge.setPadding(dp(8),dp(6),dp(8),dp(6)); badge.setBackground(round(PANEL2,dp(20))); header.addView(badge);
        participants.addView(header); voiceParticipantsView=txt("",12,MUTED,false);
        voiceParticipantsView.setPadding(0,dp(12),0,0); voiceParticipantsView.setLineSpacing(dp(4),1.0f); participants.addView(voiceParticipantsView);
        screen.addView(participants);

        voiceJoinButton.setOnClickListener(v->requestVoicePermissionAndJoin());
        voiceMuteButton.setOnClickListener(v->toggleVoiceMute()); voiceSpeakerButton.setOnClickListener(v->toggleSpeaker()); voiceLeaveButton.setOnClickListener(v->leaveVoice());
        updateVoiceUi(); updateVoiceParticipants();
    }

    private void requestVoicePermissionAndJoin() {
        if(session.isEmpty()) {
            Toast.makeText(this,"請先登入。",Toast.LENGTH_SHORT).show();
            return;
        }
        if(voiceJoined || voiceConnecting) return;
        if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            pendingVoiceJoin = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},4021);
            return;
        }
        startVoiceConnection();
    }

    private void startVoiceConnection() {
        if(voiceJoined || voiceConnecting || voiceSocket!=null) return;
        try {
            synchronized(MainActivity.class) {
                if(!webRtcInitialized) {
                    PeerConnectionFactory.initialize(
                        PeerConnectionFactory.InitializationOptions.builder(getApplicationContext()).createInitializationOptions()
                    );
                    webRtcInitialized = true;
                }
            }

            peerFactory = PeerConnectionFactory.builder().createPeerConnectionFactory();
            voiceAudioSource = peerFactory.createAudioSource(new MediaConstraints());
            localAudioTrack = peerFactory.createAudioTrack("bols-voice-audio",voiceAudioSource);
            localAudioTrack.setEnabled(!voiceMuted);

            audioManager = (AudioManager)getSystemService(AUDIO_SERVICE);
            if(audioManager!=null) {
                previousAudioMode = audioManager.getMode();
                previousSpeakerState = audioManager.isSpeakerphoneOn();
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                audioManager.setSpeakerphoneOn(speakerEnabled);
            }

            voiceHttpClient = new OkHttpClient.Builder()
                .readTimeout(0,TimeUnit.MILLISECONDS)
                .build();
            Request request = new Request.Builder()
                .url(API.replace("https://","wss://")+"/api/voice/ws")
                .header("Authorization","Bearer "+session)
                .build();

            voiceConnecting = true;
            voiceStatusMessage = "正在連線語音大廳…";
            updateVoiceUi();
            voiceSocket = voiceHttpClient.newWebSocket(request,new WebSocketListener() {
                @Override public void onOpen(WebSocket webSocket, Response response) {
                    runOnUiThread(()->{
                        voiceJoined = true;
                        voiceConnecting = false;
                        voiceStatusMessage = "已連線，等待其他成員加入…";
                        updateVoiceUi();
                    });
                }

                @Override public void onMessage(WebSocket webSocket, String text) {
                    try {
                        handleVoiceSignal(new JSONObject(text));
                    } catch(Exception e) {
                        android.util.Log.w("BolsVoice","無法解析語音訊號",e);
                    }
                }

                @Override public void onFailure(WebSocket webSocket, Throwable error, Response response) {
                    android.util.Log.w("BolsVoice","語音連線失敗",error);
                    runOnUiThread(()->{
                        endVoiceSession(false);
                        voiceStatusMessage = "語音連線失敗："+(error.getMessage()==null?"未知錯誤":error.getMessage());
                        updateVoiceUi();
                    });
                }

                @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                    runOnUiThread(()->{
                        if(voiceJoined || voiceConnecting) {
                            endVoiceSession(false);
                            voiceStatusMessage = "語音連線已中斷";
                            updateVoiceUi();
                        }
                    });
                }
            });
        } catch(Exception e) {
            android.util.Log.e("BolsVoice","初始化語音失敗",e);
            endVoiceSession(false);
            voiceStatusMessage = "無法啟動語音："+e.getMessage();
            updateVoiceUi();
            Toast.makeText(this,"無法啟動語音功能。",Toast.LENGTH_LONG).show();
        }
    }

    private void handleVoiceSignal(JSONObject data) {
        String type = data.optString("type","");
        if("voice-state".equals(type)) {
            voicePeerId = data.optString("selfPeerId","");
            JSONArray participants = data.optJSONArray("participants");
            if(participants!=null) {
                for(int i=0;i<participants.length();i++) {
                    JSONObject item = participants.optJSONObject(i);
                    if(item==null) continue;
                    String id = item.optString("peerId","");
                    if(id.isEmpty() || id.equals(voicePeerId)) continue;
                    voiceUsers.put(id,voiceName(item.optJSONObject("user")));
                }
                updateVoiceParticipants();
                for(int i=0;i<participants.length();i++) {
                    JSONObject item = participants.optJSONObject(i);
                    if(item==null) continue;
                    String id = item.optString("peerId","");
                    if(!id.isEmpty() && !id.equals(voicePeerId) && voicePeerId.compareTo(id)<0) createVoiceOffer(id);
                }
            }
            return;
        }

        if("voice-join".equals(type)) {
            String id = data.optString("peerId","");
            if(id.isEmpty() || id.equals(voicePeerId)) return;
            voiceUsers.put(id,voiceName(data.optJSONObject("user")));
            updateVoiceParticipants();
            if(!voicePeerId.isEmpty() && voicePeerId.compareTo(id)<0) createVoiceOffer(id);
            return;
        }

        if("voice-offer".equals(type)) {
            String id = data.optString("fromPeerId","");
            JSONObject offer = data.optJSONObject("sdp");
            if(offer==null) offer=data.optJSONObject("offer");
            if(offer==null) offer=data.optJSONObject("payload");
            if(id.isEmpty() || offer==null) return;
            String sdp = offer.optString("sdp","");
            String sdpType = offer.optString("type","offer");
            if(sdp.isEmpty()) return;
            voiceUsers.put(id,voiceName(data.optJSONObject("fromUser")));
            updateVoiceParticipants();
            PeerConnection pc = getOrCreateVoicePeer(id);
            SessionDescription remote = new SessionDescription(SessionDescription.Type.fromCanonicalForm(sdpType),sdp);
            pc.setRemoteDescription(new SdpObserverAdapter() {
                @Override public void onSetSuccess() {
                    flushVoiceIce(id,pc);
                    pc.createAnswer(new SdpObserverAdapter() {
                        @Override public void onCreateSuccess(SessionDescription answer) {
                            pc.setLocalDescription(new SdpObserverAdapter() {
                                @Override public void onSetSuccess() {
                                    sendVoiceDescription("voice-answer",id,answer);
                                }
                                @Override public void onSetFailure(String error) {
                                    android.util.Log.w("BolsVoice","設定 Answer 失敗："+error);
                                }
                            },answer);
                        }
                        @Override public void onCreateFailure(String error) {
                            android.util.Log.w("BolsVoice","建立 Answer 失敗："+error);
                        }
                    },new MediaConstraints());
                }
                @Override public void onSetFailure(String error) {
                    android.util.Log.w("BolsVoice","設定 Offer 失敗："+error);
                }
            },remote);
            return;
        }

        if("voice-answer".equals(type)) {
            String id = data.optString("fromPeerId","");
            JSONObject answer = data.optJSONObject("sdp");
            if(answer==null) answer=data.optJSONObject("answer");
            if(answer==null) answer=data.optJSONObject("payload");
            if(answer==null) return;
            String sdp = answer.optString("sdp","");
            String sdpType = answer.optString("type","answer");
            PeerConnection pc = voicePeers.get(id);
            if(pc!=null && !sdp.isEmpty()) {
                pc.setRemoteDescription(new SdpObserverAdapter() {
                    @Override public void onSetSuccess() {
                        flushVoiceIce(id,pc);
                    }
                    @Override public void onSetFailure(String error) {
                        android.util.Log.w("BolsVoice","設定 Answer 失敗："+error);
                    }
                },new SessionDescription(SessionDescription.Type.fromCanonicalForm(sdpType),sdp));
            }
            return;
        }

        if("voice-ice".equals(type)) {
            String id = data.optString("fromPeerId","");
            JSONObject candidate = data.optJSONObject("candidate");
            if(candidate==null) candidate=data.optJSONObject("ice");
            if(candidate==null) candidate=data.optJSONObject("payload");
            if(candidate==null) return;
            IceCandidate ice = new IceCandidate(
                candidate.optString("sdpMid","audio"),
                candidate.optInt("sdpMLineIndex",0),
                candidate.optString("candidate","")
            );
            PeerConnection pc = voicePeers.get(id);
            if(pc==null || pc.getRemoteDescription()==null) {
                List<IceCandidate> waiting = pendingVoiceIce.get(id);
                if(waiting==null) {
                    waiting = new ArrayList<>();
                    pendingVoiceIce.put(id,waiting);
                }
                waiting.add(ice);
            } else {
                pc.addIceCandidate(ice);
            }
            return;
        }

        if("voice-leave".equals(type)) {
            String id = data.optString("peerId","");
            PeerConnection pc = voicePeers.remove(id);
            if(pc!=null) {
                pc.close();
                pc.dispose();
            }
            pendingVoiceIce.remove(id);
            voiceUsers.remove(id);
            updateVoiceParticipants();
        }
    }

    private String voiceName(JSONObject user) {
        if(user==null) return "Minecraft 玩家";
        String name = user.optString("name","");
        if(name.isEmpty()) name=user.optString("email","");
        return name.isEmpty()?"Minecraft 玩家":name;
    }

    private PeerConnection getOrCreateVoicePeer(String peerId) {
        PeerConnection current = voicePeers.get(peerId);
        if(current!=null) return current;
        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(Arrays.asList(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        ));
        PeerConnection pc = peerFactory.createPeerConnection(config,new PeerConnection.Observer() {
            @Override public void onSignalingChange(PeerConnection.SignalingState state) {}
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
                if(state==PeerConnection.IceConnectionState.FAILED) {
                    runOnUiThread(()->{
                        voiceStatusMessage = "與成員的語音連線失敗，請檢查網路。";
                        updateVoiceUi();
                    });
                }
            }
            @Override public void onIceConnectionReceivingChange(boolean receiving) {}
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) {}
            @Override public void onIceCandidate(IceCandidate candidate) {
                try {
                    JSONObject value = new JSONObject();
                    value.put("sdpMid",candidate.sdpMid);
                    value.put("sdpMLineIndex",candidate.sdpMLineIndex);
                    value.put("candidate",candidate.sdp);
                    sendVoiceSignal("voice-ice",peerId,"candidate",value);
                } catch(Exception e) {
                    android.util.Log.w("BolsVoice","建立 ICE 訊號失敗",e);
                }
            }
            @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) {}
            @Override public void onAddStream(MediaStream stream) {
                for(AudioTrack track:stream.audioTracks) track.setEnabled(true);
            }
            @Override public void onRemoveStream(MediaStream stream) {}
            @Override public void onDataChannel(org.webrtc.DataChannel channel) {}
            @Override public void onRenegotiationNeeded() {}
            @Override public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) {
                if(receiver.track() instanceof AudioTrack) {
                    ((AudioTrack)receiver.track()).setEnabled(true);
                }
            }
        });
        if(pc==null) throw new IllegalStateException("無法建立語音連線");
        voicePeers.put(peerId,pc);
        pc.addTrack(localAudioTrack,Collections.singletonList("bols-audio"));
        return pc;
    }

    private void flushVoiceIce(String peerId,PeerConnection pc) {
        List<IceCandidate> waiting = pendingVoiceIce.remove(peerId);
        if(waiting==null) return;
        for(IceCandidate candidate:waiting) pc.addIceCandidate(candidate);
    }

    private void createVoiceOffer(String peerId) {
        if(peerId==null || peerId.isEmpty() || peerId.equals(voicePeerId) || voicePeers.containsKey(peerId)) return;
        try {
            PeerConnection pc = getOrCreateVoicePeer(peerId);
            pc.createOffer(new SdpObserverAdapter() {
                @Override public void onCreateSuccess(SessionDescription offer) {
                    pc.setLocalDescription(new SdpObserverAdapter() {
                        @Override public void onSetSuccess() {
                            sendVoiceDescription("voice-offer",peerId,offer);
                        }
                        @Override public void onSetFailure(String error) {
                            android.util.Log.w("BolsVoice","設定 Offer 失敗："+error);
                        }
                    },offer);
                }
                @Override public void onCreateFailure(String error) {
                    android.util.Log.w("BolsVoice","建立 Offer 失敗："+error);
                }
            },new MediaConstraints());
        } catch(Exception e) {
            android.util.Log.w("BolsVoice","建立語音連線失敗",e);
        }
    }

    private void sendVoiceDescription(String type,String peerId,SessionDescription description) {
        try {
            JSONObject value = new JSONObject();
            value.put("type",description.type.canonicalForm());
            value.put("sdp",description.description);
            sendVoiceSignal(type,peerId,"sdp",value);
        } catch(Exception e) {
            android.util.Log.w("BolsVoice","傳送語音描述失敗",e);
        }
    }

    private void sendVoiceSignal(String type,String peerId,String field,JSONObject value) {
        WebSocket socket = voiceSocket;
        if(socket==null) return;
        try {
            JSONObject packet = new JSONObject();
            packet.put("type",type);
            packet.put("toPeerId",peerId);
            packet.put(field,value);
            socket.send(packet.toString());
        } catch(Exception e) {
            android.util.Log.w("BolsVoice","傳送語音訊號失敗",e);
        }
    }

    private void toggleVoiceMute() {
        voiceMuted = !voiceMuted;
        if(localAudioTrack!=null) localAudioTrack.setEnabled(!voiceMuted);
        updateVoiceUi();
    }

    private void toggleSpeaker() {
        speakerEnabled = !speakerEnabled;
        if(audioManager!=null) audioManager.setSpeakerphoneOn(speakerEnabled);
        updateVoiceUi();
    }

    private void leaveVoice() {
        endVoiceSession(true);
        voiceStatusMessage = "已離開語音大廳";
        updateVoiceUi();
    }

    private void endVoiceSession(boolean closeSocket) {
        WebSocket oldSocket = voiceSocket;
        voiceSocket = null;
        voiceJoined = false;
        voiceConnecting = false;
        voicePeerId = "";
        voiceUsers.clear();
        pendingVoiceIce.clear();

        if(closeSocket && oldSocket!=null) {
            try { oldSocket.close(1000,"leave voice"); } catch(Exception ignored) {}
        }
        for(PeerConnection pc:voicePeers.values()) {
            try { pc.close(); pc.dispose(); } catch(Exception ignored) {}
        }
        voicePeers.clear();

        try { if(localAudioTrack!=null) localAudioTrack.dispose(); } catch(Exception ignored) {}
        try { if(voiceAudioSource!=null) voiceAudioSource.dispose(); } catch(Exception ignored) {}
        try { if(peerFactory!=null) peerFactory.dispose(); } catch(Exception ignored) {}
        localAudioTrack = null;
        voiceAudioSource = null;
        peerFactory = null;

        if(audioManager!=null) {
            try {
                audioManager.setSpeakerphoneOn(previousSpeakerState);
                audioManager.setMode(previousAudioMode);
            } catch(Exception ignored) {}
            audioManager = null;
        }
        if(voiceHttpClient!=null) {
            try { voiceHttpClient.dispatcher().executorService().shutdown(); } catch(Exception ignored) {}
            try { voiceHttpClient.connectionPool().evictAll(); } catch(Exception ignored) {}
            voiceHttpClient = null;
        }
        updateVoiceParticipants();
        updateVoiceUi();
    }

    private void updateVoiceParticipants() {
        StringBuilder result = new StringBuilder();
        if(voiceJoined) result.append("• 你（已加入）");
        for(Map.Entry<String,String> entry:voiceUsers.entrySet()) {
            if(result.length()>0) result.append("\n");
            result.append("• ").append(entry.getValue());
        }
        if(result.length()==0) result.append("目前沒有其他成員，加入後等待朋友進入。");
        TextView view = voiceParticipantsView;
        if(view!=null) runOnUiThread(()->view.setText(result.toString()));
    }

    private void updateVoiceUi() {
        runOnUiThread(()->{
            if(voiceStatusView!=null) voiceStatusView.setText(voiceStatusMessage);
            if(voiceJoinButton!=null) {
                voiceJoinButton.setEnabled(!voiceJoined && !voiceConnecting);
                voiceJoinButton.setText(voiceConnecting?"連線中…":(voiceJoined?"已加入":"加入語音大廳"));
            }
            if(voiceMuteButton!=null) {
                voiceMuteButton.setEnabled(voiceJoined);
                voiceMuteButton.setText(voiceMuted?"取消靜音":"麥克風靜音");
            }
            if(voiceSpeakerButton!=null) {
                voiceSpeakerButton.setText(speakerEnabled?"喇叭開啟":"喇叭關閉");
            }
            if(voiceLeaveButton!=null) voiceLeaveButton.setEnabled(voiceJoined || voiceConnecting);
        });
    }

    private void onVoicePermissionResult(boolean granted) {
        if(!pendingVoiceJoin) return;
        pendingVoiceJoin = false;
        if(granted) startVoiceConnection();
        else Toast.makeText(this,"需要允許麥克風權限才能使用語音。",Toast.LENGTH_LONG).show();
    }

    private static class SdpObserverAdapter implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription description) {}
        @Override public void onSetSuccess() {}
        @Override public void onCreateFailure(String error) {}
        @Override public void onSetFailure(String error) {}
    }

    private void addSectionHeader(String title,String eyebrow) {
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(txt(title,17,WHITE,true),new LinearLayout.LayoutParams(0,-2,1));
        TextView label=txt(eyebrow,9,MUTED,true); label.setLetterSpacing(0.10f); row.addView(label); body.addView(row);
    }

    private void addFeature(String title,String desc,String symbol,Runnable click) {
        LinearLayout card=panel(); card.setOrientation(LinearLayout.HORIZONTAL); card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(13),dp(13),dp(14),dp(13));
        TextView icon=txt(symbol,22,MINT,true); icon.setGravity(Gravity.CENTER); icon.setBackground(round(PANEL2,dp(14)));
        card.addView(icon,new LinearLayout.LayoutParams(dp(46),dp(46)));
        LinearLayout words=new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL); words.setPadding(dp(13),0,dp(7),0);
        words.addView(txt(title,14,WHITE,true)); TextView description=txt(desc,11,MUTED,false);
        LinearLayout.LayoutParams descLp=new LinearLayout.LayoutParams(-1,-2); descLp.topMargin=dp(3); words.addView(description,descLp);
        card.addView(words,new LinearLayout.LayoutParams(0,-2,1)); card.addView(txt("›",26,MUTED,false));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(9); body.addView(card,lp);
        card.setOnClickListener(v->click.run());
    }

    private void requireLogin() {
        LinearLayout p=panel(); p.addView(txt("需要先登入",17,WHITE,true));
        TextView message=txt("登入後即可使用這項社群功能。",12,MUTED,false);
        LinearLayout.LayoutParams messageLp=new LinearLayout.LayoutParams(-1,-2); messageLp.topMargin=dp(5); p.addView(message,messageLp);
        Button b=button("使用 Google 登入"); LinearLayout.LayoutParams buttonLp=new LinearLayout.LayoutParams(-1,dp(48));
        buttonLp.topMargin=dp(13); p.addView(b,buttonLp); b.setOnClickListener(v->startGoogleLogin()); body.addView(p);
    }

    private void startGoogleLogin() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(AUTH))); }
        catch(Exception e){ Toast.makeText(this,"無法開啟 Google 登入。",Toast.LENGTH_LONG).show(); }
    }




    private void registerCurrentPushToken() {
        try {
            if(FirebaseApp.getApps(this).isEmpty()) {
                pushFirebaseStatus = "未初始化：APK 沒有載入 Firebase 設定";
                pushTokenStatus = "無法取得";
                pushRegistrationStatus = "未註冊";
                updatePushDiagnosticDialog();
                android.util.Log.w("BolsPush","FirebaseApp is not initialized");
                return;
            }
            pushFirebaseStatus = "已初始化";
            updatePushDiagnosticDialog();
            FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    if(token==null || token.trim().isEmpty()) {
                        pushTokenStatus = "取得失敗：Firebase 回傳空 Token";
                        pushRegistrationStatus = "未註冊";
                        updatePushDiagnosticDialog();
                        return;
                    }
                    String currentToken = token.trim();
                    String tokenHint = currentToken.length() > 14
                        ? currentToken.substring(0,8) + "…" + currentToken.substring(currentToken.length()-6)
                        : "已取得";
                    pushTokenStatus = "已取得（" + tokenHint + "）";
                    if(session.isEmpty()) {
                        pushRegistrationStatus = "尚未登入 App 帳號";
                        updatePushDiagnosticDialog();
                        return;
                    }
                    String authSession = session;
                    pushRegistrationStatus = "正在向 Worker 註冊…";
                    updatePushDiagnosticDialog();
                    new Thread(() -> {
                        HttpURLConnection connection=null;
                        try {
                            connection=(HttpURLConnection)new URL(API+"/api/push/device/register").openConnection();
                            connection.setRequestMethod("POST");
                            connection.setConnectTimeout(12000);
                            connection.setReadTimeout(12000);
                            connection.setDoOutput(true);
                            connection.setRequestProperty("Content-Type","application/json; charset=UTF-8");
                            connection.setRequestProperty("Authorization","Bearer "+authSession);
                            JSONObject payload=new JSONObject();
                            payload.put("token",currentToken);
                            try(OutputStream os=connection.getOutputStream()) {
                                os.write(payload.toString().getBytes("UTF-8"));
                            }
                            int status=connection.getResponseCode();
                            java.io.InputStream stream = status>=200 && status<300
                                ? connection.getInputStream() : connection.getErrorStream();
                            StringBuilder responseText=new StringBuilder();
                            if(stream!=null) {
                                BufferedReader reader=new BufferedReader(new InputStreamReader(stream,"UTF-8"));
                                String line;
                                while((line=reader.readLine())!=null) responseText.append(line);
                                reader.close();
                            }
                            String detail="";
                            try {
                                JSONObject data=new JSONObject(responseText.toString());
                                detail=data.optString("error","");
                            } catch(Exception ignored) {}
                            if(status>=200 && status<300) {
                                pushRegistrationStatus="已註冊（HTTP "+status+"）";
                            } else {
                                pushRegistrationStatus="註冊失敗（HTTP "+status+"）"
                                    + (detail.isEmpty() ? "" : "：" + detail);
                            }
                        } catch(Exception e) {
                            pushRegistrationStatus="連線失敗："+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());
                            android.util.Log.w("BolsPush","Token registration failed",e);
                        } finally {
                            if(connection!=null) connection.disconnect();
                            updatePushDiagnosticDialog();
                        }
                    }).start();
                })
                .addOnFailureListener(error -> {
                    pushTokenStatus="取得失敗："+(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage());
                    pushRegistrationStatus="未註冊";
                    android.util.Log.w("BolsPush","FCM token retrieval failed",error);
                    updatePushDiagnosticDialog();
                });
        } catch(Exception e) {
            pushFirebaseStatus="初始化錯誤："+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());
            pushTokenStatus="無法取得";
            pushRegistrationStatus="未註冊";
            android.util.Log.w("BolsPush","Firebase is not ready",e);
            updatePushDiagnosticDialog();
        }
    }

    private String buildPushDiagnosticText() {
        String permissionStatus;
        if(Build.VERSION.SDK_INT>=33) {
            permissionStatus=checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
                ? "已允許" : "未允許（請到 Android 設定開啟通知）";
        } else {
            permissionStatus="此 Android 版本不需要額外通知權限";
        }
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(manager!=null && !manager.areNotificationsEnabled()) {
            permissionStatus += "；此 App 的系統通知目前已關閉";
        }
        if(Build.VERSION.SDK_INT>=26 && manager!=null) {
            NotificationChannel channel=manager.getNotificationChannel("bols_server_notifications");
            if(channel!=null && channel.getImportance()==NotificationManager.IMPORTANCE_NONE) {
                permissionStatus += "；「無垠伺服器通知」頻道已被關閉";
            }
        }
        return "Android 通知權限："+permissionStatus
            + "\nFirebase："+pushFirebaseStatus
            + "\nFCM Token："+pushTokenStatus
            + "\nWorker 註冊："+pushRegistrationStatus
            + "\n\n若顯示 HTTP 503，通常代表 Worker 尚未設定 FCM 服務帳戶；HTTP 401 代表登入工作階段未通過。";
    }

    private void updatePushDiagnosticDialog() {
        runOnUiThread(() -> {
            if(pushDiagnosticDialog!=null && pushDiagnosticDialog.isShowing()) {
                pushDiagnosticDialog.setMessage(buildPushDiagnosticText());
            }
        });
    }

    private void showPushDiagnostics() {
        pushDiagnosticDialog=new AlertDialog.Builder(this)
            .setTitle("通知診斷")
            .setMessage(buildPushDiagnosticText())
            .setPositiveButton("重新檢查",null)
            .setNegativeButton("關閉",null)
            .create();
        pushDiagnosticDialog.setOnShowListener(dialog -> {
            Button retry=pushDiagnosticDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            retry.setOnClickListener(v -> {
                pushFirebaseStatus="檢查中…";
                pushTokenStatus="檢查中…";
                pushRegistrationStatus="檢查中…";
                pushDiagnosticDialog.setMessage(buildPushDiagnosticText());
                registerCurrentPushToken();
            });
        });
        pushDiagnosticDialog.show();
        pushDiagnosticDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            pushFirebaseStatus="檢查中…";
            pushTokenStatus="檢查中…";
            pushRegistrationStatus="檢查中…";
            updatePushDiagnosticDialog();
            registerCurrentPushToken();
        });
        registerCurrentPushToken();
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
                    if(items==null||items.length()==0){
                        target.addView(txt("目前還沒有訊息。",13,MUTED,false));
                        scrollChatToBottom(target);
                        return;
                    }
                    for(int i=0;i<items.length();i++){
                        JSONObject m=items.optJSONObject(i); if(m==null)continue;
                        JSONObject u=m.optJSONObject("user"); String who=u==null?"玩家":u.optString("name","玩家");
                        String message=m.optString("text","");
                        boolean mine=!userName.isEmpty() && who.equalsIgnoreCase(userName);
                        LinearLayout card=panel(); card.setPadding(dp(13),dp(12),dp(13),dp(12));
                        card.setBackground(outline(mine?Color.rgb(26,43,59):PANEL,mine?Color.rgb(66,112,124):BORDER,dp(17)));
                        LinearLayout messageHeader=new LinearLayout(this); messageHeader.setGravity(Gravity.CENTER_VERTICAL);
                        TextView avatar=txt(who.isEmpty()?"?":who.substring(0,1).toUpperCase(Locale.getDefault()),13,BG,true);
                        avatar.setGravity(Gravity.CENTER); avatar.setBackground(round(mine?MINT:ACCENT,dp(12)));
                        messageHeader.addView(avatar,new LinearLayout.LayoutParams(dp(32),dp(32)));
                        LinearLayout authorBox=new LinearLayout(this); authorBox.setOrientation(LinearLayout.VERTICAL); authorBox.setPadding(dp(9),0,0,0);
                        authorBox.addView(txt(who,12,WHITE,true));
                        long time=m.optLong("createdAt",0);
                        String timeText=time>0?new SimpleDateFormat("MM/dd  HH:mm",Locale.getDefault()).format(new Date(time)):"";
                        authorBox.addView(txt(timeText,9,MUTED,false));
                        messageHeader.addView(authorBox,new LinearLayout.LayoutParams(0,-2,1));
                        if(mine) { TextView ownTag=txt("你",9,MINT,true); ownTag.setPadding(dp(8),dp(5),dp(8),dp(5)); ownTag.setBackground(round(PANEL2,dp(20))); messageHeader.addView(ownTag); }
                        card.addView(messageHeader);
                        TextView messageView=txt(message,14,WHITE,false); messageView.setLineSpacing(dp(3),1.0f);
                        messageView.setTextIsSelectable(true); Linkify.addLinks(messageView,Linkify.WEB_URLS);
                        messageView.setMovementMethod(LinkMovementMethod.getInstance()); messageView.setLinkTextColor(MINT);
                        LinearLayout.LayoutParams messageLp=new LinearLayout.LayoutParams(-1,-2); messageLp.topMargin=dp(10); card.addView(messageView,messageLp);
                        LinearLayout.LayoutParams cardLp=new LinearLayout.LayoutParams(-1,-2); cardLp.bottomMargin=dp(9); target.addView(card,cardLp);
                    }
                    scrollChatToBottom(target);
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
        LinearLayout p=new LinearLayout(this); p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(16),dp(15),dp(16),dp(15)); p.setBackground(outline(PANEL,BORDER,dp(20))); return p;
    }

    private TextView txt(String s,int size,int color,boolean bold) {
        TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); t.setIncludeFontPadding(false);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setPadding(0,dp(2),0,dp(2)); return t;
    }

    private Button button(String s) {
        Button b=new Button(this); b.setText(s); b.setTextColor(BG); b.setAllCaps(false); b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setGravity(Gravity.CENTER); b.setMinHeight(dp(46));
        b.setPadding(dp(16),0,dp(16),0); b.setBackground(gradient(MINT,Color.rgb(91,193,218),dp(14))); b.setElevation(dp(2)); return b;
    }

    private Button secondaryButton(String s) {
        Button b=new Button(this); b.setText(s); b.setTextColor(WHITE); b.setAllCaps(false); b.setTextSize(11);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setGravity(Gravity.CENTER); b.setMinHeight(dp(44));
        b.setPadding(dp(5),0,dp(5),0); b.setBackground(outline(PANEL2,BORDER,dp(13))); return b;
    }

    private GradientDrawable round(int color,int radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d;
    }

    private GradientDrawable gradient(int start,int end,int radius) {
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{start,end}); d.setCornerRadius(radius); return d;
    }

    private GradientDrawable outline(int color,int stroke,int radius) {
        GradientDrawable d=round(color,radius); d.setStroke(dp(1),stroke); return d;
    }

    private void addSpace(int h) { View v=new View(this); body.addView(v,new LinearLayout.LayoutParams(1,dp(h))); }
    private void addSpaceTo(LinearLayout target,int h) { View v=new View(this); target.addView(v,new LinearLayout.LayoutParams(1,dp(h))); }
    private int dp(float v) { return (int)(v*getResources().getDisplayMetrics().density+0.5f); }

    private void askNotificationPermission() {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1202);
    }

    private void handleAuthIntent(Intent intent) {
        if(intent==null) return;
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

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==4021) {
            boolean granted = grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED;
            onVoicePermissionResult(granted);
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        handleAuthIntent(intent);
    }

    @Override protected void onDestroy() {
        endVoiceSession(true);
        super.onDestroy();
    }
}
