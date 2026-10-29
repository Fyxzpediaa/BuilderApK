package com.fyxzpedia.photoeditor;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Build;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RemoteActivity extends Activity {
    private final ExecutorService io = Executors.newCachedThreadPool();
    private EditText serverField;
    private TextView pairCode, state, flashState;
    private Button connectButton, disconnectButton, flashButton;
    private SharedPreferences prefs;
    private volatile boolean polling = false;
    private volatile boolean flashOn = false;
    private volatile String deviceId = "";
    private volatile String clientToken = "";
    private String pendingCommand = "";
    private static final int REQ_CAMERA = 601;
    private static final int REQ_FLASH = 602;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.rgb(11,13,19));
        getWindow().setNavigationBarColor(Color.rgb(11,13,19));
        prefs = getSharedPreferences("remote", MODE_PRIVATE);
        deviceId = prefs.getString("deviceId", "");
        clientToken = prefs.getString("clientToken", "");
        buildUi();
        if (!clientToken.isEmpty()) {
            pairCode.setText("Paired device ID: " + deviceId);
            state.setText("Saved pairing found. Tap Connect to resume.");
        }
    }

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private TextView label(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD); return t;
    }
    private android.graphics.drawable.GradientDrawable bg(int color) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(14)); return d;
    }
    private Button btn(String s, boolean primary) {
        Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(13); b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(primary ? Color.rgb(12,14,20) : Color.WHITE);
        b.setBackground(bg(primary ? Color.rgb(155,140,255) : Color.rgb(27,31,44))); return b;
    }
    private void buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(11,13,19));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(14),dp(18),dp(24));
        scroll.addView(root);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setOrientation(LinearLayout.HORIZONTAL);
        header.addView(label("REMOTE CONTROL",19,Color.WHITE,true), new LinearLayout.LayoutParams(0,-2,1));
        Button back = btn("← Editor",false); back.setOnClickListener(v -> finish()); header.addView(back); root.addView(header);
        TextView desc = label("Telegram commands are requests only. Camera and flash actions require confirmation on this device.",13,Color.rgb(160,166,184),false);
        desc.setPadding(0,dp(8),0,dp(16)); root.addView(desc);

        LinearLayout setup = card(root,"01  ·  CONNECTION");
        serverField = new EditText(this); serverField.setSingleLine(true); serverField.setTextColor(Color.WHITE); serverField.setHintTextColor(Color.GRAY);
        serverField.setHint("https://your-control-server.example"); serverField.setText(prefs.getString("serverUrl",""));
        serverField.setTextSize(13); serverField.setPadding(dp(12),dp(10),dp(12),dp(10)); serverField.setBackground(bg(Color.rgb(11,14,22)));
        setup.addView(serverField,new LinearLayout.LayoutParams(-1,dp(48)));
        connectButton = btn("Register / Connect",true); LinearLayout.LayoutParams c = new LinearLayout.LayoutParams(-1,dp(48)); c.topMargin=dp(10); setup.addView(connectButton,c);
        pairCode = label("Pair code will appear here",13,Color.rgb(205,210,228),true); pairCode.setPadding(0,dp(12),0,dp(4)); setup.addView(pairCode);
        state = label("Disconnected",12,Color.rgb(153,161,183),false); setup.addView(state);
        disconnectButton = btn("Disconnect",false); LinearLayout.LayoutParams dc=new LinearLayout.LayoutParams(-1,dp(44)); dc.topMargin=dp(8); setup.addView(disconnectButton,dc);
        connectButton.setOnClickListener(v -> registerOrConnect());
        disconnectButton.setOnClickListener(v -> disconnect());

        LinearLayout controls = card(root,"02  ·  LOCAL CONTROLS");
        controls.addView(label("Flashlight",14,Color.WHITE,true));
        flashState = label("Status: unknown",12,Color.rgb(153,161,183),false); flashState.setPadding(0,dp(4),0,dp(8)); controls.addView(flashState);
        flashButton = btn("Toggle flashlight",false); controls.addView(flashButton,new LinearLayout.LayoutParams(-1,dp(46)));
        flashButton.setOnClickListener(v -> requestFlashPermissionThenToggle());
        Button camera = btn("Open camera app",false); LinearLayout.LayoutParams camlp=new LinearLayout.LayoutParams(-1,dp(46)); camlp.topMargin=dp(8); controls.addView(camera,camlp);
        camera.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Open camera")
                .setMessage("The camera app will open visibly. Choose the front or rear camera there; no photo is taken or uploaded automatically.")
                .setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->openCamera("camera")).show();

        LinearLayout safety = card(root,"03  ·  SAFETY");
        safety.addView(label("• Commands are checked only while this screen is open.\n• Camera requests open the visible camera app.\n• Flash requests require confirmation and Android permission.\n• No background camera capture or file scanning.\n• Keep the server private and allowlist your Telegram ID.",12,Color.rgb(176,183,202),false));
        setContentView(scroll);
    }
    private LinearLayout card(LinearLayout root,String title) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(14),dp(14),dp(14),dp(14)); box.setBackground(bg(Color.rgb(20,23,33)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(12); root.addView(box,lp);
        TextView t=label(title,12,Color.rgb(155,140,255),true); t.setPadding(0,0,0,dp(12)); box.addView(t); return box;
    }

    private void registerOrConnect() {
        String base = serverField.getText().toString().trim().replaceAll("/+$","");
        if (!base.startsWith("https://")) {
            toast("Use an HTTPS URL for the control server."); return;
        }
        prefs.edit().putString("serverUrl",base).apply();
        if (!clientToken.isEmpty() && !deviceId.isEmpty()) { startPolling(); return; }
        state.setText("Registering device…"); connectButton.setEnabled(false);
        io.execute(() -> {
            try {
                JSONObject result = request("POST",base+"/api/register",new JSONObject());
                deviceId=result.getString("deviceId"); clientToken=result.getString("clientToken"); String code=result.getString("pairCode");
                prefs.edit().putString("deviceId",deviceId).putString("clientToken",clientToken).apply();
                runOnUiThread(() -> { pairCode.setText("PAIR CODE: " + code + "\nSend /pair " + code + " to your Telegram bot"); state.setText("Registered. Pair through Telegram, then Connect."); connectButton.setText("Connect"); });
                startPolling();
            } catch(Exception e) { runOnUiThread(()->state.setText("Register failed: "+safeMessage(e))); }
            finally { runOnUiThread(()->connectButton.setEnabled(true)); }
        });
    }
    private void startPolling() {
        if (polling) return;
        polling=true; runOnUiThread(()->{state.setText("Connected · waiting for Telegram commands"); connectButton.setText("Connected");});
        io.execute(() -> {
            while(polling) {
                try {
                    String base=prefs.getString("serverUrl","");
                    JSONObject result=request("GET",base+"/api/poll?deviceId="+Uri.encode(deviceId),null);
                    JSONArray commands=result.optJSONArray("commands");
                    if(commands!=null) for(int i=0;i<commands.length();i++) {
                        JSONObject cmd=commands.getJSONObject(i); String id=cmd.optString("id"); String action=cmd.optString("action");
                        runOnUiThread(()->handleCommand(id,action));
                    }
                } catch(Exception e) { runOnUiThread(()->state.setText("Connection issue · "+safeMessage(e))); }
                try { Thread.sleep(2500); } catch(InterruptedException ignored) { Thread.currentThread().interrupt(); break; }
            }
        });
    }
    private void handleCommand(String id,String action) {
        if (id == null || id.isEmpty() || action == null) return;
        if (action.equals("status")) { sendAck(id,true,deviceStatus()); return; }
        if (!action.equals("camera") && !action.equals("camera_front") && !action.equals("camera_rear") && !action.equals("flash_on") && !action.equals("flash_off")) { sendAck(id,false,"Unsupported action"); return; }
        boolean cameraAction=action.startsWith("camera");
        String title= cameraAction ? "Telegram requests camera" : "Telegram requests flashlight";
        String message= cameraAction ? "Open the visible camera app now? The front/rear hint may be ignored by some camera apps. No photo will be taken or sent automatically." : "Allow Telegram to change the flashlight state?";
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setCancelable(false)
            .setNegativeButton("Deny",(d,w)->sendAck(id,false,"Denied on device"))
            .setPositiveButton("Allow",(d,w)->{
                if(cameraAction) { sendAck(id,true,"Opening visible camera app"); openCamera(action); }
                else { boolean target=action.equals("flash_on"); pendingCommand=id; pendingFlashTarget=target; requestFlashPermissionThenToggleTarget(target); }
            }).show();
    }
    private boolean pendingFlashTarget=false;
    private void requestFlashPermissionThenToggle() { requestFlashPermissionThenToggleTarget(!flashOn); }
    private void requestFlashPermissionThenToggleTarget(boolean target) {
        pendingFlashTarget=target;
        if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA},REQ_FLASH); return;
        }
        setFlash(target);
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==REQ_FLASH) {
            if(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED) setFlash(pendingFlashTarget);
            else { toast("Camera permission denied"); if(!pendingCommand.isEmpty()) sendAck(pendingCommand,false,"Camera permission denied"); pendingCommand=""; }
        }
    }
    private void setFlash(boolean enabled) {
        try {
            CameraManager cm=(CameraManager)getSystemService(Context.CAMERA_SERVICE); String chosen=null;
            for(String id:cm.getCameraIdList()) { CameraCharacteristics cc=cm.getCameraCharacteristics(id); Boolean has=cc.get(CameraCharacteristics.FLASH_INFO_AVAILABLE); Integer facing=cc.get(CameraCharacteristics.LENS_FACING); if(Boolean.TRUE.equals(has) && facing!=null && facing==CameraCharacteristics.LENS_FACING_BACK){chosen=id;break;} }
            if(chosen==null) throw new Exception("No rear flash found");
            cm.setTorchMode(chosen,enabled); flashOn=enabled; flashState.setText("Status: "+(enabled?"ON":"OFF")); flashButton.setText(enabled?"Turn flashlight OFF":"Turn flashlight ON");
            if(!pendingCommand.isEmpty()) { sendAck(pendingCommand,true,"Flashlight set to "+(enabled?"ON":"OFF")); pendingCommand=""; }
        } catch(Exception e) { toast("Flash unavailable: "+safeMessage(e)); if(!pendingCommand.isEmpty()) {sendAck(pendingCommand,false,safeMessage(e));pendingCommand="";} }
    }
    private void openCamera(String action) { try {
        Intent i=new Intent("android.media.action.IMAGE_CAPTURE");
        if(action.equals("camera_front")) i.putExtra("android.intent.extras.CAMERA_FACING", 1);
        if(action.equals("camera_rear")) i.putExtra("android.intent.extras.CAMERA_FACING", 0);
        if(i.resolveActivity(getPackageManager())!=null) startActivityForResult(i,REQ_CAMERA); else toast("No camera app available");
    } catch(Exception e){toast("Unable to open camera");} }
    private String deviceStatus() {
        BatteryManager bm=(BatteryManager)getSystemService(BATTERY_SERVICE); int level=bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return "Battery: "+level+"%\nAndroid: "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")\nApp screen: active";
    }
    private void sendAck(String id,boolean ok,String message) {
        io.execute(()->{try {JSONObject j=new JSONObject();j.put("id",id);j.put("ok",ok);j.put("message",message);request("POST",prefs.getString("serverUrl","")+"/api/ack",j);}catch(Exception ignored){}});
    }
    private JSONObject request(String method,String urlText,JSONObject body) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(urlText).openConnection(); c.setRequestMethod(method); c.setConnectTimeout(6000); c.setReadTimeout(6000); c.setRequestProperty("Authorization","Bearer "+clientToken);
        if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
        int code=c.getResponseCode(); java.io.InputStream stream=code>=200&&code<400?c.getInputStream():c.getErrorStream();
        StringBuilder sb=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)sb.append(line);}
        c.disconnect(); if(code<200||code>=300) throw new Exception("HTTP "+code+": "+sb); return new JSONObject(sb.toString());
    }
    private void disconnect() { polling=false; flashOn=false; pendingCommand=""; clientToken=""; deviceId=""; prefs.edit().remove("clientToken").remove("deviceId").apply(); pairCode.setText("Not paired");state.setText("Disconnected");connectButton.setText("Register / Connect");flashState.setText("Status: unknown"); }
    private String safeMessage(Exception e) { String m=e.getMessage(); return m==null?"unknown error":m.length()>100?m.substring(0,100):m; }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    @Override protected void onDestroy() { polling=false; io.shutdownNow(); super.onDestroy(); }
}
