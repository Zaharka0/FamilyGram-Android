from pathlib import Path
import json, sys, types, unittest
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'TMessagesProj/src/main/python'))
import family_plugin_host as host

class Bridge:
    def __init__(self,partner=20):self.values={};self.partner=partner
    def getSetting(self,key):return self.values.get(key)
    def setSetting(self,key,value):self.values[key]=value
    def getPartner(self):return self.partner
    def getAccount(self):return 0
    def log(self,message):pass

class PluginsTest(unittest.TestCase):
    def setUp(self):self.source=(ROOT/'TMessagesProj/src/main/assets/family_plugins/heart_signature.plugin').read_text();self.bridge=Bridge()
    def tearDown(self):
        for uid,plugin in list(host._instances):
            try:host.unload(uid,plugin)
            except Exception:pass
    def test_metadata_never_executes(self):
        info=json.loads(host.metadata(self.source+'\nraise RuntimeError("must not run")\n'))
        self.assertEqual(info['id'],'heart_signature');self.assertFalse(host._instances)
    def test_no_metadata_expressions(self):
        with self.assertRaises(ValueError):host.metadata("__id__=str('xx')\n__name__='Example'")
    def test_path_and_size(self):
        for ident in ['../bad','a/b','a','a'*33]:
            with self.assertRaises(ValueError):host.metadata(f'__id__={ident!r}\n__name__="X"')
        with self.assertRaises(ValueError):host.metadata('#'*(262145))
    def test_unsupported_hooks_and_pip(self):
        for extra in ['\nfrom hook_utils import find_class','\n__requirements__=["requests"]','\ndef hook(self): self.hook_method(None)','\n__familygram_sdk__=999']:
            with self.assertRaises(ValueError):host.metadata(self.source+extra)
    def test_lifecycle_settings_and_account_scope(self):
        host.load(10,self.source,self.bridge);other=Bridge(30);host.load(11,self.source,other)
        rows=json.loads(host.settings(10,'heart_signature'));self.assertEqual(rows[1]['type'],'switch');self.assertFalse(rows[1]['value'])
        params=types.SimpleNamespace(peer=20,message='Hello');host.send(10,'heart_signature',0,params);self.assertEqual(params.message,'Hello')
        self.bridge.setSetting('active','true');host.send(10,'heart_signature',0,params);self.assertEqual(params.message,'Hello ❤️')
        host.send(10,'heart_signature',0,params);self.assertEqual(params.message,'Hello ❤️')
        params=types.SimpleNamespace(peer=999,message='Hi');host.send(10,'heart_signature',0,params);self.assertEqual(params.message,'Hi')
        params=types.SimpleNamespace(peer=30,message='Hi');host.send(11,'heart_signature',1,params);self.assertEqual(params.message,'Hi')
        host.unload(10,'heart_signature');self.assertNotIn((10,'heart_signature'),host._instances);self.assertIn((11,'heart_signature'),host._instances)
    def test_failed_load_not_registered(self):
        with self.assertRaises(RuntimeError):host.load(10,self.source+'\nraise RuntimeError("test")',self.bridge)
        self.assertNotIn((10,'heart_signature'),host._instances)
        self.assertNotIn('_family_plugin_10_heart_signature',sys.modules)
    def test_cancel_hook(self):
        source="""from base_plugin import BasePlugin,HookResult,HookStrategy
__id__='cancel_test'
__name__='Cancel'
class Test(BasePlugin):
 def on_plugin_load(self): self.add_on_send_message_hook()
 def on_send_message_hook(self,account,params):return HookResult(HookStrategy.CANCEL)
"""
        host.load(10,source,self.bridge);self.assertFalse(host.send(10,'cancel_test',0,object()))
    def test_loop_budget(self):
        source="""from base_plugin import BasePlugin
__id__='loop_test'
__name__='Loop'
class Test(BasePlugin):
 def on_plugin_load(self):
  while True: pass
"""
        with self.assertRaises(TimeoutError):host.load(10,source,self.bridge)
        self.assertNotIn((10,'loop_test'),host._instances)

if __name__=='__main__':unittest.main()
