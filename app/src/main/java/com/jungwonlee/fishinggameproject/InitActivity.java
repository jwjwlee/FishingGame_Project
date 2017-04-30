package com.jungwonlee.fishinggameproject;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class InitActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_init);
    }

    public void btnStart(View v) {
        Intent intent  = new Intent(getApplicationContext(), MainActivity.class);
        startActivity(intent);
    }

}
