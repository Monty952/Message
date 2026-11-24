package com.example.message.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.message.database.AppDatabase;
import com.example.message.database.ScheduledMessageEntity;
import com.example.message.utils.SmsHelper;

public class ScheduledSmsWorker extends Worker {

    public ScheduledSmsWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        long scheduledId = getInputData().getLong("scheduled_id", -1);
        if (scheduledId == -1) return Result.failure();

        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        ScheduledMessageEntity entity = db.scheduledMessageDao().getByIdSync(scheduledId);
        if (entity == null || !"PENDING".equals(entity.getStatus())) {
            return Result.success();
        }

        boolean success = SmsHelper.sendSms(getApplicationContext(), entity.getAddress(), entity.getMessage());
        if (success) {
            db.scheduledMessageDao().updateStatus(scheduledId, "SENT");
            return Result.success();
        } else {
            db.scheduledMessageDao().updateStatus(scheduledId, "FAILED");
            return Result.retry();
        }
    }
}
