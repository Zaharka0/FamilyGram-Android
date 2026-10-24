"""Account is the one owning the current callback, never selectedAccount."""
def get_account_instance():
    from org.telegram.messenger import AccountInstance
    from family_plugin_host import current_bridge
    return AccountInstance.getInstance(current_bridge().getAccount())
def get_messages_controller(): return get_account_instance().getMessagesController()
def get_user_config(): return get_account_instance().getUserConfig()
def get_send_messages_helper(): return get_account_instance().getSendMessagesHelper()
def get_partner():
    from family_plugin_host import current_bridge
    return current_bridge().getPartner()
def get_last_fragment():
    from org.telegram.ui import LaunchActivity
    from family_plugin_host import current_bridge
    fragment=LaunchActivity.getSafeLastFragment()
    return fragment if fragment is not None and fragment.getCurrentAccount()==current_bridge().getAccount() else None
