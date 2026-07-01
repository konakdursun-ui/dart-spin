package com.dkonak.dartat;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

public class MainActivity extends Activity {
    private static final int REQUEST_CUSTOM_TARGET_IMAGE = 7001;
    private static final int REQUEST_CROP_CUSTOM_TARGET_IMAGE = 7002;

    private PinGameView gameView;
    private RewardedAd rewardedAd;
    private boolean rewardedAdLoading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeAds();
        gameView = new PinGameView(this, this::showRewardedAd, this::openCustomTargetImagePicker);
        setContentView(gameView);
    }

    private void initializeAds() {
        new Thread(() -> {
            MobileAds.initialize(this, initializationStatus -> {});
            runOnUiThread(this::loadRewardedAd);
        }).start();
    }

    private void loadRewardedAd() {
        if (rewardedAd != null || rewardedAdLoading) {
            return;
        }
        rewardedAdLoading = true;
        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedAd.load(this, getString(R.string.rewarded_ad_unit_id), adRequest, new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(RewardedAd ad) {
                rewardedAd = ad;
                rewardedAdLoading = false;
            }

            @Override
            public void onAdFailedToLoad(LoadAdError loadAdError) {
                rewardedAd = null;
                rewardedAdLoading = false;
            }
        });
    }

    private void showRewardedAd(PinGameView.RewardedContinueCallback callback) {
        if (rewardedAd == null) {
            callback.onAdUnavailable();
            loadRewardedAd();
            return;
        }

        RewardedAd adToShow = rewardedAd;
        rewardedAd = null;
        adToShow.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                loadRewardedAd();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(AdError adError) {
                callback.onAdUnavailable();
                loadRewardedAd();
            }
        });

        adToShow.show(this, rewardItem -> {
            Toast.makeText(this, "Odul verildi.", Toast.LENGTH_SHORT).show();
            callback.onRewardEarned();
        });
    }

    private void openCustomTargetImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_CUSTOM_TARGET_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        if (requestCode == REQUEST_CUSTOM_TARGET_IMAGE) {
            openCropper(data.getData(), data.getFlags());
        } else if (requestCode == REQUEST_CROP_CUSTOM_TARGET_IMAGE && gameView != null) {
            gameView.setCustomTargetImage(data.getData());
        }
    }

    private void openCropper(Uri imageUri, int sourceFlags) {
        int readFlag = sourceFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        try {
            getContentResolver().takePersistableUriPermission(imageUri, readFlag);
        } catch (RuntimeException ignored) {
            // Temporary access is still enough while the crop screen is open.
        }

        Intent cropIntent = new Intent(this, CropImageActivity.class);
        cropIntent.setData(imageUri);
        cropIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(cropIntent, REQUEST_CROP_CUSTOM_TARGET_IMAGE);
    }
}
