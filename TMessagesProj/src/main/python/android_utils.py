def log(message):
    from family_plugin_host import current_bridge
    current_bridge().log(str(message))
def run_on_ui_thread(callback):
    from java import dynamic_proxy
    from java.lang import Runnable
    from org.telegram.messenger import AndroidUtilities
    class Task(dynamic_proxy(Runnable)):
        def run(self): callback()
    AndroidUtilities.runOnUIThread(Task())
