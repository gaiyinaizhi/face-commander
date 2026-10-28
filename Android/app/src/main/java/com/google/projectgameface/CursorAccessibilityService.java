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

import static java.lang.Math.max;
import static java.lang.Math.round;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The cursor service of GameFace app. */
@SuppressLint("UnprotectedReceiver") // All of the broadcasts can only be sent by system.
public class CursorAccessibilityService extends AccessibilityService implements LifecycleOwner {
    private static final String TAG = "CursorAccessibilityService";

    /** Limit UI update rate to 60 fps */
    public static final int UI_UPDATE = 16;

    /** Limit the FaceLandmark detect rate. */
    private static final int MIN_PROCESS = 30;




    private static final int IMAGE_ANALYZER_WIDTH = 300;
    private static final int IMAGE_ANALYZER_HEIGHT = 400;
    ServiceUiManager serviceUiManager;
    public CursorController cursorController;

    /** Monotonic time of last successfully dispatched face action. */
    private long lastActionDispatchMs = 0L;
    private FaceLandmarkerHelper facelandmarkerHelper;
    public WindowManager windowManager;
    private Handler tickFunctionHandler;
    public Point screenSize;

    private ProcessCameraProvider cameraProvider;

    /** Blocking ML operations are performed using this executor */
    private ExecutorService backgroundExecutor;

    private LifecycleRegistry lifecycleRegistry;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private long lastSendMessage = 0;
    private BroadcastReceiver changeServiceStateReceiver;
    private BroadcastReceiver requestServiceStateReceiver;
    private BroadcastReceiver loadSharedConfigBasicReceiver;
    private BroadcastReceiver loadSharedConfigGestureReceiver;
    private BroadcastReceiver enableScorePreviewReceiver;
    private BroadcastReceiver applyReadingPagerUiReceiver;

    /** This is state of cursor. */
    public enum ServiceState {
        ENABLE,
        DISABLE,
        /** User cannot move cursor but can still perform event from face gesture. */
        PAUSE,
        /**
         * For user to see themself in config page. Remove buttons and make camera feed static.
         */
        GLOBAL_STICK
    }

    private ServiceState serviceState = ServiceState.DISABLE;

    /** The setting app may request the float blendshape score. */
    private String requestedScoreBlendshapeName = "";

    /** Should we send blendshape score to front-end or not. */
    private boolean shouldSendScore = false;

    /**
     * Independent detector for DOUBLE_BLINK sensitivity preview so the green bar
     * reflects composite progress, not a single blink raw score.
     */
    private final DoubleBlinkGesture scorePreviewDoubleBlink = new DoubleBlinkGesture();

    /** Threshold (0–1) used by the preview double-blink detector; default 0.5. */
    private float scorePreviewThreshold = 0.5f;

