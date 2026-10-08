"""Run: python3 -m unittest discover -s pc -p 'test_*.py'. No hardware needed."""
import base64
import json
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer
from server import Store, Handler, Conflict


class LinkedSystemCheck(unittest.TestCase):
    def test_catalog_map_tasks_and_http(self):
        with tempfile.TemporaryDirectory() as directory:
            store = Store(directory)
            def change(entity_kind, ident, **data):
                return dict(kind=entity_kind, id=ident, expectedVersion=0, data=dict(id=ident, **data))
            state = store.sync({'changes': [
                change('warehouses', 'wh', name='我的仓库', createdAt=1),
                change('areas', 'area', name='A货架', warehouseId='wh', createdAt=1),
                change('bases', 'BLE', sn='BLE', name='Bluetooth 基站'),
                change('beacons', 'BLE_0000000123', code='123', baseSn='BLE', name='信标'),
                change('products', 'p', name='轴承', sku='P001', warehouseId='wh', areaId='area', beaconCode='123', updatedAt=1)
            ]})
            self.assertEqual(state['products'][0]['beaconCode'], '0000000123')
            old = state['products'][0]
            store.sync({'changes': [dict(kind='products', id='p', expectedVersion=old['version'], data=dict(old, name='轴承新名称'))]})
            with self.assertRaises(Conflict):
                store.sync({'changes': [dict(kind='products', id='p', expectedVersion=old['version'], data=old)]})
            with self.assertRaises(ValueError):
                store.sync({'changes': [change('products', 'duplicate', name='重复绑定', beaconCode='123')]})
            self.assertEqual(len(store.snapshot()['products']), 1)
            image = 'data:image/png;base64,' + base64.b64encode(b'\x89PNG\r\n\x1a\nfixture').decode()
            state = store.upload_map(dict(id='map', warehouseId='wh', name='手绘地图', floor='1F', width=100, height=80, image=image))
            store.sync({'changes': [change('placements', 'point', kind='product', entityId='p', mapId='map', x=.25, y=.7)]})
            product = store.snapshot()['products'][0]
            store.sync({'changes': [dict(kind='products', id='p', expectedVersion=product['version'], data=dict(product, shelf='R-01'))]})
            saved = store.snapshot()['products'][0]
            legacy = dict(saved); legacy.pop('shelf')
            store.sync({'changes': [dict(kind='products', id='p', expectedVersion=saved['version'], data=legacy)]})
            self.assertEqual(store.snapshot()['products'][0]['shelf'], 'R-01')
            current = store.snapshot()['products'][0]
            with self.assertRaises(ValueError):
                store.sync({'changes': [dict(kind='products', id='p', expectedVersion=current['version'], data=dict(current, areaId='', shelf='R-01'))]})
            self.assertEqual(store.snapshot()['products'][0]['areaId'], 'area')
            # Restart retains bindings and map coordinates.
            other = Store(directory)
            self.assertEqual(other.snapshot()['placements'][0]['x'], .25)
            self.assertEqual(other.token, store.token)
            other.db.close()
            task = store.find(dict(productId='p', origin='phone'))
            self.assertEqual(task['id'], store.find(dict(productId='p', origin='pc'))['id'])
            claim = store.claim(dict(clientId='phone1', bases=['BLE']))
            self.assertEqual(claim['id'], task['id'])
            self.assertIsNone(store.claim(dict(clientId='phone2', bases=['BLE'])))
            report = dict(clientId='phone1', generation=claim['generation'], status='sent', message='SDK 已发送')
            store.report(task['id'], report)
            stopped = store.stop(task['id'])
            self.assertEqual(stopped['generation'], store.stop(task['id'])['generation'])
            with self.assertRaises(Conflict): store.report(task['id'], report)
            off = store.claim(dict(clientId='phone1', bases=['BLE']))
            self.assertEqual(off['action'], 'stop')
            store.report(task['id'], dict(clientId='phone1', generation=off['generation'], status='stopped', message='已消灯'))
            self.assertEqual(store.task(task['id'])['status'], 'stopped')
            # Clock-based expiration never replays an old light command.
            expired = store.find(dict(productId='p'))
            with store.lock, store.db:
                expired['expiresAt'] = 1; store.put_task(expired)
            self.assertIsNone(store.claim(dict(clientId='phone1', bases=['BLE'])))
            self.assertEqual(store.task(expired['id'])['status'], 'expired')
            # Different goods stay active together; stopping one never changes the others.
            store.sync({'changes': [
                change('beacons', 'BLE_0000000456', code='456', baseSn='BLE', name='信标二'),
                change('products', 'p2', name='螺丝', warehouseId='wh', beaconCode='456')
            ]})
            first = store.find(dict(productId='p'))
            second = store.find(dict(productId='p2'))
            self.assertNotEqual(first['id'], second['id'])
            store.stop(first['id'])
            self.assertEqual(store.task(second['id'])['status'], 'queued')
            claims = [store.claim(dict(clientId='phone1', bases=['BLE'])), store.claim(dict(clientId='phone1', bases=['BLE']))]
            self.assertEqual({c['id'] for c in claims}, {first['id'], second['id']})
            self.assertEqual(next(c for c in claims if c['id'] == first['id'])['action'], 'stop')
            self.assertEqual(next(c for c in claims if c['id'] == second['id'])['action'], 'light')
            # A batch is one persistent user operation with independent beacon commands.
            before = len(store.snapshot()['taskRuns'])
            run = store.find_batch(dict(productIds=['p', 'p2', 'missing', 'p'], origin='pc'))
            self.assertEqual(len(run['items']), 3)
            self.assertEqual(len(store.snapshot()['taskRuns']), before + 1)
            self.assertTrue(run['items'][2]['error'])
            self.assertEqual({item['taskId'] for item in run['items'][:2]}, {first['id'], second['id']})
            restarted = Store(directory)
            self.assertIn(run['id'], {r['id'] for r in restarted.snapshot()['taskRuns']})
            restarted.db.close()
            with self.assertRaises(ValueError): store.find_batch(dict(productIds=[]))
            server = ThreadingHTTPServer(('127.0.0.1', 0), Handler); server.store = store
            thread = threading.Thread(target=server.serve_forever, daemon=True); thread.start()
            url = f'http://127.0.0.1:{server.server_port}'
            try:
                with self.assertRaises(urllib.error.HTTPError) as denied:
                    urllib.request.urlopen(url + '/api/state')
                self.assertEqual(denied.exception.code, 401)
                req = urllib.request.Request(url + '/api/session', headers={'Host': 'other.test'})
                with self.assertRaises(urllib.error.HTTPError) as denied: urllib.request.urlopen(req)
                self.assertEqual(denied.exception.code, 401)
                req = urllib.request.Request(url + '/api/state', headers={'X-Access-Key': store.token})
                result = json.load(urllib.request.urlopen(req))
                self.assertEqual(result['maps'][0]['name'], '手绘地图')
                self.assertEqual(result['products'][0]['name'], '轴承新名称')
                req = urllib.request.Request(url + '/api/find', data=b'{}', headers={'X-Access-Key': store.token, 'Origin': 'http://other.test'})
                with self.assertRaises(urllib.error.HTTPError) as denied: urllib.request.urlopen(req)
                self.assertEqual(denied.exception.code, 403)
            finally:
                server.shutdown(); server.server_close(); store.db.close()


if __name__ == '__main__':
    unittest.main()
