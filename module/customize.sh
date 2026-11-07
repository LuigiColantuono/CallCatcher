#!/system/bin/sh
# Sourced by the KernelSU/Magisk module installer. The system/ tree is mounted over /system at
# boot, which makes CallCatcher a privileged system app.

ui_print "- CallCatcher becomes a privileged system app after reboot"
ui_print "- If CallCatcher is installed as a normal app signed with a different key,"
ui_print "  uninstall it before rebooting, or Android will keep the old copy"
