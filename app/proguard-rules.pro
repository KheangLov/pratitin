# The app uses no reflection; R8 full mode can strip freely.
-keep class com.pratitin.app.widget.** extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
