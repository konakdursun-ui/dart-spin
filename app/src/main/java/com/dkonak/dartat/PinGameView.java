package com.dkonak.dartat;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PinGameView extends View {

    private static final String PREFS_NAME = "pin_orbit_progress";
    private static final String KEY_BEST_SCORE = "best_score";
    private static final String KEY_COINS = "coins";
    private static final String KEY_HIGHEST_UNLOCKED = "highest_unlocked_level";
    private static final String KEY_SELECTED_SKIN = "selected_skin";
    private static final String KEY_SELECTED_CORE = "selected_core";
    private static final String KEY_CUSTOM_TARGET_URI = "custom_target_uri";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_SKIN_UNLOCK_PREFIX = "skin_unlock_";
    private static final String KEY_CORE_UNLOCK_PREFIX = "core_unlock_";
    private static final long LEVEL_TRANSITION_DELAY_MS = 800L;
    private static final float SHOOT_SPEED_DP = 1080f;
    private static final float IMPACT_ANGLE_DEG = 90f;
    private static final int INITIAL_PIN_COUNT = 5;
    private static final int INITIAL_SHOTS_PER_LEVEL = 5;
    private static final int LEVELS_PER_SHOT_INCREMENT = 10;
    private static final boolean TEST_UNLOCK_ALL_LEVELS = true;

    private final Paint backgroundPaint = new Paint();
    private final Paint hubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint primaryButtonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentButtonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ghostButtonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint buttonTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint darkTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private final List<LevelConfig> levels = createLevels();
    private final List<Float> attachedPinAngles = new ArrayList<>();
    private final List<Float> inFlightShotProgresses = new ArrayList<>();
    private final List<CoinParticle> coinParticles = new ArrayList<>();
    private final List<SkinConfig> skins = createSkins();
    private final List<CoreStyle> coreStyles = createCoreStyles();
    private final Map<String, RectF> skinRects = new LinkedHashMap<>();
    private final Map<String, RectF> coreRects = new LinkedHashMap<>();
    private final RectF overlayRect = new RectF();
    private final RectF primaryButtonRect = new RectF();
    private final RectF secondaryButtonRect = new RectF();
    private final RectF tertiaryButtonRect = new RectF();
    private final RectF minusButtonRect = new RectF();
    private final RectF plusButtonRect = new RectF();
    private final RectF skinButtonRect = new RectF();
    private final RectF coreButtonRect = new RectF();
    private final RectF selectedSkinRowRect = new RectF();
    private final RectF selectedCoreRowRect = new RectF();
    private final RectF backButtonRect = new RectF();
    private final RectF settingsButtonRect = new RectF();
    private final RectF languageTrRect = new RectF();
    private final RectF languageEnRect = new RectF();
    private final RectF rewardCoinsButtonRect = new RectF();
    private final SharedPreferences preferences;
    private final Vibrator vibrator;
    private final ToneGenerator toneGenerator;
    private final RewardedContinueGateway rewardedContinueGateway;
    private final CustomTargetImageGateway customTargetImageGateway;

    private float shootSpeedPx;

    private int levelIndex;
    private int selectedStartLevel;
    private int shotsRemaining;
    private int score;
    private int bestScore;
    private int coins;
    private int highestUnlockedLevel;
    private int comboStreak;
    private int pendingCoinReward;
    private int runCoinsEarned;
    private float hubRotation;
    private float hubRotationSpeed;
    private float shakeOffsetX;
    private String selectedSkinId = "needle";
    private String selectedCoreId = "classic";
    private String customTargetUriString;
    private String currentLanguage = "tr";
    private String failOverlayMessage = "Istersen reklam izleyip kaldigin yerden devam et.";
    private boolean rewardedContinueUsed;
    private long lastFrameTime;
    private long stateChangeAt;
    private long shakeUntil;
    private ScreenMode screenMode = ScreenMode.MENU;
    private ScreenMode previousScreenMode = ScreenMode.MENU;
    private GameStatus status = GameStatus.PLAYING;
    private Bitmap customTargetBitmap;
    private Bitmap appLogoBitmap;
    private Bitmap gameBackgroundBitmap;
    private Bitmap preparedBackgroundBitmap;

    public PinGameView(Context context, RewardedContinueGateway rewardedContinueGateway) {
        this(context, rewardedContinueGateway, null);
    }

    public PinGameView(Context context, RewardedContinueGateway rewardedContinueGateway, CustomTargetImageGateway customTargetImageGateway) {
        super(context);
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 70);
        this.rewardedContinueGateway = rewardedContinueGateway;
        this.customTargetImageGateway = customTargetImageGateway;
        init();
    }

    private void init() {
        shootSpeedPx = dp(SHOOT_SPEED_DP);
        bestScore = preferences.getInt(KEY_BEST_SCORE, 0);
        coins = preferences.getInt(KEY_COINS, 0);
        highestUnlockedLevel = Math.max(1, preferences.getInt(KEY_HIGHEST_UNLOCKED, 1));
        selectedStartLevel = highestUnlockedLevel;
        selectedSkinId = preferences.getString(KEY_SELECTED_SKIN, "needle");
        selectedCoreId = preferences.getString(KEY_SELECTED_CORE, "classic");
        customTargetUriString = preferences.getString(KEY_CUSTOM_TARGET_URI, null);
        currentLanguage = preferences.getString(KEY_LANGUAGE, "tr");
        appLogoBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.dartat_app_logo);
        gameBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.dartat_game_background);
        loadCustomTargetBitmap();

        backgroundPaint.setColor(Color.rgb(237, 232, 222));

        hubPaint.setColor(Color.rgb(32, 45, 58));
        hubPaint.setStyle(Paint.Style.FILL);

        pinPaint.setColor(Color.rgb(23, 31, 40));
        pinPaint.setStyle(Paint.Style.FILL);

        linePaint.setColor(Color.argb(190, 48, 58, 71));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeWidth(dp(2.4f));

        titlePaint.setColor(Color.BLACK);
        titlePaint.setTextSize(sp(18f));
        titlePaint.setFakeBoldText(true);
        titlePaint.setTextAlign(Paint.Align.CENTER);

        headingPaint.setColor(Color.BLACK);
        headingPaint.setTextSize(sp(32f));
        headingPaint.setFakeBoldText(true);
        headingPaint.setTextAlign(Paint.Align.CENTER);

        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(sp(28f));
        labelPaint.setFakeBoldText(true);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        smallLabelPaint.setColor(Color.WHITE);
        smallLabelPaint.setTextSize(sp(14f));
        smallLabelPaint.setFakeBoldText(true);
        smallLabelPaint.setTextAlign(Paint.Align.CENTER);

        bodyPaint.setColor(Color.rgb(70, 70, 70));
        bodyPaint.setTextSize(sp(15f));
        bodyPaint.setTextAlign(Paint.Align.CENTER);

        darkTextPaint.setColor(Color.BLACK);
        darkTextPaint.setTextSize(sp(15f));
        darkTextPaint.setTextAlign(Paint.Align.CENTER);

        overlayPaint.setColor(Color.argb(138, 0, 0, 0));

        cardPaint.setColor(Color.WHITE);
        cardPaint.setShadowLayer(dp(12f), 0f, dp(5f), Color.argb(25, 0, 0, 0));

        chipPaint.setColor(Color.argb(18, 0, 0, 0));

        primaryButtonPaint.setColor(Color.rgb(34, 45, 58));
        accentButtonPaint.setColor(Color.rgb(196, 92, 67));
        ghostButtonPaint.setColor(Color.argb(46, 34, 45, 58));

        buttonTextPaint.setColor(Color.WHITE);
        buttonTextPaint.setTextSize(sp(16f));
        buttonTextPaint.setFakeBoldText(true);
        buttonTextPaint.setTextAlign(Paint.Align.CENTER);

        coinPaint.setColor(Color.rgb(219, 163, 41));
        coinPaint.setStyle(Paint.Style.FILL);

        ensureDefaultSkinOwnership();
        ensureDefaultCoreOwnership();
        if (!isSkinUnlocked(selectedSkinId)) {
            selectedSkinId = "needle";
        }
        if (!isCoreUnlocked(selectedCoreId)) {
            selectedCoreId = "classic";
        }
        if ("custom".equals(selectedCoreId) && customTargetBitmap == null) {
            selectedCoreId = "classic";
        }

        setLayerType(LAYER_TYPE_SOFTWARE, cardPaint);
        resetProgress();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lastFrameTime = SystemClock.elapsedRealtime();
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        toneGenerator.release();
        if (preparedBackgroundBitmap != null) {
            preparedBackgroundBitmap.recycle();
            preparedBackgroundBitmap = null;
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        prepareBackgroundBitmap(width, height);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN) {
            return true;
        }

        float x = event.getX() - shakeOffsetX;
        float y = event.getY();

        if (screenMode == ScreenMode.MENU) {
            if (primaryButtonRect.contains(x, y)) {
                startNewRun();
            } else if (selectedSkinRowRect.contains(x, y)) {
                openSkins(ScreenMode.MENU);
            } else if (selectedCoreRowRect.contains(x, y)) {
                openCores(ScreenMode.MENU);
            } else if (settingsButtonRect.contains(x, y)) {
                openSettings(ScreenMode.MENU);
            } else if (rewardCoinsButtonRect.contains(x, y)) {
                startRewardedCoins();
            } else if (minusButtonRect.contains(x, y)) {
                selectedStartLevel = Math.max(1, selectedStartLevel - 1);
            } else if (plusButtonRect.contains(x, y)) {
                selectedStartLevel = Math.min(getMaxSelectableLevel(), selectedStartLevel + 1);
            }
            invalidate();
            return true;
        }

        if (screenMode == ScreenMode.SKINS) {
            if (backButtonRect.contains(x, y)) {
                screenMode = previousScreenMode;
            } else {
                handleSkinTap(x, y);
            }
            invalidate();
            return true;
        }

        if (screenMode == ScreenMode.CORES) {
            if (backButtonRect.contains(x, y)) {
                screenMode = previousScreenMode;
            } else {
                handleCoreTap(x, y);
            }
            invalidate();
            return true;
        }

        if (screenMode == ScreenMode.SETTINGS) {
            if (backButtonRect.contains(x, y)) {
                screenMode = previousScreenMode;
            } else if (languageTrRect.contains(x, y)) {
                setLanguage("tr");
            } else if (languageEnRect.contains(x, y)) {
                setLanguage("en");
            }
            invalidate();
            return true;
        }

        if (screenMode == ScreenMode.RESULT) {
            if (primaryButtonRect.contains(x, y)) {
                startNewRun();
            } else if (secondaryButtonRect.contains(x, y)) {
                screenMode = ScreenMode.MENU;
                resetProgress();
            }
            invalidate();
            return true;
        }

        if (status == GameStatus.LEVEL_FAILED) {
            if (!rewardedContinueUsed && primaryButtonRect.contains(x, y)) {
                startRewardedContinue();
            } else if (secondaryButtonRect.contains(x, y)) {
                restartCurrentLevel();
            } else if (tertiaryButtonRect.contains(x, y)) {
                screenMode = ScreenMode.MENU;
                resetProgress();
            }
            invalidate();
            return true;
        }

        if (status == GameStatus.PAUSED) {
            if (primaryButtonRect.contains(x, y)) {
                status = GameStatus.PLAYING;
            } else if (secondaryButtonRect.contains(x, y)) {
                screenMode = ScreenMode.MENU;
                resetProgress();
            }
            invalidate();
            return true;
        }

        if (status == GameStatus.PLAYING && settingsButtonRect.contains(x, y)) {
            status = GameStatus.PAUSED;
            invalidate();
            return true;
        }

        if (status == GameStatus.PLAYING && shotsRemaining > 0) {
            launchShot();
            invalidate();
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.elapsedRealtime();
        float dt = Math.min((now - lastFrameTime) / 1000f, 0.032f);
        lastFrameTime = now;

        updateGame(dt, now);
        updateCoinParticles(dt);
        drawBackground(canvas);

        if (now < shakeUntil) {
            shakeOffsetX = (float) (Math.sin(now * 0.1f) * dp(7f));
        } else {
            shakeOffsetX = 0f;
        }

        canvas.save();
        canvas.translate(shakeOffsetX, 0f);
        if (screenMode == ScreenMode.MENU) {
            drawMenu(canvas);
        } else if (screenMode == ScreenMode.SKINS) {
            drawSkinSelection(canvas);
        } else if (screenMode == ScreenMode.CORES) {
            drawCoreSelection(canvas);
        } else if (screenMode == ScreenMode.SETTINGS) {
            drawSettings(canvas);
        } else if (screenMode == ScreenMode.RESULT) {
            drawResult(canvas);
        } else {
            drawGame(canvas);
        }
        canvas.restore();

        postInvalidateOnAnimation();
    }

    private void updateGame(float dt, long now) {
        if (screenMode != ScreenMode.GAME) {
            return;
        }

        if (status == GameStatus.PLAYING) {
            hubRotation = normalizeAngle(hubRotation + hubRotationSpeed * dt);
            if (!inFlightShotProgresses.isEmpty()) {
                float centerY = getHeight() * 0.38f;
                float hubRadius = gameScaleSize() * 0.095f;
                float pinLength = gameScaleSize() * 0.23f;
                float startY = getHeight() * 0.82f;
                float targetY = centerY + hubRadius + pinLength;
                for (int i = inFlightShotProgresses.size() - 1; i >= 0; i--) {
                    float progress = inFlightShotProgresses.get(i) + (shootSpeedPx * dt);
                    inFlightShotProgresses.set(i, progress);
                    float shotY = startY - progress;
                    if (shotY <= targetY) {
                        inFlightShotProgresses.remove(i);
                        resolveShot();
                        if (status != GameStatus.PLAYING) {
                            break;
                        }
                    }
                }
            }
        } else if (status == GameStatus.LEVEL_WON && stateChangeAt > 0L && now >= stateChangeAt) {
            if (levelIndex + 1 < levels.size()) {
                loadLevel(levelIndex + 1);
            } else {
                screenMode = ScreenMode.RESULT;
                status = GameStatus.ALL_CLEAR;
                stateChangeAt = 0L;
                persistProgress();
            }
        }
    }

    private void resolveShot() {
        for (float angle : attachedPinAngles) {
            if (isShotCollidingWithPin(angle)) {
                score = Math.max(0, score - 8);
                status = GameStatus.LEVEL_FAILED;
                failOverlayMessage = t("continue_offer");
                inFlightShotProgresses.clear();
                triggerFailureFeedback();
                persistProgress();
                invalidate();
                return;
            }
        }

        attachedPinAngles.add(normalizeAngle(IMPACT_ANGLE_DEG - hubRotation));
        comboStreak++;
        score += 10 + (comboStreak * 2);
        playTone(ToneGenerator.TONE_PROP_BEEP, 60);

        if (shotsRemaining <= 0 && inFlightShotProgresses.isEmpty()) {
            LevelConfig finishedLevel = levels.get(levelIndex);
            score += finishedLevel.clearBonus;
            pendingCoinReward = finishedLevel.coinReward;
            coins += pendingCoinReward;
            runCoinsEarned += pendingCoinReward;
            comboStreak = 0;
            rewardedContinueUsed = false;
            unlockNextLevel(levelIndex + 2);
            status = GameStatus.LEVEL_WON;
            stateChangeAt = SystemClock.elapsedRealtime() + LEVEL_TRANSITION_DELAY_MS;
            spawnCoinBurst(pendingCoinReward);
            vibrate(35);
            playTone(ToneGenerator.TONE_PROP_ACK, 120);
            persistProgress();
        }
        invalidate();
    }

    private void launchShot() {
        shotsRemaining--;
        inFlightShotProgresses.add(0f);
        playTone(ToneGenerator.TONE_PROP_BEEP, 35);
    }

    private boolean isShotCollidingWithPin(float localAngle) {
        float centerX = getWidth() / 2f;
        float centerY = getHeight() * 0.38f;
        float hubRadius = gameScaleSize() * 0.095f;
        float pinLength = gameScaleSize() * 0.23f;
        float pinBallRadius = gameScaleSize() * 0.03f;

        float shotEndX = centerX;
        float shotEndY = centerY + hubRadius + pinLength;

        double angleRad = Math.toRadians(normalizeAngle(localAngle + hubRotation));
        float pinEndX = centerX + (float) (Math.cos(angleRad) * (hubRadius + pinLength));
        float pinEndY = centerY + (float) (Math.sin(angleRad) * (hubRadius + pinLength));

        return distance(shotEndX, shotEndY, pinEndX, pinEndY) < pinBallRadius * 1.28f;
    }

    private void drawBackground(Canvas canvas) {
        if (preparedBackgroundBitmap != null) {
            canvas.drawBitmap(preparedBackgroundBitmap, 0f, 0f, null);
        } else {
            canvas.drawRect(0f, 0f, getWidth(), getHeight(), backgroundPaint);
        }

        if (screenMode != ScreenMode.GAME) {
            Paint topBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            topBarPaint.setColor(Color.argb(30, 34, 45, 58));
            float topBarWidth = panelWidth(0.86f, 540f);
            float topBarLeft = (getWidth() - topBarWidth) / 2f;
            canvas.drawRoundRect(new RectF(topBarLeft, getHeight() * 0.075f, topBarLeft + topBarWidth, getHeight() * 0.092f), dp(14f), dp(14f), topBarPaint);
        }
    }

    private void drawMenu(Canvas canvas) {
        float centerX = getWidth() / 2f;
        float width = panelWidth(0.86f, 520f);
        float left = (getWidth() - width) / 2f;
        float top = getHeight() * 0.1f;
        float bottom = getHeight() * 0.9f;
        overlayRect.set(left, top, left + width, bottom);
        canvas.drawRoundRect(overlayRect, dp(28f), dp(28f), cardPaint);

        if (appLogoBitmap != null) {
            drawAppLogo(canvas, centerX, top + dp(44f), dp(52f));
            Paint menuTitlePaint = new Paint(headingPaint);
            menuTitlePaint.setTextSize(sp(24f));
            canvas.drawText(t("app_title"), centerX, top + dp(94f), menuTitlePaint);
        } else {
            canvas.drawText(t("app_title"), centerX, top + dp(56f), headingPaint);
        }

        drawTopStat(canvas, left + dp(98f), top + dp(128f), t("score_short"), String.valueOf(bestScore));
        drawTopStat(canvas, left + width - dp(98f), top + dp(128f), t("coins_short"), String.valueOf(coins));

        Paint sectionPaint = new Paint(titlePaint);
        sectionPaint.setTextAlign(Paint.Align.LEFT);
        sectionPaint.setTextSize(sp(16f));
        float sectionX = left + dp(24f);
        canvas.drawText(t("start_level"), sectionX, top + dp(178f), sectionPaint);

        setButtonRect(minusButtonRect, left + dp(54f), top + dp(214f), dp(42f), dp(42f));
        setButtonRect(plusButtonRect, left + width - dp(54f), top + dp(214f), dp(42f), dp(42f));
        drawGhostButton(canvas, minusButtonRect, "-");
        drawGhostButton(canvas, plusButtonRect, "+");

        RectF levelRect = new RectF(left + dp(88f), top + dp(193f), left + width - dp(88f), top + dp(235f));
        canvas.drawRoundRect(levelRect, dp(20f), dp(20f), chipPaint);
        Paint levelPaint = new Paint(titlePaint);
        levelPaint.setColor(Color.BLACK);
        levelPaint.setTextSize(sp(16f));
        drawCenteredText(canvas, "LV " + selectedStartLevel, levelRect.centerX(), levelRect.centerY(), levelPaint);

        selectedSkinRowRect.set(left + dp(24f), top + dp(284f), left + width - dp(24f), top + dp(322f));
        selectedCoreRowRect.set(left + dp(24f), top + dp(334f), left + width - dp(24f), top + dp(372f));
        drawInfoRow(canvas, selectedSkinRowRect, t("selected_skin"), getSkinLabel(getSelectedSkin()));
        drawInfoRow(canvas, selectedCoreRowRect, t("selected_core"), getCoreLabel(getSelectedCore()));

        skinButtonRect.setEmpty();
        coreButtonRect.setEmpty();

        setButtonRect(rewardCoinsButtonRect, centerX, bottom - dp(182f), dp(220f), dp(42f));
        drawGhostButton(canvas, rewardCoinsButtonRect, t("watch_ad_coins"));

        setButtonRect(settingsButtonRect, centerX, bottom - dp(128f), dp(200f), dp(44f));
        drawGhostButton(canvas, settingsButtonRect, t("settings"));

        setButtonRect(primaryButtonRect, centerX, bottom - dp(60f), dp(220f), dp(54f));
        drawButton(canvas, primaryButtonRect, t("start_game"), primaryButtonPaint);
    }

    private void drawSkinSelection(Canvas canvas) {
        float centerX = getWidth() / 2f;
        float width = panelWidth(0.86f, 540f);
        float left = (getWidth() - width) / 2f;
        float top = getHeight() * 0.08f;
        float bottom = getHeight() * 0.88f;
        overlayRect.set(left, top, left + width, bottom);
        canvas.drawRoundRect(overlayRect, dp(28f), dp(28f), cardPaint);

        canvas.drawText(t("choose_skin"), centerX, top + dp(44f), headingPaint);
        canvas.drawText(t("skin_hint"), centerX, top + dp(78f), bodyPaint);

        setButtonRect(backButtonRect, centerX, bottom - dp(46f), dp(170f), dp(44f));
        drawGhostButton(canvas, backButtonRect, t("back"));

        drawSkinCards(canvas, left + dp(18f), top + dp(114f), width - dp(36f));
    }

    private void drawCoreSelection(Canvas canvas) {
        float centerX = getWidth() / 2f;
        float width = panelWidth(0.86f, 540f);
        float left = (getWidth() - width) / 2f;
        float top = getHeight() * 0.08f;
        float bottom = getHeight() * 0.88f;
        overlayRect.set(left, top, left + width, bottom);
        canvas.drawRoundRect(overlayRect, dp(28f), dp(28f), cardPaint);

        canvas.drawText(t("choose_core"), centerX, top + dp(44f), headingPaint);
        canvas.drawText(t("core_hint"), centerX, top + dp(78f), bodyPaint);

        setButtonRect(backButtonRect, centerX, bottom - dp(46f), dp(170f), dp(44f));
        drawGhostButton(canvas, backButtonRect, t("back"));

        drawCoreCards(canvas, left + dp(18f), top + dp(114f), width - dp(36f));
    }

    private void drawSettings(Canvas canvas) {
        float centerX = getWidth() / 2f;
        float width = panelWidth(0.82f, 500f);
        float left = (getWidth() - width) / 2f;
        float top = getHeight() * 0.18f;
        float bottom = getHeight() * 0.82f;
        overlayRect.set(left, top, left + width, bottom);
        canvas.drawRoundRect(overlayRect, dp(28f), dp(28f), cardPaint);

        canvas.drawText(t("settings"), centerX, top + dp(46f), headingPaint);
        canvas.drawText(t("language"), centerX, top + dp(92f), bodyPaint);

        setButtonRect(languageTrRect, centerX, top + dp(160f), dp(220f), dp(48f));
        setButtonRect(languageEnRect, centerX, top + dp(220f), dp(220f), dp(48f));
        drawButton(canvas, languageTrRect, "Turkce", "tr".equals(currentLanguage) ? accentButtonPaint : primaryButtonPaint);
        drawButton(canvas, languageEnRect, "English", "en".equals(currentLanguage) ? accentButtonPaint : primaryButtonPaint);

        setButtonRect(backButtonRect, centerX, bottom - dp(42f), dp(160f), dp(42f));
        drawGhostButton(canvas, backButtonRect, t("back"));
    }

    private void drawResult(Canvas canvas) {
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), overlayPaint);

        float centerX = getWidth() / 2f;
        float cardWidth = panelWidth(0.82f, 500f);
        float cardHeight = dp(290f);
        float left = (getWidth() - cardWidth) / 2f;
        float top = (getHeight() - cardHeight) / 2f;
        overlayRect.set(left, top, left + cardWidth, top + cardHeight);
        canvas.drawRoundRect(overlayRect, dp(28f), dp(28f), cardPaint);

        canvas.drawText(t("run_complete"), centerX, top + dp(54f), titlePaint);
        Paint resultScorePaint = new Paint(headingPaint);
        resultScorePaint.setTextSize(sp(40f));
        canvas.drawText(String.valueOf(score), centerX, top + dp(118f), resultScorePaint);
        canvas.drawText(t("total_score"), centerX, top + dp(150f), bodyPaint);
        canvas.drawText(t("coins_short") + ": +" + runCoinsEarned, centerX, top + dp(182f), bodyPaint);
        canvas.drawText(t("best_score") + ": " + bestScore, centerX, top + dp(208f), bodyPaint);

        setButtonRect(primaryButtonRect, centerX, top + dp(246f), dp(200f), dp(50f));
        drawButton(canvas, primaryButtonRect, t("play_again"), primaryButtonPaint);

        setButtonRect(secondaryButtonRect, centerX, top + dp(304f), dp(160f), dp(42f));
        drawGhostButton(canvas, secondaryButtonRect, t("menu"));
    }

    private void drawGame(Canvas canvas) {
        float centerX = getWidth() / 2f;
        float centerY = getHeight() * 0.38f;
        float hubRadius = gameScaleSize() * 0.095f;
        float pinLength = gameScaleSize() * 0.23f;
        float tipRadius = gameScaleSize() * 0.03f;
        LevelConfig level = levels.get(levelIndex);

        drawHud(canvas, level);

        for (int i = 0; i < attachedPinAngles.size(); i++) {
            drawProjectile(canvas, centerX, centerY, hubRadius, pinLength, tipRadius,
                    normalizeAngle(attachedPinAngles.get(i) + hubRotation));
        }

        drawTargetHub(canvas, centerX, centerY, hubRadius, level.centerLabel);
        drawQueue(canvas, centerX, tipRadius);
        drawCoinParticles(canvas);

        for (float progress : inFlightShotProgresses) {
            float shotY = getHeight() * 0.82f - progress;
            drawFlyingProjectile(canvas, centerX, shotY, pinLength, tipRadius);
        }

        if (status == GameStatus.LEVEL_WON) {
            drawStatusOverlay(canvas, t("level_complete"), "+" + pendingCoinReward + " " + t("coins_short") + "  +" + levels.get(levelIndex).clearBonus + " " + t("bonus"));
        } else if (status == GameStatus.LEVEL_FAILED) {
            drawFailOverlay(canvas);
        } else if (status == GameStatus.PAUSED) {
            drawPauseOverlay(canvas);
        }
    }

    private void drawHud(Canvas canvas, LevelConfig level) {
        Paint leftPaint = new Paint(titlePaint);
        leftPaint.setTextAlign(Paint.Align.LEFT);
        leftPaint.setTextSize(sp(15f));
        Paint rightPaint = new Paint(titlePaint);
        rightPaint.setTextAlign(Paint.Align.RIGHT);
        rightPaint.setTextSize(sp(15f));

        float top = dp(34f);
        canvas.drawText("LV " + (levelIndex + 1), dp(20f), top, leftPaint);
        canvas.drawText(t("score_short") + " " + score, getWidth() - dp(20f), top, rightPaint);

        Paint smallHudPaint = new Paint(bodyPaint);
        smallHudPaint.setTextAlign(Paint.Align.LEFT);
        smallHudPaint.setTextSize(sp(13f));
        canvas.drawText(t("coins_short") + " " + coins, dp(20f), top + dp(22f), smallHudPaint);

        Paint centerHudPaint = new Paint(bodyPaint);
        centerHudPaint.setTextSize(sp(13f));
        canvas.drawText(t("target") + " " + level.shotsToFire + " " + t("shots"), getWidth() / 2f, top + dp(64f), centerHudPaint);

        skinButtonRect.setEmpty();
        coreButtonRect.setEmpty();
        setButtonRect(settingsButtonRect, getWidth() / 2f, top + dp(20f), dp(110f), dp(26f));
        drawGhostButton(canvas, settingsButtonRect, t("pause"));
    }

    private void drawQueue(Canvas canvas, float centerX, float tipRadius) {
        float queueStartY = getHeight() * 0.78f;
        float spacing = tipRadius * 2.85f;
        int visibleShots = Math.min(shotsRemaining, 12);
        for (int i = 0; i < visibleShots; i++) {
            float y = queueStartY + i * spacing;
            drawFlyingProjectile(canvas, centerX, y, gameScaleSize() * 0.17f, tipRadius);
        }
        Paint countPaint = new Paint(titlePaint);
        countPaint.setTextAlign(Paint.Align.LEFT);
        countPaint.setTextSize(sp(18f));
        canvas.drawText("x" + shotsRemaining, centerX + dp(26f), queueStartY + dp(8f), countPaint);
    }

    private void drawFlyingProjectile(Canvas canvas, float x, float y, float length, float tipRadius) {
        SkinConfig skin = getSelectedSkin();
        float tailY = y - length;
        canvas.drawLine(x, tailY, x, y, linePaint);
        drawSkinHead(canvas, skin, x, y, 270f, tipRadius);
    }

    private void drawProjectile(Canvas canvas, float centerX, float centerY, float hubRadius, float pinLength, float tipRadius, float angleDeg) {
        double angleRad = Math.toRadians(angleDeg);
        float startX = centerX + (float) (Math.cos(angleRad) * hubRadius);
        float startY = centerY + (float) (Math.sin(angleRad) * hubRadius);
        float endX = centerX + (float) (Math.cos(angleRad) * (hubRadius + pinLength));
        float endY = centerY + (float) (Math.sin(angleRad) * (hubRadius + pinLength));
        canvas.drawLine(startX, startY, endX, endY, linePaint);
        drawSkinHead(canvas, getSelectedSkin(), endX, endY, angleDeg, tipRadius);
    }

    private void drawSkinHead(Canvas canvas, SkinConfig skin, float x, float y, float angleDeg, float radius) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(angleDeg + 90f);
        if ("arrow".equals(skin.id)) {
            Path arrow = new Path();
            arrow.moveTo(0f, -radius * 1.6f);
            arrow.lineTo(radius * 0.55f, radius * 0.4f);
            arrow.lineTo(radius * 0.22f, radius * 0.2f);
            arrow.lineTo(radius * 0.22f, radius * 1.55f);
            arrow.lineTo(-radius * 0.22f, radius * 1.55f);
            arrow.lineTo(-radius * 0.22f, radius * 0.2f);
            arrow.lineTo(-radius * 0.55f, radius * 0.4f);
            arrow.close();
            canvas.drawPath(arrow, pinPaint);
        } else if ("sword".equals(skin.id)) {
            Path sword = new Path();
            sword.moveTo(0f, -radius * 1.8f);
            sword.lineTo(radius * 0.34f, -radius * 1.05f);
            sword.lineTo(radius * 0.24f, radius * 1.2f);
            sword.lineTo(radius * 0.56f, radius * 1.42f);
            sword.lineTo(radius * 0.18f, radius * 1.62f);
            sword.lineTo(-radius * 0.18f, radius * 1.62f);
            sword.lineTo(-radius * 0.56f, radius * 1.42f);
            sword.lineTo(-radius * 0.24f, radius * 1.2f);
            sword.lineTo(-radius * 0.34f, -radius * 1.05f);
            sword.close();
            canvas.drawPath(sword, pinPaint);
        } else if ("dart".equals(skin.id)) {
            Path dart = new Path();
            dart.moveTo(0f, -radius * 1.55f);
            dart.lineTo(radius * 0.42f, -radius * 0.25f);
            dart.lineTo(radius * 0.2f, radius * 0.25f);
            dart.lineTo(radius * 0.6f, radius * 1.1f);
            dart.lineTo(0f, radius * 0.75f);
            dart.lineTo(-radius * 0.6f, radius * 1.1f);
            dart.lineTo(-radius * 0.2f, radius * 0.25f);
            dart.lineTo(-radius * 0.42f, -radius * 0.25f);
            dart.close();
            canvas.drawPath(dart, pinPaint);
        } else if ("spear".equals(skin.id)) {
            Path spear = new Path();
            spear.moveTo(0f, -radius * 1.85f);
            spear.lineTo(radius * 0.32f, -radius * 0.7f);
            spear.lineTo(radius * 0.16f, radius * 1.55f);
            spear.lineTo(-radius * 0.16f, radius * 1.55f);
            spear.lineTo(-radius * 0.32f, -radius * 0.7f);
            spear.close();
            canvas.drawPath(spear, pinPaint);
        } else {
            Path needle = new Path();
            needle.moveTo(0f, -radius * 1.7f);
            needle.lineTo(radius * 0.2f, -radius * 0.5f);
            needle.lineTo(radius * 0.14f, radius * 1.55f);
            needle.lineTo(-radius * 0.14f, radius * 1.55f);
            needle.lineTo(-radius * 0.2f, -radius * 0.5f);
            needle.close();
            canvas.drawPath(needle, pinPaint);
        }
        canvas.restore();
    }

    private void drawTargetHub(Canvas canvas, float centerX, float centerY, float hubRadius, int levelNumber) {
        CoreStyle coreStyle = getSelectedCore();
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(dp(4f));
        ring.setColor(Color.argb(95, 28, 36, 44));
        canvas.drawCircle(centerX, centerY, hubRadius * 1.02f, ring);

        if ("custom".equals(coreStyle.id) && customTargetBitmap != null) {
            drawCustomImageCore(canvas, centerX, centerY, hubRadius);
        } else if ("soccer".equals(coreStyle.id)) {
            drawSoccerCore(canvas, centerX, centerY, hubRadius);
        } else if ("basketball".equals(coreStyle.id)) {
            drawBasketballCore(canvas, centerX, centerY, hubRadius);
        } else if ("tennis".equals(coreStyle.id)) {
            drawTennisCore(canvas, centerX, centerY, hubRadius);
        } else if ("baseball".equals(coreStyle.id)) {
            drawBaseballCore(canvas, centerX, centerY, hubRadius);
        } else {
            drawClassicCore(canvas, centerX, centerY, hubRadius);
        }

        Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgePaint.setColor(Color.rgb(24, 31, 39));
        RectF badge = new RectF(centerX - hubRadius * 0.8f, centerY - hubRadius * 1.6f, centerX + hubRadius * 0.8f, centerY - hubRadius);
        canvas.drawRoundRect(badge, dp(18f), dp(18f), badgePaint);

        Paint badgeText = new Paint(titlePaint);
        badgeText.setTextSize(sp(12f));
        badgeText.setColor(Color.WHITE);
        drawCenteredText(canvas, t("level_badge"), badge.centerX(), badge.centerY(), badgeText);

        Paint levelText = new Paint(labelPaint);
        levelText.setTextSize(sp(22f));
        levelText.setColor("tennis".equals(coreStyle.id) || "baseball".equals(coreStyle.id) ? Color.BLACK : Color.WHITE);
        if ("custom".equals(coreStyle.id) && customTargetBitmap != null) {
            levelText.setShadowLayer(dp(3f), 0f, dp(1f), Color.argb(180, 0, 0, 0));
        }
        drawCenteredText(canvas, String.valueOf(levelNumber), centerX, centerY, levelText);
        levelText.clearShadowLayer();
    }

    private void drawInfoRow(Canvas canvas, RectF rect, String label, String value) {
        canvas.drawRoundRect(rect, dp(18f), dp(18f), chipPaint);
        Paint labelPaint = new Paint(bodyPaint);
        labelPaint.setTextAlign(Paint.Align.LEFT);
        labelPaint.setTextSize(sp(12f));
        canvas.drawText(label, rect.left + dp(14f), rect.centerY() + dp(4f), labelPaint);
        Paint valuePaint = new Paint(titlePaint);
        valuePaint.setTextAlign(Paint.Align.RIGHT);
        valuePaint.setTextSize(sp(14f));
        canvas.drawText(value, rect.right - dp(14f), rect.centerY() + dp(4f), valuePaint);
    }

    private void drawSectionLabel(Canvas canvas, float left, float top, String text) {
        Paint paint = new Paint(titlePaint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(sp(15f));
        canvas.drawText(text, left, top, paint);
    }

    private void drawClassicCore(Canvas canvas, float centerX, float centerY, float hubRadius) {
        Paint outer = new Paint(Paint.ANTI_ALIAS_FLAG);
        outer.setColor(Color.rgb(206, 109, 77));
        Paint middle = new Paint(Paint.ANTI_ALIAS_FLAG);
        middle.setColor(Color.rgb(248, 242, 230));
        Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
        core.setColor(Color.rgb(51, 69, 88));
        canvas.drawCircle(centerX, centerY, hubRadius * 1.06f, outer);
        canvas.drawCircle(centerX, centerY, hubRadius * 0.78f, middle);
        canvas.drawCircle(centerX, centerY, hubRadius * 0.44f, core);
    }

    private void drawSoccerCore(Canvas canvas, float centerX, float centerY, float hubRadius) {
        Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
        white.setColor(Color.WHITE);
        Paint black = new Paint(Paint.ANTI_ALIAS_FLAG);
        black.setColor(Color.BLACK);
        canvas.drawCircle(centerX, centerY, hubRadius, white);
        canvas.drawCircle(centerX, centerY, hubRadius * 0.26f, black);
        for (int i = 0; i < 5; i++) {
            double angle = Math.toRadians((360d / 5d) * i - 90d);
            float px = centerX + (float) Math.cos(angle) * hubRadius * 0.58f;
            float py = centerY + (float) Math.sin(angle) * hubRadius * 0.58f;
            canvas.drawCircle(px, py, hubRadius * 0.15f, black);
        }
    }

    private void drawBasketballCore(Canvas canvas, float centerX, float centerY, float hubRadius) {
        Paint orange = new Paint(Paint.ANTI_ALIAS_FLAG);
        orange.setColor(Color.rgb(219, 122, 44));
        Paint seam = new Paint(Paint.ANTI_ALIAS_FLAG);
        seam.setColor(Color.BLACK);
        seam.setStyle(Paint.Style.STROKE);
        seam.setStrokeWidth(dp(2.3f));
        canvas.drawCircle(centerX, centerY, hubRadius, orange);
        canvas.drawLine(centerX - hubRadius, centerY, centerX + hubRadius, centerY, seam);
        canvas.drawLine(centerX, centerY - hubRadius, centerX, centerY + hubRadius, seam);
        RectF oval = new RectF(centerX - hubRadius * 0.82f, centerY - hubRadius, centerX + hubRadius * 0.82f, centerY + hubRadius);
        canvas.drawArc(oval, -70f, 140f, false, seam);
        canvas.drawArc(oval, 110f, 140f, false, seam);
    }

    private void drawTennisCore(Canvas canvas, float centerX, float centerY, float hubRadius) {
        Paint green = new Paint(Paint.ANTI_ALIAS_FLAG);
        green.setColor(Color.rgb(186, 212, 68));
        Paint seam = new Paint(Paint.ANTI_ALIAS_FLAG);
        seam.setColor(Color.WHITE);
        seam.setStyle(Paint.Style.STROKE);
        seam.setStrokeWidth(dp(3f));
        canvas.drawCircle(centerX, centerY, hubRadius, green);
        RectF oval = new RectF(centerX - hubRadius * 0.95f, centerY - hubRadius * 0.72f, centerX + hubRadius * 0.25f, centerY + hubRadius * 0.72f);
        canvas.drawArc(oval, -90f, 180f, false, seam);
        RectF oval2 = new RectF(centerX - hubRadius * 0.25f, centerY - hubRadius * 0.72f, centerX + hubRadius * 0.95f, centerY + hubRadius * 0.72f);
        canvas.drawArc(oval2, 90f, 180f, false, seam);
    }

    private void drawBaseballCore(Canvas canvas, float centerX, float centerY, float hubRadius) {
        Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
        white.setColor(Color.rgb(248, 246, 240));
        Paint seam = new Paint(Paint.ANTI_ALIAS_FLAG);
        seam.setColor(Color.rgb(189, 58, 52));
        seam.setStyle(Paint.Style.STROKE);
        seam.setStrokeWidth(dp(2.1f));
        canvas.drawCircle(centerX, centerY, hubRadius, white);
        RectF oval = new RectF(centerX - hubRadius * 0.95f, centerY - hubRadius * 0.72f, centerX + hubRadius * 0.15f, centerY + hubRadius * 0.72f);
        canvas.drawArc(oval, -85f, 170f, false, seam);
        RectF oval2 = new RectF(centerX - hubRadius * 0.15f, centerY - hubRadius * 0.72f, centerX + hubRadius * 0.95f, centerY + hubRadius * 0.72f);
        canvas.drawArc(oval2, 95f, 170f, false, seam);
    }

    private void drawCustomImageCore(Canvas canvas, float centerX, float centerY, float hubRadius) {
        RectF target = new RectF(centerX - hubRadius, centerY - hubRadius, centerX + hubRadius, centerY + hubRadius);
        Path circle = new Path();
        circle.addCircle(centerX, centerY, hubRadius, Path.Direction.CW);

        canvas.save();
        canvas.clipPath(circle);
        canvas.drawBitmap(customTargetBitmap, null, target, null);
        canvas.restore();

        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(dp(2.2f));
        border.setColor(Color.argb(150, 255, 255, 255));
        canvas.drawCircle(centerX, centerY, hubRadius * 0.98f, border);
    }

    private void drawStatusOverlay(Canvas canvas, String title, String subtitle) {
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), overlayPaint);
        float width = panelWidth(0.76f, 480f);
        float height = dp(150f);
        float left = (getWidth() - width) / 2f;
        float top = (getHeight() - height) / 2f;
        overlayRect.set(left, top, left + width, top + height);
        Paint successPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        successPaint.setColor(Color.rgb(45, 157, 93));
        successPaint.setShadowLayer(dp(14f), 0f, dp(6f), Color.argb(38, 0, 0, 0));
        canvas.drawRoundRect(overlayRect, dp(26f), dp(26f), successPaint);

        Paint overlayTitlePaint = new Paint(titlePaint);
        overlayTitlePaint.setTextSize(sp(22f));
        overlayTitlePaint.setColor(Color.WHITE);
        canvas.drawText(title, overlayRect.centerX(), overlayRect.top + dp(52f), overlayTitlePaint);

        Paint overlaySubtitlePaint = new Paint(bodyPaint);
        overlaySubtitlePaint.setColor(Color.WHITE);
        canvas.drawText(subtitle, overlayRect.centerX(), overlayRect.top + dp(92f), overlaySubtitlePaint);
    }

    private void drawFailOverlay(Canvas canvas) {
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), overlayPaint);
        float width = panelWidth(0.84f, 520f);
        float height = dp(rewardedContinueUsed ? 300f : 292f);
        float left = (getWidth() - width) / 2f;
        float top = (getHeight() - height) / 2f;
        overlayRect.set(left, top, left + width, top + height);
        canvas.drawRoundRect(overlayRect, dp(26f), dp(26f), cardPaint);

        drawFailMessage(canvas, overlayRect.centerX(), overlayRect.top + dp(58f));

        float buttonY;
        if (!rewardedContinueUsed) {
            buttonY = overlayRect.top + dp(144f);
            setButtonRect(primaryButtonRect, overlayRect.centerX(), buttonY, dp(220f), dp(44f));
            drawButton(canvas, primaryButtonRect, t("watch_ad_continue"), accentButtonPaint);
            buttonY += dp(56f);
        } else {
            buttonY = overlayRect.top + dp(136f);
            Paint usedPaint = new Paint(bodyPaint);
            usedPaint.setColor(Color.rgb(170, 90, 50));
            usedPaint.setTextSize(sp(16f));
            canvas.drawText(t("continue_used"), overlayRect.centerX(), buttonY, usedPaint);
            buttonY += dp(52f);
        }

        setButtonRect(secondaryButtonRect, overlayRect.centerX(), buttonY, dp(190f), dp(40f));
        drawGhostButton(canvas, secondaryButtonRect, t("restart_level"));

        setButtonRect(tertiaryButtonRect, overlayRect.centerX(), buttonY + dp(56f), dp(130f), dp(36f));
        drawGhostButton(canvas, tertiaryButtonRect, t("menu"));
    }

    private void drawFailMessage(Canvas canvas, float centerX, float firstLineY) {
        Paint messagePaint = new Paint(bodyPaint);
        messagePaint.setTextSize(sp(17f));
        if (t("continue_offer").equals(failOverlayMessage)) {
            canvas.drawText(t("continue_offer_line_1"), centerX, firstLineY, messagePaint);
            canvas.drawText(t("continue_offer_line_2"), centerX, firstLineY + dp(26f), messagePaint);
        } else {
            canvas.drawText(failOverlayMessage, centerX, firstLineY + dp(12f), messagePaint);
        }
    }

    private void drawPauseOverlay(Canvas canvas) {
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), overlayPaint);
        float width = panelWidth(0.76f, 480f);
        float height = dp(190f);
        float left = (getWidth() - width) / 2f;
        float top = (getHeight() - height) / 2f;
        overlayRect.set(left, top, left + width, top + height);
        canvas.drawRoundRect(overlayRect, dp(26f), dp(26f), cardPaint);

        Paint pauseTitlePaint = new Paint(titlePaint);
        pauseTitlePaint.setTextSize(sp(22f));
        canvas.drawText(t("paused"), overlayRect.centerX(), overlayRect.top + dp(48f), pauseTitlePaint);

        setButtonRect(primaryButtonRect, overlayRect.centerX(), overlayRect.top + dp(100f), dp(190f), dp(46f));
        drawButton(canvas, primaryButtonRect, t("continue_game"), primaryButtonPaint);

        setButtonRect(secondaryButtonRect, overlayRect.centerX(), overlayRect.top + dp(152f), dp(150f), dp(40f));
        drawGhostButton(canvas, secondaryButtonRect, t("exit"));
    }

    private void drawTopStat(Canvas canvas, float cx, float cy, String label, String value) {
        RectF rect = new RectF(cx - dp(44f), cy - dp(24f), cx + dp(44f), cy + dp(24f));
        canvas.drawRoundRect(rect, dp(18f), dp(18f), chipPaint);
        Paint small = new Paint(bodyPaint);
        small.setTextSize(sp(12f));
        canvas.drawText(label, cx, cy - dp(2f), small);
        Paint valuePaint = new Paint(titlePaint);
        valuePaint.setTextSize(sp(16f));
        drawCenteredText(canvas, value, cx, cy + dp(10f), valuePaint);
    }

    private void drawSkinCards(Canvas canvas, float left, float top, float width) {
        skinRects.clear();
        float cardGap = dp(10f);
        float cardWidth = (width - cardGap) / 2f;
        float cardHeight = dp(82f);
        for (int i = 0; i < skins.size(); i++) {
            SkinConfig skin = skins.get(i);
            int row = i / 2;
            int col = i % 2;
            float cardLeft = left + col * (cardWidth + cardGap);
            float cardTop = top + row * (cardHeight + cardGap);
            RectF rect = new RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight);
            skinRects.put(skin.id, rect);

            Paint fill = new Paint(chipPaint);
            if (skin.id.equals(selectedSkinId)) {
                fill.setColor(Color.argb(48, 214, 95, 54));
            } else {
                fill.setColor(Color.argb(22, 0, 0, 0));
            }
            canvas.drawRoundRect(rect, dp(18f), dp(18f), fill);

            canvas.save();
            canvas.translate(rect.left + dp(34f), rect.centerY());
            drawSkinHead(canvas, skin, 0f, 0f, 0f, dp(10f));
            canvas.restore();

            Paint namePaint = new Paint(titlePaint);
            namePaint.setTextSize(sp(14f));
            namePaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(getSkinLabel(skin), rect.left + dp(58f), rect.top + dp(28f), namePaint);

            Paint infoPaint = new Paint(bodyPaint);
            infoPaint.setTextAlign(Paint.Align.LEFT);
            infoPaint.setTextSize(sp(12f));
            String state;
            if (isSkinUnlocked(skin.id)) {
                state = skin.id.equals(selectedSkinId) ? t("selected") : t("tap_select");
            } else {
                state = skin.cost + " " + t("coins_short");
            }
            canvas.drawText(state, rect.left + dp(58f), rect.top + dp(52f), infoPaint);
        }
    }

    private void handleSkinTap(float x, float y) {
        for (SkinConfig skin : skins) {
            RectF rect = skinRects.get(skin.id);
            if (rect != null && rect.contains(x, y)) {
                if (isSkinUnlocked(skin.id)) {
                    selectedSkinId = skin.id;
                    persistProgress();
                    playTone(ToneGenerator.TONE_PROP_ACK, 70);
                } else if (coins >= skin.cost) {
                    coins -= skin.cost;
                    unlockSkin(skin.id);
                    selectedSkinId = skin.id;
                    vibrate(28);
                    playTone(ToneGenerator.TONE_PROP_ACK, 100);
                    persistProgress();
                } else {
                    triggerFailureFeedback();
                }
                return;
            }
        }
    }

    private void drawCoreCards(Canvas canvas, float left, float top, float width) {
        coreRects.clear();
        float cardGap = dp(10f);
        float cardWidth = (width - cardGap) / 2f;
        float cardHeight = dp(82f);
        for (int i = 0; i < coreStyles.size(); i++) {
            CoreStyle core = coreStyles.get(i);
            int row = i / 2;
            int col = i % 2;
            float cardLeft = left + col * (cardWidth + cardGap);
            float cardTop = top + row * (cardHeight + cardGap);
            RectF rect = new RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight);
            coreRects.put(core.id, rect);

            Paint fill = new Paint(chipPaint);
            fill.setColor(core.id.equals(selectedCoreId) ? Color.argb(54, 196, 92, 67) : Color.argb(22, 0, 0, 0));
            canvas.drawRoundRect(rect, dp(18f), dp(18f), fill);

            float cx = rect.left + dp(34f);
            float cy = rect.centerY();
            float radius = dp(16f);
            if ("custom".equals(core.id) && customTargetBitmap != null) {
                drawCustomImageCore(canvas, cx, cy, radius);
            } else if ("soccer".equals(core.id)) {
                drawSoccerCore(canvas, cx, cy, radius);
            } else if ("basketball".equals(core.id)) {
                drawBasketballCore(canvas, cx, cy, radius);
            } else if ("tennis".equals(core.id)) {
                drawTennisCore(canvas, cx, cy, radius);
            } else if ("baseball".equals(core.id)) {
                drawBaseballCore(canvas, cx, cy, radius);
            } else {
                drawClassicCore(canvas, cx, cy, radius);
            }

            Paint namePaint = new Paint(titlePaint);
            namePaint.setTextSize(sp(14f));
            namePaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(getCoreLabel(core), rect.left + dp(60f), rect.top + dp(30f), namePaint);

            Paint infoPaint = new Paint(bodyPaint);
            infoPaint.setTextAlign(Paint.Align.LEFT);
            infoPaint.setTextSize(sp(12f));
            String state;
            if ("custom".equals(core.id)) {
                if (customTargetBitmap == null) {
                    state = t("choose_photo");
                } else {
                    state = core.id.equals(selectedCoreId) ? t("change_photo") : t("tap_select");
                }
            } else {
                state = isCoreUnlocked(core.id)
                        ? (core.id.equals(selectedCoreId) ? t("selected") : t("tap_select"))
                        : core.cost + " " + t("coins_short");
            }
            canvas.drawText(state, rect.left + dp(60f), rect.top + dp(56f), infoPaint);
        }
    }

    private void handleCoreTap(float x, float y) {
        for (CoreStyle core : coreStyles) {
            RectF rect = coreRects.get(core.id);
            if (rect != null && rect.contains(x, y)) {
                if ("custom".equals(core.id)) {
                    if (customTargetBitmap == null || core.id.equals(selectedCoreId)) {
                        requestCustomTargetImage();
                    } else {
                        selectedCoreId = core.id;
                        persistProgress();
                        playTone(ToneGenerator.TONE_PROP_ACK, 70);
                    }
                    return;
                }
                if (isCoreUnlocked(core.id)) {
                    selectedCoreId = core.id;
                    persistProgress();
                    playTone(ToneGenerator.TONE_PROP_ACK, 70);
                } else if (coins >= core.cost) {
                    coins -= core.cost;
                    unlockCore(core.id);
                    selectedCoreId = core.id;
                    persistProgress();
                    playTone(ToneGenerator.TONE_PROP_ACK, 110);
                    vibrate(24);
                } else {
                    triggerFailureFeedback();
                }
                return;
            }
        }
    }

    private void drawButton(Canvas canvas, RectF rect, String text, Paint paint) {
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, paint);
        drawCenteredText(canvas, text, rect.centerX(), rect.centerY(), buttonTextPaint);
    }

    private void drawGhostButton(Canvas canvas, RectF rect, String text) {
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, ghostButtonPaint);
        Paint textPaint = new Paint(darkTextPaint);
        textPaint.setTextSize(sp(14f));
        drawCenteredText(canvas, text, rect.centerX(), rect.centerY(), textPaint);
    }

    private void setButtonRect(RectF rect, float centerX, float centerY, float width, float height) {
        rect.set(centerX - width / 2f, centerY - height / 2f, centerX + width / 2f, centerY + height / 2f);
    }

    private void drawAppLogo(Canvas canvas, float centerX, float centerY, float size) {
        RectF logoRect = new RectF(centerX - size / 2f, centerY - size / 2f, centerX + size / 2f, centerY + size / 2f);
        canvas.save();
        Path clip = new Path();
        clip.addRoundRect(logoRect, size * 0.22f, size * 0.22f, Path.Direction.CW);
        canvas.clipPath(clip);
        canvas.drawBitmap(appLogoBitmap, null, logoRect, imagePaint);
        canvas.restore();
    }

    private void prepareBackgroundBitmap(int width, int height) {
        if (preparedBackgroundBitmap != null) {
            preparedBackgroundBitmap.recycle();
            preparedBackgroundBitmap = null;
        }
        if (width <= 0 || height <= 0 || gameBackgroundBitmap == null) {
            return;
        }

        preparedBackgroundBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        Canvas backgroundCanvas = new Canvas(preparedBackgroundBitmap);
        drawBitmapCover(backgroundCanvas, gameBackgroundBitmap, new RectF(0f, 0f, width, height), imagePaint);
    }

    private void drawBitmapCover(Canvas canvas, Bitmap bitmap, RectF destination, Paint paint) {
        float bitmapRatio = bitmap.getWidth() / (float) bitmap.getHeight();
        float destinationRatio = destination.width() / destination.height();
        Rect source;
        if (bitmapRatio > destinationRatio) {
            float sourceWidth = bitmap.getHeight() * destinationRatio;
            float sourceLeft = (bitmap.getWidth() - sourceWidth) / 2f;
            source = new Rect(Math.round(sourceLeft), 0, Math.round(sourceLeft + sourceWidth), bitmap.getHeight());
        } else {
            float sourceHeight = bitmap.getWidth() / destinationRatio;
            float sourceTop = (bitmap.getHeight() - sourceHeight) / 2f;
            source = new Rect(0, Math.round(sourceTop), bitmap.getWidth(), Math.round(sourceTop + sourceHeight));
        }
        canvas.drawBitmap(bitmap, source, destination, paint);
    }

    private void drawCenteredText(Canvas canvas, String text, float cx, float cy, Paint paint) {
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = cy - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(text, cx, baseline, paint);
    }

    private float panelWidth(float screenRatio, float maxDp) {
        return Math.min(getWidth() * screenRatio, dp(maxDp));
    }

    private float gameScaleSize() {
        return Math.min(Math.min(getWidth(), getHeight()), dp(720f));
    }

    private void startNewRun() {
        score = 0;
        comboStreak = 0;
        pendingCoinReward = 0;
        runCoinsEarned = 0;
        screenMode = ScreenMode.GAME;
        loadLevel(selectedStartLevel - 1);
        invalidate();
    }

    private void resetProgress() {
        score = 0;
        comboStreak = 0;
        pendingCoinReward = 0;
        runCoinsEarned = 0;
        levelIndex = 0;
        shotsRemaining = 0;
        hubRotation = 0f;
        hubRotationSpeed = 0f;
        inFlightShotProgresses.clear();
        rewardedContinueUsed = false;
        failOverlayMessage = t("continue_offer");
        stateChangeAt = 0L;
        status = GameStatus.PLAYING;
    }

    private void restartCurrentLevel() {
        comboStreak = 0;
        loadLevel(levelIndex);
        invalidate();
    }

    private void startRewardedContinue() {
        failOverlayMessage = t("ad_opening");
        playTone(ToneGenerator.TONE_SUP_RADIO_ACK, 180);
        rewardedContinueGateway.showRewardedContinue(new RewardedContinueCallback() {
            @Override
            public void onRewardEarned() {
                resumeAfterRewardedContinue();
            }

            @Override
            public void onAdUnavailable() {
                failOverlayMessage = t("ad_unavailable");
                triggerFailureFeedback();
                invalidate();
            }
        });
        invalidate();
    }

    private void resumeAfterRewardedContinue() {
        rewardedContinueUsed = true;
        shotsRemaining = Math.max(1, shotsRemaining);
        inFlightShotProgresses.clear();
        status = GameStatus.PLAYING;
        failOverlayMessage = t("continue_offer");
        vibrate(40);
        playTone(ToneGenerator.TONE_PROP_ACK, 120);
        invalidate();
    }

    private void loadLevel(int index) {
        levelIndex = Math.max(0, Math.min(index, levels.size() - 1));
        selectedStartLevel = Math.min(getMaxSelectableLevel(), levelIndex + 1);
        LevelConfig config = levels.get(levelIndex);
        attachedPinAngles.clear();
        attachedPinAngles.addAll(config.initialAngles);
        shotsRemaining = config.shotsToFire;
        hubRotation = 0f;
        hubRotationSpeed = config.rotationSpeedDegPerSec;
        inFlightShotProgresses.clear();
        rewardedContinueUsed = false;
        failOverlayMessage = t("continue_offer");
        status = GameStatus.PLAYING;
        stateChangeAt = 0L;
        lastFrameTime = SystemClock.elapsedRealtime();
    }

    private void unlockNextLevel(int unlockedLevel) {
        highestUnlockedLevel = Math.max(highestUnlockedLevel, Math.min(unlockedLevel, levels.size()));
        selectedStartLevel = Math.min(getMaxSelectableLevel(), Math.max(selectedStartLevel, highestUnlockedLevel));
    }

    private int getMaxSelectableLevel() {
        return TEST_UNLOCK_ALL_LEVELS ? levels.size() : highestUnlockedLevel;
    }

    private void ensureDefaultSkinOwnership() {
        if (!preferences.getBoolean(KEY_SKIN_UNLOCK_PREFIX + "needle", false)) {
            preferences.edit().putBoolean(KEY_SKIN_UNLOCK_PREFIX + "needle", true).apply();
        }
    }

    private void ensureDefaultCoreOwnership() {
        if (!preferences.getBoolean(KEY_CORE_UNLOCK_PREFIX + "classic", false)) {
            preferences.edit().putBoolean(KEY_CORE_UNLOCK_PREFIX + "classic", true).apply();
        }
    }

    private boolean isSkinUnlocked(String skinId) {
        return preferences.getBoolean(KEY_SKIN_UNLOCK_PREFIX + skinId, false);
    }

    private void unlockSkin(String skinId) {
        preferences.edit().putBoolean(KEY_SKIN_UNLOCK_PREFIX + skinId, true).apply();
    }

    private boolean isCoreUnlocked(String coreId) {
        return preferences.getBoolean(KEY_CORE_UNLOCK_PREFIX + coreId, false);
    }

    private void unlockCore(String coreId) {
        preferences.edit().putBoolean(KEY_CORE_UNLOCK_PREFIX + coreId, true).apply();
    }

    private SkinConfig getSelectedSkin() {
        for (SkinConfig skin : skins) {
            if (skin.id.equals(selectedSkinId)) {
                return skin;
            }
        }
        return skins.get(0);
    }

    private void persistProgress() {
        if (score > bestScore) {
            bestScore = score;
        }
        preferences.edit()
                .putInt(KEY_BEST_SCORE, bestScore)
                .putInt(KEY_COINS, coins)
                .putInt(KEY_HIGHEST_UNLOCKED, highestUnlockedLevel)
                .putString(KEY_SELECTED_SKIN, selectedSkinId)
                .putString(KEY_SELECTED_CORE, selectedCoreId)
                .putString(KEY_CUSTOM_TARGET_URI, customTargetUriString)
                .putString(KEY_LANGUAGE, currentLanguage)
                .apply();
    }

    public void setCustomTargetImage(Uri imageUri) {
        customTargetUriString = imageUri == null ? null : imageUri.toString();
        loadCustomTargetBitmap();
        selectedCoreId = "custom";
        unlockCore("custom");
        persistProgress();
        playTone(ToneGenerator.TONE_PROP_ACK, 100);
        invalidate();
    }

    private void requestCustomTargetImage() {
        if (customTargetImageGateway != null) {
            customTargetImageGateway.openCustomTargetImagePicker();
        } else {
            triggerFailureFeedback();
        }
    }

    private void loadCustomTargetBitmap() {
        if (customTargetBitmap != null) {
            customTargetBitmap.recycle();
            customTargetBitmap = null;
        }
        if (customTargetUriString == null || customTargetUriString.trim().isEmpty()) {
            return;
        }
        Uri imageUri = Uri.parse(customTargetUriString);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream boundsStream = getContext().getContentResolver().openInputStream(imageUri)) {
            BitmapFactory.decodeStream(boundsStream, null, bounds);
        } catch (Exception ignored) {
            customTargetUriString = null;
            return;
        }

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = calculateImageSampleSize(bounds.outWidth, bounds.outHeight, 1024);
        try (InputStream imageStream = getContext().getContentResolver().openInputStream(imageUri)) {
            customTargetBitmap = BitmapFactory.decodeStream(imageStream, null, decodeOptions);
        } catch (Exception ignored) {
            customTargetUriString = null;
        }
    }

    private int calculateImageSampleSize(int width, int height, int maxSize) {
        int sampleSize = 1;
        int largestSide = Math.max(width, height);
        while (largestSide / sampleSize > maxSize) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    private void triggerFailureFeedback() {
        shakeUntil = SystemClock.elapsedRealtime() + 260L;
        vibrate(140);
        playTone(ToneGenerator.TONE_SUP_ERROR, 180);
    }

    private void vibrate(long durationMs) {
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(durationMs);
        }
    }

    private void playTone(int tone, int durationMs) {
        toneGenerator.startTone(tone, durationMs);
    }

    private void spawnCoinBurst(int rewardAmount) {
        float centerX = getWidth() / 2f;
        float centerY = getHeight() * 0.38f;
        int particleCount = Math.max(6, Math.min(16, rewardAmount + 4));
        for (int i = 0; i < particleCount; i++) {
            float angle = (360f / particleCount) * i;
            float speed = dp(80f + ((i % 5) * 18f));
            coinParticles.add(new CoinParticle(centerX, centerY, angle, speed, dp(4f + (i % 3))));
        }
    }

    private void updateCoinParticles(float dt) {
        for (int i = coinParticles.size() - 1; i >= 0; i--) {
            CoinParticle particle = coinParticles.get(i);
            particle.life -= dt;
            if (particle.life <= 0f) {
                coinParticles.remove(i);
                continue;
            }
            particle.x += particle.vx * dt;
            particle.y += particle.vy * dt;
            particle.vy += dp(120f) * dt;
        }
    }

    private void drawCoinParticles(Canvas canvas) {
        for (CoinParticle particle : coinParticles) {
            int alpha = (int) (255 * Math.max(0f, particle.life / particle.maxLife));
            coinPaint.setAlpha(alpha);
            canvas.drawCircle(particle.x, particle.y, particle.radius, coinPaint);
        }
        coinPaint.setAlpha(255);
    }

    private List<LevelConfig> createLevels() {
        List<LevelConfig> configs = new ArrayList<>();
        for (int i = 1; i <= 500; i++) {
            float speed = 40f + ((i - 1) * 0.26f);
            int clearBonus = 12 + (i * 3);
            int coinReward = 3;
            configs.add(new LevelConfig(i, shotsForLevel(i), speed, clearBonus, coinReward, generateAngles(INITIAL_PIN_COUNT, i)));
        }
        return configs;
    }

    private int shotsForLevel(int levelNumber) {
        return INITIAL_SHOTS_PER_LEVEL + Math.max(0, (levelNumber - 1) / LEVELS_PER_SHOT_INCREMENT);
    }

    private float[] generateAngles(int count, int seed) {
        float[] angles = new float[count];
        float spacing = 360f / count;
        float offset = (seed * 19f) % 360f;
        for (int i = 0; i < count; i++) {
            float wobble = ((seed + (i * 13)) % 9 - 4) * 2.3f;
            angles[i] = normalizeAngle(offset + (i * spacing) + wobble);
        }
        return angles;
    }

    private List<SkinConfig> createSkins() {
        List<SkinConfig> list = new ArrayList<>();
        list.add(new SkinConfig("needle", "Igne", 0));
        list.add(new SkinConfig("arrow", "Ok", 35));
        list.add(new SkinConfig("sword", "Kilic", 80));
        list.add(new SkinConfig("dart", "Dart", 55));
        list.add(new SkinConfig("spear", "Mizrak", 95));
        return list;
    }

    private List<CoreStyle> createCoreStyles() {
        List<CoreStyle> list = new ArrayList<>();
        list.add(new CoreStyle("classic", 0));
        list.add(new CoreStyle("custom", 0));
        list.add(new CoreStyle("soccer", 45));
        list.add(new CoreStyle("basketball", 55));
        list.add(new CoreStyle("tennis", 70));
        list.add(new CoreStyle("baseball", 85));
        return list;
    }

    private void openSkins(ScreenMode source) {
        previousScreenMode = source;
        screenMode = ScreenMode.SKINS;
    }

    private void openCores(ScreenMode source) {
        previousScreenMode = source;
        screenMode = ScreenMode.CORES;
    }

    private void openSettings(ScreenMode source) {
        previousScreenMode = source;
        screenMode = ScreenMode.SETTINGS;
    }

    private void setLanguage(String language) {
        currentLanguage = language;
        failOverlayMessage = t("continue_offer");
        persistProgress();
    }

    private void startRewardedCoins() {
        rewardedContinueGateway.showRewardedContinue(new RewardedContinueCallback() {
            @Override
            public void onRewardEarned() {
                int rewardCoins = 5;
                coins += rewardCoins;
                runCoinsEarned += rewardCoins;
                spawnCoinBurst(rewardCoins);
                playTone(ToneGenerator.TONE_PROP_ACK, 120);
                persistProgress();
                invalidate();
            }

            @Override
            public void onAdUnavailable() {
                triggerFailureFeedback();
                invalidate();
            }
        });
    }

    private String getSkinLabel(SkinConfig skin) {
        switch (skin.id) {
            case "arrow":
                return t("skin_arrow");
            case "sword":
                return t("skin_sword");
            case "dart":
                return t("skin_dart");
            case "spear":
                return t("skin_spear");
            default:
                return t("skin_needle");
        }
    }

    private String getCoreLabel(CoreStyle core) {
        switch (core.id) {
            case "soccer":
                return t("core_soccer");
            case "basketball":
                return t("core_basketball");
            case "tennis":
                return t("core_tennis");
            case "baseball":
                return t("core_baseball");
            case "custom":
                return t("core_custom");
            default:
                return t("core_classic");
        }
    }

    private CoreStyle getSelectedCore() {
        for (CoreStyle coreStyle : coreStyles) {
            if (coreStyle.id.equals(selectedCoreId)) {
                return coreStyle;
            }
        }
        return coreStyles.get(0);
    }

    private String t(String key) {
        boolean en = "en".equals(currentLanguage);
        switch (key) {
            case "app_title":
                return "Dart At";
            case "menu_subtitle":
                return en ? "A polished orbit challenge with unlockable styles" : "Acilan stillere sahip daha rafine bir orbit deneyimi";
            case "score_short":
                return en ? "Score" : "Skor";
            case "coins_short":
                return en ? "Coins" : "Coin";
            case "open_short":
                return en ? "Open" : "Acik";
            case "start_level":
                return en ? "Start Level" : "Baslangic Seviyesi";
            case "selected_skin":
                return en ? "Selected Skin" : "Secili Skin";
            case "collection":
                return en ? "Collection" : "Koleksiyon";
            case "options":
                return en ? "options" : "secenek";
            case "selected_core":
                return en ? "Selected Core" : "Secili Merkez";
            case "customize":
                return en ? "Customize" : "Ozellestir";
            case "cores":
                return en ? "Cores" : "Merkezler";
            case "skins":
                return en ? "Skins" : "Skinler";
            case "settings":
                return en ? "Settings" : "Ayarlar";
            case "pause":
                return en ? "Pause" : "Durdur";
            case "paused":
                return en ? "Paused" : "Durdu";
            case "continue_game":
                return en ? "Continue" : "Devam Et";
            case "exit":
                return en ? "Exit" : "Cik";
            case "start_game":
                return en ? "Start Game" : "Oyuna Basla";
            case "watch_ad_coins":
                return en ? "Watch Ad For Coins" : "Coin Icin Reklam Izle";
            case "choose_skin":
                return en ? "Choose Skin" : "Skin Sec";
            case "skin_hint":
                return en ? "Unlock with coins, tap to equip." : "Coin ile ac, dokunup sec.";
            case "choose_core":
                return en ? "Choose Core" : "Merkez Sec";
            case "core_hint":
                return en ? "Choose a ball style or add a gallery photo to the center." : "Top stili sec veya merkeze galeriden resim ekle.";
            case "back":
                return en ? "Back" : "Geri";
            case "run_complete":
                return en ? "Run Complete" : "Run Tamamlandi";
            case "total_score":
                return en ? "Total Score" : "Toplam Skor";
            case "best_score":
                return en ? "Best Score" : "En iyi skor";
            case "play_again":
                return en ? "Play Again" : "Tekrar Oyna";
            case "menu":
                return "Menu";
            case "level_complete":
                return en ? "Level Complete" : "Seviye Tamam";
            case "bonus":
                return en ? "bonus" : "bonus";
            case "target":
                return en ? "Target" : "Hedef";
            case "shots":
                return en ? "shots" : "atis";
            case "level_badge":
                return en ? "LEVEL" : "SEVIYE";
            case "burned_out":
                return en ? "Run Failed" : "Yandik Cik";
            case "watch_ad_continue":
                return en ? "Watch Ad And Continue" : "Reklam Izle ve Devam Et";
            case "continue_used":
                return en ? "Continue chance already used in this level." : "Bu seviyede devam hakki kullanildi.";
            case "restart_level":
                return en ? "Restart Level" : "Bolumu Bastan";
            case "continue_offer":
                return en ? "Watch an ad to continue from where you left off." : "Istersen reklam izleyip kaldigin yerden devam et.";
            case "continue_offer_line_1":
                return en ? "Watch an ad to continue" : "Istersen reklam izleyip";
            case "continue_offer_line_2":
                return en ? "from where you left off." : "kaldigin yerden devam et.";
            case "ad_opening":
                return en ? "Opening ad..." : "Reklam aciliyor...";
            case "ad_unavailable":
                return en ? "Ad is not ready right now. Try again soon." : "Reklam su an hazir degil. Birazdan tekrar dene.";
            case "language":
                return en ? "Language" : "Dil";
            case "selected":
                return en ? "Selected" : "Secili";
            case "tap_select":
                return en ? "Tap to equip" : "Dokun sec";
            case "skin_needle":
                return en ? "Needle" : "Igne";
            case "skin_arrow":
                return en ? "Arrow" : "Ok";
            case "skin_sword":
                return en ? "Sword" : "Kilic";
            case "skin_dart":
                return en ? "Dart" : "Dart";
            case "skin_spear":
                return en ? "Spear" : "Mizrak";
            case "core_classic":
                return en ? "Classic" : "Klasik";
            case "core_soccer":
                return en ? "Soccer Ball" : "Futbol Topu";
            case "core_basketball":
                return en ? "Basketball" : "Basketbol Topu";
            case "core_tennis":
                return en ? "Tennis Ball" : "Tenis Topu";
            case "core_baseball":
                return en ? "Baseball" : "Beyzbol Topu";
            case "core_custom":
                return en ? "Custom Photo" : "Ozel Resim";
            case "choose_photo":
                return en ? "Choose photo" : "Resim sec";
            case "change_photo":
                return en ? "Tap to change" : "Degistirmek icin dokun";
            default:
                return key;
        }
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }

    private float normalizeAngle(float angle) {
        float result = angle % 360f;
        return result < 0f ? result + 360f : result;
    }

    private float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt((dx * dx) + (dy * dy));
    }

    private float distancePointToSegment(float px, float py, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float segmentLengthSquared = (dx * dx) + (dy * dy);
        if (segmentLengthSquared == 0f) {
            return distance(px, py, x1, y1);
        }
        float t = ((px - x1) * dx + (py - y1) * dy) / segmentLengthSquared;
        t = Math.max(0f, Math.min(1f, t));
        float closestX = x1 + (t * dx);
        float closestY = y1 + (t * dy);
        return distance(px, py, closestX, closestY);
    }

    private enum GameStatus {
        PLAYING,
        PAUSED,
        LEVEL_WON,
        LEVEL_FAILED,
        ALL_CLEAR
    }

    private enum ScreenMode {
        MENU,
        SKINS,
        CORES,
        SETTINGS,
        GAME,
        RESULT
    }

    private static final class LevelConfig {
        final int centerLabel;
        final int shotsToFire;
        final float rotationSpeedDegPerSec;
        final int clearBonus;
        final int coinReward;
        final List<Float> initialAngles = new ArrayList<>();

        LevelConfig(int centerLabel, int shotsToFire, float rotationSpeedDegPerSec, int clearBonus, int coinReward, float[] angles) {
            this.centerLabel = centerLabel;
            this.shotsToFire = shotsToFire;
            this.rotationSpeedDegPerSec = rotationSpeedDegPerSec;
            this.clearBonus = clearBonus;
            this.coinReward = coinReward;
            for (float angle : angles) {
                initialAngles.add(angle);
            }
        }
    }

    private static final class SkinConfig {
        final String id;
        final String name;
        final int cost;

        SkinConfig(String id, String name, int cost) {
            this.id = id;
            this.name = name;
            this.cost = cost;
        }
    }

    private static final class CoreStyle {
        final String id;
        final int cost;

        CoreStyle(String id, int cost) {
            this.id = id;
            this.cost = cost;
        }
    }

    private static final class CoinParticle {
        final float vx;
        final float maxLife;
        final float radius;
        float x;
        float y;
        float vy;
        float life;

        CoinParticle(float startX, float startY, float angleDeg, float speed, float radius) {
            double angleRad = Math.toRadians(angleDeg);
            this.x = startX;
            this.y = startY;
            this.vx = (float) (Math.cos(angleRad) * speed);
            this.vy = (float) (Math.sin(angleRad) * speed) - speed * 0.35f;
            this.radius = radius;
            this.maxLife = 0.75f;
            this.life = maxLife;
        }
    }

    public interface RewardedContinueGateway {
        void showRewardedContinue(RewardedContinueCallback callback);
    }

    public interface RewardedContinueCallback {
        void onRewardEarned();

        void onAdUnavailable();
    }

    public interface CustomTargetImageGateway {
        void openCustomTargetImagePicker();
    }
}
