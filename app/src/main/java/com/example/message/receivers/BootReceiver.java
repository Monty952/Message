package com.example.message.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.message.database.AppDatabase;
import com.example.message.database.ScheduledMessageEntity;
import com.example.message.workers.ScheduledSmsWorker;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Executors.newSingleThreadExecutor().execute(() -> {
                AppDatabase db = AppDatabase.getInstance(context);
                List<ScheduledMessageEntity> list = db.scheduledMessageDao().getPendingSync();
                long now = System.currentTimeMillis();

                for (ScheduledMessageEntity item : list) {
                    long delay = Math.max(0, item.getScheduledTimestamp() - now);
                    Data inputData = new Data.Builder()
                            .putLong("scheduled_id", item.getId())
                            .build();

                    OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(ScheduledSmsWorker.class)
                            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                            .setInputData(inputData)
                            .build();

                    WorkManager.getInstance(context).enqueue(work);
                    item.setWorkId(work.getId().toString());
                    db.scheduledMessageDao().update(item);
                }
            });
        }
    }
}