    @SuppressLint({"UnspecifiedRegisterReceiverFlag", "ObsoleteSdkInt"})
    private void defineAndRegisterBroadcastMessageReceivers() {

        // Initialize the broadcast receiver
        loadSharedConfigBasicReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String configName = intent.getStringExtra("configName");
                    cursorController.cursorMovementConfig.updateOneConfigFromSharedPreference(configName);
                }
            };

        loadSharedConfigGestureReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String configName = intent.getStringExtra("configName");
                    cursorController.blendshapeEventTriggerConfig.updateOneConfigFromSharedPreference(
                        configName);
                }
            };

        changeServiceStateReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    int receivedEnumValue = intent.getIntExtra("state", -1);
                    Log.i(TAG, "changeServiceStateReceiver: " + ServiceState.values()[receivedEnumValue]);

                    // Target state to be changing.
                    switch (ServiceState.values()[receivedEnumValue]) {
                        case ENABLE:
                            enableService();
                            break;
                        case DISABLE:
                            disableService();
                            break;
                        case PAUSE:
                            togglePause();
                            break;
                        case GLOBAL_STICK:
                            enterGlobalStickState();
                            break;
                    }
                }
            };

        requestServiceStateReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String state = intent.getStringExtra("state");
                    sendBroadcastServiceState(state);
                }
            };

        enableScorePreviewReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    boolean enable = intent.getBooleanExtra("enable", false);
                    shouldSendScore = enable;
                    requestedScoreBlendshapeName = intent.getStringExtra("blendshapesName");
                    if (intent.hasExtra("threshold")) {
                        scorePreviewThreshold = intent.getFloatExtra("threshold", 0.5f);
                    }
                    // Reset composite preview state on enter/exit (and on threshold refresh).
                    scorePreviewDoubleBlink.reset();
                }
            };

        applyReadingPagerUiReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    applyReadingPagerUi();
                }
            };

        if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
            registerReceiver(
                changeServiceStateReceiver, new IntentFilter("CHANGE_SERVICE_STATE"), RECEIVER_EXPORTED);
            registerReceiver(
                requestServiceStateReceiver,
                new IntentFilter("REQUEST_SERVICE_STATE"),
                RECEIVER_EXPORTED);
            registerReceiver(
                loadSharedConfigBasicReceiver,
                new IntentFilter("LOAD_SHARED_CONFIG_BASIC"),
                RECEIVER_EXPORTED);
            registerReceiver(
                loadSharedConfigGestureReceiver,
                new IntentFilter("LOAD_SHARED_CONFIG_GESTURE"),
                RECEIVER_EXPORTED);
            registerReceiver(
                enableScorePreviewReceiver, new IntentFilter("ENABLE_SCORE_PREVIEW"), RECEIVER_EXPORTED);
            registerReceiver(
                applyReadingPagerUiReceiver,
                new IntentFilter(ReadingPagerPreferences.ACTION_APPLY_UI),
                RECEIVER_EXPORTED);
            registerReceiver(
                serviceUiManager.flyInWindowReceiver,
                new IntentFilter("FLY_IN_FLOAT_WINDOW"),
                RECEIVER_EXPORTED);
            registerReceiver(
                serviceUiManager.flyOutWindowReceiver,
                new IntentFilter("FLY_OUT_FLOAT_WINDOW"),
                RECEIVER_EXPORTED);
        } else {
            registerReceiver(changeServiceStateReceiver, new IntentFilter("CHANGE_SERVICE_STATE"));
            registerReceiver(requestServiceStateReceiver, new IntentFilter("REQUEST_SERVICE_STATE"));
            registerReceiver(loadSharedConfigBasicReceiver, new IntentFilter("LOAD_SHARED_CONFIG_BASIC"));
            registerReceiver(
                loadSharedConfigGestureReceiver, new IntentFilter("LOAD_SHARED_CONFIG_GESTURE"));
            registerReceiver(enableScorePreviewReceiver, new IntentFilter("ENABLE_SCORE_PREVIEW"));
            registerReceiver(
                applyReadingPagerUiReceiver,
                new IntentFilter(ReadingPagerPreferences.ACTION_APPLY_UI));
            registerReceiver(
                serviceUiManager.flyInWindowReceiver, new IntentFilter("FLY_IN_FLOAT_WINDOW"));
            registerReceiver(
                serviceUiManager.flyOutWindowReceiver, new IntentFilter("FLY_OUT_FLOAT_WINDOW"));
        }
    }

    /** Get current service state. */
    public ServiceState getServiceState() {
        return serviceState;
    }

    /**
     * One-time service setup. This will run immediately after user toggle grant Accessibility
     *
     * <p>permission.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY);

        windowManager = ContextCompat.getSystemService(this, WindowManager.class);

        cursorController = new CursorController(this);
        serviceUiManager = new ServiceUiManager(this, windowManager);

        screenSize = new Point();
        windowManager.getDefaultDisplay().getRealSize(screenSize);

        lifecycleRegistry = new LifecycleRegistry(this::getLifecycle);
        lifecycleRegistry.setCurrentState(Lifecycle.State.CREATED);
        lifecycleRegistry.setCurrentState(Lifecycle.State.STARTED);

        defineAndRegisterBroadcastMessageReceivers();

        // Initialize our background executor
        backgroundExecutor = Executors.newSingleThreadExecutor();

        backgroundExecutor.execute(
            () -> {
                facelandmarkerHelper = new FaceLandmarkerHelper();
                facelandmarkerHelper.setFrontCameraOrientation(CameraHelper.checkFrontCameraOrientation(this));
                facelandmarkerHelper.setRotation(windowManager.getDefaultDisplay().getRotation());
                facelandmarkerHelper.start();
                facelandmarkerHelper.init(this);
            });

        setImageAnalyzer();

        // Initialize the Handler
        tickFunctionHandler = new Handler();
        tickFunctionHandler.postDelayed(tick, 0);
    }

    /** Set image property to match the MediaPipe model. - Using RGBA 8888. - Lowe the resolution. */
    private ImageAnalysis imageAnalyzer =
        new ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setResolutionSelector(
                new ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        new ResolutionStrategy(
                            new Size(IMAGE_ANALYZER_WIDTH, IMAGE_ANALYZER_HEIGHT),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build())
            .build();

    /**
     * Tick function of the service. This function runs every {@value UI_UPDATE}
     *
     * <p>Milliseconds. 1. Update cursor location on screen. 2. Dispatch event. 2. Change status icon.
     */
    private final Runnable tick =
        new Runnable() {
            @Override
            public void run() {
                if (facelandmarkerHelper == null) {
                    // Back-off.
                    tickFunctionHandler.postDelayed(this, CursorAccessibilityService.UI_UPDATE);
                }

                switch (serviceState) {
                    case GLOBAL_STICK:
                        if (shouldSendScore) {
                            sendBroadcastScore();
                        }
                    case ENABLE:
                        // Drag drag line if in drag mode.
                        if (cursorController.isDragging) {
                            serviceUiManager.updateDragLine(cursorController.getCursorPositionXY());
                        }

                        // Use for smoothing.
                        int gapFrames =
                            round(max(((float) facelandmarkerHelper.gapTimeMs / (float) UI_UPDATE), 1.0f));

                        cursorController.updateInternalCursorPosition(
                            facelandmarkerHelper.getHeadCoordXY(),
                            gapFrames,screenSize.x,screenSize.y
                        );


                        // Actually update the UI cursor image.
                        serviceUiManager.updateCursorImagePositionOnScreen(
                            cursorController.getCursorPositionXY()
                            );

                        dispatchEvent();

                        serviceUiManager.drawHeadCenter(
                            facelandmarkerHelper.getHeadCoordXY(),
                            facelandmarkerHelper.mpInputWidth,
                            facelandmarkerHelper.mpInputHeight);

                        serviceUiManager.updateDebugTextOverlay(
                            facelandmarkerHelper.preprocessTimeMs,
                            facelandmarkerHelper.mediapipeTimeMs,
                            serviceState == ServiceState.PAUSE);
                        break;

                    case PAUSE:
                        // In PAUSE state user cannot move cursor
                        // but still can perform some event from face gesture.
                        dispatchEvent();

                        serviceUiManager.drawHeadCenter(
                            facelandmarkerHelper.getHeadCoordXY(),
                            facelandmarkerHelper.mpInputWidth,
                            facelandmarkerHelper.mpInputHeight);

                        serviceUiManager.updateDebugTextOverlay(
                            facelandmarkerHelper.preprocessTimeMs,
                            facelandmarkerHelper.mediapipeTimeMs,
                            getServiceState() == ServiceState.PAUSE);
                        break;

                    default:
                        break;
                }

                serviceUiManager.updateStatusIcon(
                    serviceState == ServiceState.PAUSE, checkFaceVisibleInFrame());

                tickFunctionHandler.postDelayed(this, CursorAccessibilityService.UI_UPDATE);
            }
        };

    /** Assign function to image analyzer to send it to MediaPipe */
    private void setImageAnalyzer() {
        imageAnalyzer.setAnalyzer(
            backgroundExecutor,
            imageProxy -> {
                if ((SystemClock.uptimeMillis() - lastSendMessage) > MIN_PROCESS) {

                    // Create a new message and attach image.
                    Message msg = Message.obtain();
                    msg.obj = imageProxy;

                    if ((facelandmarkerHelper != null) && (facelandmarkerHelper.getHandler() != null)) {
                        // Send message to the thread to process.
                        facelandmarkerHelper.getHandler().sendMessage(msg);
                        lastSendMessage = SystemClock.uptimeMillis();
                    }

                } else {
                    // It will be closed by FaceLandmarkHelper.
                    imageProxy.close();
                }
            });
    }

    /** Send out blendshape score for visualize in setting page.*/
    private void sendBroadcastScore() {
        if (!shouldSendScore) {
            return;
        }

        // Get float score of the requested blendshape.
        try {
            BlendshapeEventTriggerConfig.Blendshape enumValue =
                BlendshapeEventTriggerConfig.Blendshape.valueOf(requestedScoreBlendshapeName);

            float[] blendshapes = facelandmarkerHelper.getBlendshapes();
            float score = BlendshapeEventTriggerConfig.scoreFor(enumValue, blendshapes);

            // DOUBLE_BLINK preview must show composite progress, not a single blink.
            if (enumValue == BlendshapeEventTriggerConfig.Blendshape.DOUBLE_BLINK) {
                score = previewDoubleBlinkScore(score);
            }

            Intent intent = new Intent(requestedScoreBlendshapeName);
            intent.putExtra("score", score);
            sendBroadcast(intent);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "No Blendshape named " + requestedScoreBlendshapeName);
        }
    }

    /**
     * Encode double-blink composite progress for the sensitivity bar.
     *
     * <p>Feeds {@code min(L,R)} into an independent {@link DoubleBlinkGesture}. A single blink
     * must not look fully triggered; the second legal blink reaches ~1.0.
     */
    private float previewDoubleBlinkScore(float rawBothEyesScore) {
        long nowMs = SystemClock.uptimeMillis();
        boolean fired =
            scorePreviewDoubleBlink.onScore(rawBothEyesScore, scorePreviewThreshold, nowMs);
        if (fired) {
            return 1.0f;
        }
        int pulses = scorePreviewDoubleBlink.getPulseCount();
        if (pulses > 0) {
            // pulseCount/required (~0.5 after first blink). Cap under the preview threshold so
            // the green "triggered" drawable does not engage on a single blink.
            float progress =
                (float) pulses / (float) DoubleBlinkGesture.REQUIRED_PULSES;
            float underThreshold = Math.max(0.f, scorePreviewThreshold - 0.01f);
            return Math.min(progress, underThreshold);
        }
        // Idle / below: show raw both-eyes score (typically low when eyes open).
        return Math.min(rawBothEyesScore, scorePreviewThreshold);
    }

    private void sendBroadcastServiceState(String state) {
        Intent intent;
        if (state.equals("main")) {
            intent = new Intent("SERVICE_STATE");
        } else {
            intent = new Intent("SERVICE_STATE_GESTURE");
        }
        intent.putExtra("state", serviceState.ordinal());
        sendBroadcast(intent);
    }

    /** Called from startService in MainActivity. After user click the "Start" button. */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        serviceUiManager.cameraBoxView.findViewById(R.id.popBtn).setBackground(null);

        return START_STICKY;
    }

    /** Toggle between Pause <-> ENABLE. */
    public void togglePause() {
        switch (serviceState) {
            case ENABLE:
                // Already enable, goto pause mode.
                serviceState = ServiceState.PAUSE;
                serviceUiManager.hideCursor();
                break;

            case PAUSE:
                // In pause mode, enable it.
                serviceState = ServiceState.ENABLE;
                applyReadingPagerUi();
                break;
            default:
        }
        serviceUiManager.setCameraBoxDraggable(true);
        // Immediately refresh minimized pause icon + Pause/Resume button label.
        boolean pausing = serviceState == ServiceState.PAUSE;
        serviceUiManager.updateStatusIcon(pausing, checkFaceVisibleInFrame());
        if (pausing && serviceUiManager.cameraBoxState == ServiceUiManager.CameraBoxState.MINIMIZE) {
            serviceUiManager.applyFloatIconState(ServiceUiManager.FloatIconState.PAUSE_ICON);
        }
    }

    /**
     * Enter {@link ServiceState#GLOBAL_STICK} state.
     * For binding gesture size page.
     * Remove buttons and make camera feed static.
     */
    public void enterGlobalStickState() {
        Log.i(TAG, "enterGlobalStickState");
        switch (serviceState) {
            case PAUSE:
                togglePause();
                break;
            case DISABLE:
                enableService();
                break;
            default:
                break;
        }
        serviceState = ServiceState.GLOBAL_STICK;
        serviceUiManager.setCameraBoxDraggable(false);
    }

    /** Enable GameFace service. */

    /**
     * Apply reading-pager overlay defaults from SharedPreferences: hide cursor and camera
     * preview unless the user opted back into classic Gameface overlays.
     */
    public void applyReadingPagerUi() {
        if (serviceUiManager == null) {
            return;
        }
        if (serviceState == ServiceState.DISABLE) {
            return;
        }
        if (ReadingPagerPreferences.isShowCursor(this)) {
            serviceUiManager.showCursor();
        } else {
            serviceUiManager.hideCursor();
        }
        // Respect both show-preview opt-in and user Close (dismissed) flag.
        if (ReadingPagerPreferences.shouldShowFloatAssist(this)) {
            serviceUiManager.showCameraBox();
            serviceUiManager.maximizeCameraBox();
        } else {
            serviceUiManager.hideCameraBox();
        }
        serviceUiManager.updateStatusIcon(
            serviceState == ServiceState.PAUSE, checkFaceVisibleInFrame());
    }

    public void enableService() {
        Log.i(TAG, "enableService, current: "+serviceState);

        switch (serviceState) {
            case ENABLE:
                return;


            case DISABLE:
                //Start camera.
                cameraProviderFuture = ProcessCameraProvider.getInstance(this);
                cameraProviderFuture.addListener(
                        () -> {
                            try {
                                cameraProvider = cameraProviderFuture.get();
                                CameraHelper.bindPreview(
                                        cameraProvider, serviceUiManager.innerCameraImageView, imageAnalyzer, this);
                            } catch (ExecutionException | InterruptedException e) {
                                Log.e(TAG, "cameraProvider failed to get provider future: " + e.getMessage());
                            }
                        },
                        ContextCompat.getMainExecutor(this));

                facelandmarkerHelper.resumeThread();
                setImageAnalyzer();

            case PAUSE:
            case GLOBAL_STICK:

                break;
            default:
        }

        serviceUiManager.showAllWindows();
        serviceUiManager.fitCameraBoxToScreen();
        serviceUiManager.setCameraBoxDraggable(true);
        applyReadingPagerUi();


        serviceState = ServiceState.ENABLE;

    }

    /** Disable GameFace service. */
    public void disableService() {
        Log.i(TAG, "disableService");
        switch (serviceState) {
            case ENABLE:
            case GLOBAL_STICK:
            case PAUSE:
                serviceUiManager.hideAllWindows();
                serviceUiManager.setCameraBoxDraggable(true);

                // stop the service functions.
                facelandmarkerHelper.pauseThread();
                imageAnalyzer.clearAnalyzer();

                // Stop camera.
                cameraProviderFuture = ProcessCameraProvider.getInstance(this);
                cameraProviderFuture.addListener(
                    () -> {
                        try {
                            cameraProvider = cameraProviderFuture.get();
                            cameraProvider.unbindAll();
                        } catch (ExecutionException | InterruptedException e) {
                            Log.e(TAG, "cameraProvider failed to get provider future: " + e.getMessage());
                        }
                    },
                    ContextCompat.getMainExecutor(this));
                serviceState = ServiceState.DISABLE;

                break;
            default:
                break;
        }
    }

    /** Destroy GameFace service and unregister broadcasts. */
    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        disableService();
        disableSelf();
        // Unregister when the service is destroyed
        unregisterReceiver(changeServiceStateReceiver);
        unregisterReceiver(loadSharedConfigBasicReceiver);
        unregisterReceiver(loadSharedConfigGestureReceiver);
        unregisterReceiver(requestServiceStateReceiver);
        unregisterReceiver(enableScorePreviewReceiver);
        unregisterReceiver(applyReadingPagerUiReceiver);
        unregisterReceiver(serviceUiManager.flyInWindowReceiver);
        unregisterReceiver(serviceUiManager.flyOutWindowReceiver);

        super.onDestroy();
    }



    /** Function for perform {@link BlendshapeEventTriggerConfig.EventType} actions. */
    private void dispatchEvent() {
        // Check what event to dispatch.
        BlendshapeEventTriggerConfig.EventType event =
            cursorController.createCursorEvent(facelandmarkerHelper.getBlendshapes());


        switch (event) {
            case NONE:
                return;
            case DRAG_TOGGLE:
                break;
            default:
                // Cancel drag if user perform any other event.
                cursorController.prepareDragEnd(0, 0);
                serviceUiManager.fullScreenCanvas.clearDragLine();
                break;
        }



        switch (serviceState) {
            case GLOBAL_STICK:
            case ENABLE:
                // Global action cooldown (~800ms); pause always allowed.
                if (event != BlendshapeEventTriggerConfig.EventType.CURSOR_PAUSE) {
                    long nowMs = SystemClock.uptimeMillis();
                    if (!ActionCooldown.shouldDispatch(nowMs, lastActionDispatchMs)) {
                        return;
                    }
                    lastActionDispatchMs = nowMs;
                }
                // Check event type and dispatch it.
                DispatchEventHelper.checkAndDispatchEvent(
                    this,
                    cursorController,
                    serviceUiManager,
                    event);
                break;

            case PAUSE:
                // In PAUSE state user can only perform togglePause
                // with face gesture.
                if (event == BlendshapeEventTriggerConfig.EventType.CURSOR_PAUSE) {
                    togglePause();
                }
                if (cursorController.isDragging) {
                    serviceUiManager.fullScreenCanvas.clearDragLine();
                    cursorController.prepareDragEnd(0, 0);
                }
                break;
            default:
                break;
        }
    }

    private Boolean checkFaceVisibleInFrame() {
        if (facelandmarkerHelper == null) {
            return false;
        }
        return facelandmarkerHelper.isFaceVisible;
    }




    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.d(TAG, "onConfigurationChanged");
        super.onConfigurationChanged(newConfig);


        // Temporary hide UIs while screen is rotating.
        serviceUiManager.hideAllWindows();

        windowManager.getDefaultDisplay().getRealSize(screenSize);


        // Rotate mediapipe input.
        if (windowManager != null && facelandmarkerHelper != null) {
            int newRotation = windowManager.getDefaultDisplay().getRotation();
            facelandmarkerHelper.setRotation(newRotation);
        }

        // On-going drag event will be cancel when screen is rotate.
        cursorController.prepareDragEnd(0, 0);
        serviceUiManager.fullScreenCanvas.clearDragLine();

        switch (serviceState)  {
            case ENABLE:
            case GLOBAL_STICK:
                serviceUiManager.showAllWindows();
                applyReadingPagerUi();
                break;
            case PAUSE:
                if (ReadingPagerPreferences.shouldShowFloatAssist(this)) {
                    serviceUiManager.showCameraBox();
                    serviceUiManager.updateStatusIcon(true, checkFaceVisibleInFrame());
                }
                break;
            case DISABLE:
                break;

        }


    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return lifecycleRegistry;
    }
}
