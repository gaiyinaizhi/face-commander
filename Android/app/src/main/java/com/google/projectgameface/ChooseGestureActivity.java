/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.projectgameface;

import android.util.Log;
import android.view.MenuItem;
import android.view.WindowManager.LayoutParams;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Objects;

public class ChooseGestureActivity extends AppCompatActivity {

    private static final String TAG = "ChooseGestureActivity";
    private LinearLayout unFocus;

    private String currentTextSuffix;
    private String inUseTextSuffix;

    ConstraintLayout chooseGestureLayout;

    // What is the target action for this page.
    BlendshapeEventTriggerConfig.EventType pageEventType;

    // What button should be pre-selected when user first open the page.
    int preSelectButtonIndex;

    private BlendshapeEventTriggerConfig.Blendshape selectedBlendshape;


    /**
     * Set gesture box as "In use" state by dim the color, change text
     * and disable clickable.
     * @param oneGestureBox LinearLayout item contain gesture box.
     */
    private void changeButtonStyleToInUse(View oneGestureBox){
        if (!(oneGestureBox instanceof ViewGroup))
        {return;}

        ViewGroup oneGestureBoxViewGroup = (ViewGroup) oneGestureBox;
        oneGestureBoxViewGroup.setClickable(false);
        oneGestureBoxViewGroup.setAlpha(0.3f);

        TextView gestureText = oneGestureBoxViewGroup.findViewWithTag("text_view_gesture_name");

        if (gestureText == null)
        {return;}


        String newTextLabel = (String) gestureText.getText();
        newTextLabel= newTextLabel.replace(inUseTextSuffix, "");
        newTextLabel = newTextLabel.replace(currentTextSuffix, "");
        newTextLabel +=  inUseTextSuffix;


        gestureText.setText(newTextLabel);
        gestureText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
    }

    /**
     * Set gesture box as "Current" state by change text
     * @param oneGestureBox LinearLayout item contain gesture box.
     */
    private void changeButtonStyleToCurrent(View oneGestureBox){
        if (!(oneGestureBox instanceof ViewGroup))
        {return;}

        ViewGroup oneGestureBoxViewGroup = (ViewGroup) oneGestureBox;
        TextView gestureText = oneGestureBoxViewGroup.findViewWithTag("text_view_gesture_name");
        oneGestureBoxViewGroup.setClickable(true);
        oneGestureBoxViewGroup.setAlpha(1.f);

        if (gestureText == null)
        {return;}

        String newTextLabel = (String) gestureText.getText();
        newTextLabel = newTextLabel.replace(inUseTextSuffix, "");
        newTextLabel = newTextLabel.replace(currentTextSuffix, "");
        newTextLabel +=  currentTextSuffix;
        gestureText.setText(newTextLabel);
        gestureText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
    }


    private void resetButtonStyle(View oneGestureBox){
        if (!(oneGestureBox instanceof ViewGroup))
        {return;}

        ViewGroup oneGestureBoxViewGroup = (ViewGroup) oneGestureBox;
        TextView gestureText = oneGestureBoxViewGroup.findViewWithTag("text_view_gesture_name");
        oneGestureBoxViewGroup.setClickable(true);
        oneGestureBoxViewGroup.setAlpha(1.f);

        if (gestureText == null)
        {return;}

        String newTextLabel = (String) gestureText.getText();
        newTextLabel = newTextLabel.replace(inUseTextSuffix, "");
        newTextLabel = newTextLabel.replace(currentTextSuffix, "");


        gestureText.setText(newTextLabel);
        gestureText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
    }



    /**
     * Check every face gesture button
     * and disable the already used gesture.
     * @param pageEventType What event action setting page we are in.
     * This is the action show at the page title
     */
    private void checkGestureButtonInUse(BlendshapeEventTriggerConfig.EventType pageEventType){


        SharedPreferences preferences = getSharedPreferences("GameFaceLocalConfig", Context.MODE_PRIVATE);

        // Big layout contain face gesture buttons.
        ConstraintLayout gestureLayout = findViewById(R.id.gestureLayout);

        BlendshapeEventTriggerConfig.migrateBlendshapeUiIndicesIfNeeded(preferences);
        int gestureCount = BlendshapeEventTriggerConfig.BLENDSHAPE_FROM_ORDER_IN_UI.size();

        // For each face gesture button (skip layout spacers past the blendshape list).
        for (int gestureButtonIndexInUi = 0; gestureButtonIndexInUi < gestureCount; gestureButtonIndexInUi++) {
            ViewGroup oneGestureBox = (ViewGroup) gestureLayout.getChildAt(gestureButtonIndexInUi);

            resetButtonStyle(oneGestureBox);

            // Check each possible action from local config.
            for (BlendshapeEventTriggerConfig.EventType checkingEventType : BlendshapeEventTriggerConfig.EventType.values())
            {
                // Load binding config.
                int boundGestureIndex = BlendshapeEventTriggerConfig.getBlendshapeIndexInUi(
                    preferences, checkingEventType.toString());
                BlendshapeEventTriggerConfig.Blendshape boundedBlendshape = BlendshapeEventTriggerConfig.BLENDSHAPE_FROM_ORDER_IN_UI
                    .get(boundGestureIndex);

                if (boundGestureIndex != gestureButtonIndexInUi)
                {
                    continue;
                }

                // Assign more text information on the button.
                if (pageEventType == checkingEventType)
                {
                    changeButtonStyleToCurrent(oneGestureBox);
                }
                else
                {
                    if (boundedBlendshape != BlendshapeEventTriggerConfig.Blendshape.NONE)
                    {
                        changeButtonStyleToInUse(oneGestureBox);
                    }
                }
                break;
                }
        }

        // None button case need special handling.
        ViewGroup noneGestureBox = (ViewGroup) gestureLayout.getChildAt(BlendshapeEventTriggerConfig.noneUiIndex());
        int boundGestureIndex = BlendshapeEventTriggerConfig.getBlendshapeIndexInUi(
            preferences, pageEventType.toString());
        if (boundGestureIndex == BlendshapeEventTriggerConfig.noneUiIndex())
        {
            changeButtonStyleToCurrent(noneGestureBox);
        }

    }

