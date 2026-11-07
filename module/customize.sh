#!/system/bin/sh
# Sourced by the KernelSU/Magisk module installer. The system/ tree is mounted over /system at
# boot, which makes CallCatcher a privileged system app.

ui_print "- CallCatcher becomes a privileged system app after reboot"

# Zip extraction keeps the file times from the CI build. Android's package cache is only
# refreshed when the APK is newer than the cache entry, so a module update could keep the
# previous version's parsed manifest (and with it the old permission list). Make the APK
# "new" and drop CallCatcher's cache entries; Android rebuilds them on the next boot.
touch "$MODPATH/system/priv-app/com.callcatcher/com.callcatcher.apk"
rm -f /data/system/package_cache/*/com.callcatcher-* 2>/dev/null
ui_print "- Package cache cleared for CallCatcher"

ui_print "- If CallCatcher is installed as a normal app signed with a different key,"
ui_print "  uninstall it before rebooting, or Android will keep the old copy"
