# The app uses no reflection; R8 full mode can strip freely — except for widgets.
#
# Glance identifies each widget by its GlanceAppWidget *class name* (it maps receivers to
# providers and finds widgets to update by class). R8's class merging folded all five
# widget classes into one, so every widget on the home screen rendered the same content.
# Keep every widget and receiver class as its own, unrenamed class.
-keep class * extends androidx.glance.appwidget.GlanceAppWidget
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
