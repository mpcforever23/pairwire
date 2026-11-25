package com.darkengine.app;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(DarkWatchPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
