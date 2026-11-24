package com.example.message.activities;

import android.os.Bundle;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.appcompat.widget.Toolbar;

import com.example.message.R;
import com.example.message.preferences.PreferenceManager;

public class ActivitySwipeAction extends BaseActivity {

    private Toolbar toolbar;
    private RadioGroup rgSwipeRight, rgSwipeLeft;
    private RadioButton rbRightRead, rbRightArchive, rbRightDelete, rbRightNone;
    private RadioButton rbLeftDelete, rbLeftArchive, rbLeftRead, rbLeftNone;

    private PreferenceManager prefManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_swipe_action);

        prefManager = new PreferenceManager(this);

        initViews();
        setupToolbar();
        setupSwipeOptions();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        rgSwipeRight = findViewById(R.id.rgSwipeRight);
        rbRightRead = findViewById(R.id.rbRightRead);
        rbRightArchive = findViewById(R.id.rbRightArchive);
        rbRightDelete = findViewById(R.id.rbRightDelete);
        rbRightNone = findViewById(R.id.rbRightNone);

        rgSwipeLeft = findViewById(R.id.rgSwipeLeft);
        rbLeftDelete = findViewById(R.id.rbLeftDelete);
        rbLeftArchive = findViewById(R.id.rbLeftArchive);
        rbLeftRead = findViewById(R.id.rbLeftRead);
        rbLeftNone = findViewById(R.id.rbLeftNone);
    }

    private void setupToolbar() {
        if (toolbar != null) {
            // CHANGE: Retrieve the selected language and apply the corresponding localized resources.
            toolbar.setTitle(R.string.swipe_actions);
            toolbar.setNavigationOnClickListener(v -> finish());
        }
    }

    private void setupSwipeOptions() {
        String rightAction = prefManager.getSwipeRightAction(); // read, archive, delete, none
        if ("archive".equalsIgnoreCase(rightAction)) {
            rbRightArchive.setChecked(true);
        } else if ("delete".equalsIgnoreCase(rightAction)) {
            rbRightDelete.setChecked(true);
        } else if ("none".equalsIgnoreCase(rightAction)) {
            rbRightNone.setChecked(true);
        } else {
            rbRightRead.setChecked(true);
        }

        String leftAction = prefManager.getSwipeLeftAction(); // delete, archive, read, none
        if ("archive".equalsIgnoreCase(leftAction)) {
            rbLeftArchive.setChecked(true);
        } else if ("read".equalsIgnoreCase(leftAction)) {
            rbLeftRead.setChecked(true);
        } else if ("none".equalsIgnoreCase(leftAction)) {
            rbLeftNone.setChecked(true);
        } else {
            rbLeftDelete.setChecked(true);
        }

        rgSwipeRight.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbRightArchive) {
                prefManager.setSwipeRightAction("archive");
            } else if (checkedId == R.id.rbRightDelete) {
                prefManager.setSwipeRightAction("delete");
            } else if (checkedId == R.id.rbRightNone) {
                prefManager.setSwipeRightAction("none");
            } else {
                prefManager.setSwipeRightAction("read");
            }
        });

        rgSwipeLeft.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbLeftArchive) {
                prefManager.setSwipeLeftAction("archive");
            } else if (checkedId == R.id.rbLeftRead) {
                prefManager.setSwipeLeftAction("read");
            } else if (checkedId == R.id.rbLeftNone) {
                prefManager.setSwipeLeftAction("none");
            } else {
                prefManager.setSwipeLeftAction("delete");
            }
        });
    }
}
