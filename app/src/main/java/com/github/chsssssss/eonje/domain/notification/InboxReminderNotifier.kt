package com.github.chsssssss.eonje.domain.notification

interface InboxReminderNotifier {
    fun notifyPending(count: Int)
}
