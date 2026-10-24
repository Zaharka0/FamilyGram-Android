def log(message):
    from family_plugin_host import current_bridge
    current_bridge().log(str(message))
def run_on_ui_thread(callback):
    from family_plugin_host import current_bridge
    current_bridge().runOnUI(callback)