    /**
     * Make back button work as back action in device's navigation.
     * @param item The menu item that was selected.
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
      if (item.getItemId() == android.R.id.home) {
        finish();
        return true;
      }
      return super.onOptionsItemSelected(item);

    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_choose_gesture);
        getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
        currentTextSuffix = getString(R.string.gesture_current_suffix);
        inUseTextSuffix = getString(R.string.gesture_in_use_suffix);

        SharedPreferences preferences = getSharedPreferences("GameFaceLocalConfig", Context.MODE_PRIVATE);

        pageEventType = (BlendshapeEventTriggerConfig.EventType) getIntent().getSerializableExtra("eventType");

        if (pageEventType == null)
        {
            Log.e(TAG, "Start intent with invalid extra EventType.");
            finish();
            return;
        }
        Log.i(TAG, "onCreate: " + pageEventType);

        String pageDescription = BlendshapeEventTriggerConfig.getActionDescription(this, pageEventType);
        ((TextView)findViewById(R.id.actionDescriptionText)).setText(pageDescription);


        // Setting actionbar
        String actionBarText = BlendshapeEventTriggerConfig.getBeautifyEventTypeName(this, pageEventType);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(actionBarText);



        preSelectButtonIndex = BlendshapeEventTriggerConfig.getBlendshapeIndexInUi(
                preferences, pageEventType.toString());


        findViewById(R.id.backBtn).setOnClickListener(v -> {
            // Parent CursorBinding is already on the stack; pop only.
            finish();
        });


        findViewById(R.id.nextBtn).setOnClickListener(v -> {
            if (selectedBlendshape == BlendshapeEventTriggerConfig.Blendshape.NONE){
                // Write config to sharedpref.
                BlendshapeEventTriggerConfig.writeBindingConfig(getBaseContext(),
                    BlendshapeEventTriggerConfig.Blendshape.NONE,
                    pageEventType,
                    0);
                try {
                    CharSequence text = getString(R.string.setting_completed);
                    int duration = Toast.LENGTH_LONG;
                    Toast toast = Toast.makeText(getBaseContext(), text, duration);
                    toast.show();
                } catch (Exception e) {
                    Log.i(TAG, e.toString());
                }

                // Pop back to existing CursorBinding; do not CLEAR_TASK.
                finish();
            } else {
                Intent intentGoGestureSize = new Intent(getBaseContext(), GestureSizeActivity.class);
                intentGoGestureSize.putExtra("eventType", pageEventType);
                intentGoGestureSize.putExtra("selectedGesture", selectedBlendshape);
                // Keep ChooseGesture on the stack so back returns here.
                startActivity(intentGoGestureSize);
            }
        });
        chooseGestureLayout = findViewById(R.id.gestureLayout);
        setupUi();
    }


    private void setupUi(){
        int gestureCount = BlendshapeEventTriggerConfig.BLENDSHAPE_FROM_ORDER_IN_UI.size();
        for (int i = 0; i < gestureCount; i++) {
            final int position = i;
            View childView = chooseGestureLayout.getChildAt(i);

            childView.setBackgroundResource(R.drawable.gesture_button);

            if(preSelectButtonIndex == i) {
                selectedBlendshape = BlendshapeEventTriggerConfig.BLENDSHAPE_FROM_ORDER_IN_UI.get(preSelectButtonIndex);
                childView.setBackgroundResource(R.drawable.gesture_button_selected);
                unFocus = (LinearLayout) childView;
            }

            childView.setOnClickListener(v -> {
                childView.setBackgroundResource(R.drawable.gesture_button_selected);
                if(unFocus != null && (unFocus != childView)){
                    unFocus.setBackgroundResource(R.drawable.gesture_button);
                }
                selectedBlendshape = BlendshapeEventTriggerConfig.BLENDSHAPE_FROM_ORDER_IN_UI.get(position);
                if(selectedBlendshape == BlendshapeEventTriggerConfig.Blendshape.NONE){
                    Button nextBtn = findViewById(R.id.nextBtn);
                    nextBtn.setText(getString(R.string.btn_done));
                } else {
                    Button nextBtn = findViewById(R.id.nextBtn);
                    nextBtn.setText(getString(R.string.btn_next));
                }
                unFocus = (LinearLayout) childView;

            });
        }
        checkGestureButtonInUse(pageEventType);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupUi();
    }
}