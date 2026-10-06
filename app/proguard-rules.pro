# Worker class names are persisted by WorkManager and used for reflective construction.
-keep class app.veyra.android.ReminderWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }
-keep class app.veyra.android.MaintenanceWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }
