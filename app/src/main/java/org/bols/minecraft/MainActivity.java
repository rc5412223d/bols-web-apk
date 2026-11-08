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
    private static final int BG=Color.rgb(8,16,18), PANEL=Color.rgb(18,34,34);
    private static final int PANEL2=Color.rgb(25,47,43), MINT=Color.rgb(125,238,183);
    private static final int WHITE=Color.rgb(239,248,244), MUTED=Color.rgb(151,177,168);
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
        contentFrame=new FrameLayout(this);
        root.addView(contentFrame,new LinearLayout.LayoutParams(-1,0,1));
        nav=new LinearLayout(this); nav.setGravity(Gravity.CENTER); nav.setPadding(dp(5),dp(7),dp(5),dp(7)); nav.setBackground(round(PANEL,dp(22)));
        root.addView(nav,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);
        buildNav();
    }

    private void buildNav() {
        nav.removeAllViews();
        String[][] items={{"首頁","⌂"},{"聊天室","▤"},{"語音","🎙"},{"公告","♢"},{"我的","●"}};
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
        active=tab;
        buildNav();
        contentFrame.removeAllViews();
        chatScroll=null;
        activeChatMessages=null;
        voiceStatusView=null;
        voiceParticipantsView=null;
        voiceJoinButton=null;
        voiceMuteButton=null;
        voiceSpeakerButton=null;
        voiceLeaveButton=null;
        if(tab.equals("聊天室")) {
            showChatScreen();
            return;
        }
        if(tab.equals("語音")) {
            showVoiceScreen();
            return;
        }
        body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        ScrollView pageScroll=new ScrollView(this);
        pageScroll.setFillViewport(true);
        pageScroll.addView(body);
        contentFrame.addView(pageScroll,new FrameLayout.LayoutParams(-1,-1));
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
            action.setOnClickListener(v->{if(session.isEmpty())startGoogleLogin();else logout();});
            Button pushCheck=button("通知診斷 / 重新註冊");
            LinearLayout.LayoutParams pushCheckLp=new LinearLayout.LayoutParams(-1,dp(48));
            pushCheckLp.topMargin=dp(10);
            p.addView(pushCheck,pushCheckLp);
            pushCheck.setOnClickListener(v->showPushDiagnostics());
            body.addView(p);
        }
    }


    private void showChatScreen() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        contentFrame.addView(screen, new FrameLayout.LayoutParams(-1,-1));

        screen.addView(txt("聊天室",24,WHITE,true));
        screen.addView(txt("伺服器聊天頻道",12,MUTED,false));
        View topGap = new View(this);
        screen.addView(topGap,new LinearLayout.LayoutParams(1,dp(10)));

        if(session.isEmpty()) {
            LinearLayout login = panel();
            login.addView(txt("需要先登入",17,WHITE,true));
            login.addView(txt("登入後即可使用伺服器聊天室。",12,MUTED,false));
            Button loginButton = button("使用 Google 登入");
            login.addView(loginButton);
            loginButton.setOnClickListener(v->startGoogleLogin());
            screen.addView(login,new LinearLayout.LayoutParams(-1,-2));
            return;
        }

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(txt("伺服器聊天",13,MUTED,false),new LinearLayout.LayoutParams(0,-2,1));
        Button refresh = button("重新整理");
        toolbar.addView(refresh);
        screen.addView(toolbar,new LinearLayout.LayoutParams(-1,-2));

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        chatScroll.setSmoothScrollingEnabled(true);
        chatScroll.setClipToPadding(false);
        chatScroll.setPadding(0,dp(7),0,dp(7));

        LinearLayout messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        chatScroll.addView(messages,new ScrollView.LayoutParams(-1,-2));
        activeChatMessages = messages;
        screen.addView(chatScroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(WHITE);
        input.setHintTextColor(MUTED);
        input.setHint("輸入訊息…");
        input.setTextSize(14);
        input.setBackground(round(PANEL,dp(13)));
        input.setPadding(dp(12),dp(8),dp(12),dp(8));
        composer.addView(input,new LinearLayout.LayoutParams(0,dp(48),1));
        Button send = button("傳送");
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(-2,dp(48));
        sendLp.leftMargin = dp(8);
        composer.addView(send,sendLp);
        LinearLayout.LayoutParams composerLp = new LinearLayout.LayoutParams(-1,-2);
        composerLp.topMargin = dp(8);
        screen.addView(composer,composerLp);

        refresh.setOnClickListener(v->loadChat("server",messages));
        send.setOnClickListener(v->{
            String value = input.getText().toString().trim();
            if(!value.isEmpty()) {
                input.setText("");
                postChat(value,messages);
            }
        });
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
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        contentFrame.addView(screen,new FrameLayout.LayoutParams(-1,-1));

        screen.addView(txt("語音頻道",24,WHITE,true));
        screen.addView(txt("加入大廳，與在線成員即時通話",12,MUTED,false));
        View topGap = new View(this);
        screen.addView(topGap,new LinearLayout.LayoutParams(1,dp(12)));

        if(session.isEmpty()) {
            LinearLayout login = panel();
            login.addView(txt("需要先登入",17,WHITE,true));
            login.addView(txt("登入後即可加入語音大廳。",12,MUTED,false));
            Button loginButton = button("使用 Google 登入");
            login.addView(loginButton);
            loginButton.setOnClickListener(v->startGoogleLogin());
            screen.addView(login,new LinearLayout.LayoutParams(-1,-2));
            return;
        }

        LinearLayout statusCard = panel();
        statusCard.addView(txt("大廳語音",14,WHITE,true));
        voiceStatusView = txt(voiceStatusMessage,12,MINT,false);
        statusCard.addView(voiceStatusView);
        screen.addView(statusCard,new LinearLayout.LayoutParams(-1,-2));

        View gap = new View(this);
        screen.addView(gap,new LinearLayout.LayoutParams(1,dp(10)));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        voiceJoinButton = button("加入語音");
        voiceMuteButton = button(voiceMuted ? "取消靜音" : "麥克風靜音");
        voiceSpeakerButton = button(speakerEnabled ? "🔊 喇叭開" : "🔈 喇叭關");
        voiceLeaveButton = button("離開");

        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0,dp(48),1);
        a.setMargins(0,0,dp(4),0);
        controls.addView(voiceJoinButton,a);
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0,dp(48),1);
        b.setMargins(dp(2),0,dp(2),0);
        controls.addView(voiceMuteButton,b);
        controls.addView(voiceSpeakerButton,b);
        LinearLayout.LayoutParams c = new LinearLayout.LayoutParams(0,dp(48),1);
        c.setMargins(dp(4),0,0,0);
        controls.addView(voiceLeaveButton,c);
        screen.addView(controls,new LinearLayout.LayoutParams(-1,-2));

        View gap2 = new View(this);
        screen.addView(gap2,new LinearLayout.LayoutParams(1,dp(12)));
        LinearLayout participants = panel();
        participants.addView(txt("語音成員",15,WHITE,true));
        voiceParticipantsView = txt("",12,MUTED,false);
        voiceParticipantsView.setPadding(0,dp(8),0,0);
        participants.addView(voiceParticipantsView);
        screen.addView(participants,new LinearLayout.LayoutParams(-1,-2));

        voiceJoinButton.setOnClickListener(v->requestVoicePermissionAndJoin());
        voiceMuteButton.setOnClickListener(v->toggleVoiceMute());
        voiceSpeakerButton.setOnClickListener(v->toggleSpeaker());
        voiceLeaveButton.setOnClickListener(v->leaveVoice());
        updateVoiceUi();
        updateVoiceParticipants();
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
                voiceJoinButton.setText(voiceConnecting?"連線中…":(voiceJoined?"已加入":"加入語音"));
            }
            if(voiceMuteButton!=null) {
                voiceMuteButton.setEnabled(voiceJoined);
                voiceMuteButton.setText(voiceMuted?"取消靜音":"麥克風靜音");
            }
            if(voiceSpeakerButton!=null) {
                voiceSpeakerButton.setText(speakerEnabled?"🔊 喇叭開":"🔈 喇叭關");
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
                                pushRegistrationStatus="註冊失敗（HTTP "+status+")"
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

    private void loadChat(String channel, LinearLayout target) {        try {
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
                    if(items==null||items.length()==0){
                        target.addView(txt("目前還沒有訊息。",13,MUTED,false));
                        scrollChatToBottom(target);
                        return;
                    }
                    for(int i=0;i<items.length();i++){
                        JSONObject m=items.optJSONObject(i); if(m==null)continue;
                        JSONObject u=m.optJSONObject("user"); String who=u==null?"玩家":u.optString("name","玩家");
                        String message=m.optString("text","");
                        LinearLayout card=panel(); card.addView(txt(who,12,MINT,true));
                        TextView messageView = txt(message,14,WHITE,false);
                        // Convert URLs in chat messages into tappable links.
                        Linkify.addLinks(messageView,Linkify.WEB_URLS);
                        messageView.setMovementMethod(LinkMovementMethod.getInstance());
                        messageView.setLinkTextColor(MINT);
                        card.addView(messageView);
                        long time=m.optLong("createdAt",0);
                        if(time>0)card.addView(txt(new SimpleDateFormat("MM/dd HH:mm",Locale.getDefault()).format(new Date(time)),10,MUTED,false));
                        target.addView(card);
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
