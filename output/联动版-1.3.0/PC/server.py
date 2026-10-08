#!/usr/bin/env python3
"""Warehouse console, shared catalog and leased hardware commands. Stdlib only."""
import argparse
import base64
import errno
import hmac
import json
import mimetypes
import os
from pathlib import Path
import secrets
import socket
import sqlite3
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, unquote

ROOT = Path(__file__).resolve().parent
TABLES = ('warehouses', 'areas', 'products', 'bases', 'beacons', 'placements')
TERMINAL = ('stopped', 'failed', 'expired')


class Conflict(Exception):
    pass


class Store:
    def __init__(self, directory):
        self.directory = Path(directory)
        self.directory.mkdir(parents=True, exist_ok=True)
        self.db = sqlite3.connect(self.directory / 'warehouse.sqlite', check_same_thread=False)
        self.db.row_factory = sqlite3.Row
        self.lock = threading.RLock()
        self.db.executescript('''
          PRAGMA journal_mode=WAL;
          CREATE TABLE IF NOT EXISTS entities(kind TEXT,id TEXT,data TEXT,version INTEGER,
            PRIMARY KEY(kind,id));
          CREATE TABLE IF NOT EXISTS maps(id TEXT PRIMARY KEY,data TEXT,version INTEGER);
          CREATE TABLE IF NOT EXISTS tasks(id TEXT PRIMARY KEY,data TEXT);
          CREATE TABLE IF NOT EXISTS task_runs(id TEXT PRIMARY KEY,data TEXT);
          CREATE TABLE IF NOT EXISTS clients(id TEXT PRIMARY KEY,data TEXT);
          CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY,value TEXT);
        ''')
        row = self.db.execute("SELECT value FROM meta WHERE key='token'").fetchone()
        self.token = row[0] if row else secrets.token_urlsafe(32)
        self.db.execute("INSERT OR IGNORE INTO meta VALUES('token',?)", (self.token,))
        self.db.execute("INSERT OR IGNORE INTO meta VALUES('revision','0')")
        self.db.commit()

    def bump(self):
        self.db.execute("UPDATE meta SET value=CAST(value AS INTEGER)+1 WHERE key='revision'")
        return int(self.db.execute("SELECT value FROM meta WHERE key='revision'").fetchone()[0])

    def entities(self, kind):
        return [dict(json.loads(r['data']), version=r['version']) for r in
                self.db.execute('SELECT * FROM entities WHERE kind=?', (kind,))]

    def snapshot(self):
        with self.lock, self.db:
            self.expire()
            result = {kind: self.entities(kind) for kind in TABLES}
            result['maps'] = [dict(json.loads(r['data']), version=r['version'])
                              for r in self.db.execute('SELECT * FROM maps')]
            result['tasks'] = [json.loads(r['data']) for r in self.db.execute('SELECT data FROM tasks')]
            result['taskRuns'] = [json.loads(r['data']) for r in self.db.execute('SELECT data FROM task_runs')]
            result['clients'] = [json.loads(r['data']) for r in self.db.execute('SELECT data FROM clients')]
            result['revision'] = int(self.db.execute("SELECT value FROM meta WHERE key='revision'").fetchone()[0])
            result['serverTime'] = int(time.time() * 1000)
            return result

    def put_task(self, task):
        self.db.execute('INSERT OR REPLACE INTO tasks VALUES(?,?)',
                        (task['id'], json.dumps(task)))

    def expire(self):
        now = time.time() * 1000
        for row in self.db.execute('SELECT data FROM tasks').fetchall():
            task = json.loads(row[0])
            if task['status'] not in TERMINAL and now > task['expiresAt']:
                previous = task['status']
                message = '等待桥接超时，未下发点灯指令' if previous == 'queued' else '执行回执超时，实际状态待确认' if previous == 'dispatching' else '消灯未确认，请检查连接后重试' if previous == 'stop_pending' else '任务到期；信标按下发时长自动结束'
                task.update(status='failed' if previous == 'stop_pending' else 'expired', message=message, updatedAt=int(now))
                self.put_task(task)
        # Keep whole recent operations and their per-beacon receipts together.
        runs = sorted((json.loads(r[0]) for r in self.db.execute('SELECT data FROM task_runs')), key=lambda r: r['createdAt'], reverse=True)
        for run in runs[200:]:
            self.db.execute('DELETE FROM task_runs WHERE id=?', (run['id'],))
        referenced = {item['taskId'] for run in runs[:200] for item in run['items'] if item['taskId']}
        # Completed unreferenced commands are legacy history; active work is never removed.
        rows = self.db.execute('SELECT id,data FROM tasks').fetchall()
        finished = sorted((r for r in rows if json.loads(r['data'])['status'] in TERMINAL and r['id'] not in referenced),
                          key=lambda r: json.loads(r['data'])['updatedAt'], reverse=True)
        for row in finished[200:]:
            self.db.execute('DELETE FROM tasks WHERE id=?', (row['id'],))

    def sync(self, body):
        changes = body.get('changes', [])
        if not isinstance(changes, list) or len(changes) > 5000:
            raise ValueError('同步变更数量超出限制')
        with self.lock, self.db:
            for change in changes:
                kind, ident = change.get('kind'), change.get('id')
                if kind not in TABLES or not isinstance(ident, str) or not ident or len(ident) > 180:
                    raise ValueError('数据类型或 ID 不正确')
                row = self.db.execute('SELECT version FROM entities WHERE kind=? AND id=?',
                                      (kind, ident)).fetchone()
                version = row[0] if row else 0
                if change.get('expectedVersion', 0) != version:
                    raise Conflict('资料已被另一端修改，请同步后重试')
                if change.get('delete'):
                    self.db.execute('DELETE FROM entities WHERE kind=? AND id=?', (kind, ident))
                else:
                    data = dict(change.get('data') or {})
                    data['id'] = ident
                    data.pop('version', None)
                    if kind != 'placements':
                        data.pop('kind', None)
                    if kind in ('warehouses', 'areas', 'products') and not str(data.get('name', '')).strip():
                        raise ValueError('名称不能为空')
                    if kind == 'beacons':
                        data['code'] = normalize_code(data.get('code', ''))
                        if not data.get('baseSn'):
                            raise ValueError('信标需要所属基站')
                    if kind == 'products':
                        old_row = self.db.execute('SELECT data FROM entities WHERE kind=? AND id=?', (kind, ident)).fetchone()
                        old = json.loads(old_row[0]) if old_row else {}
                        if 'shelf' not in data and old.get('shelf'):
                            data['shelf'] = old['shelf'] if all(data.get(key, '') == old.get(key, '') for key in ('warehouseId', 'areaId')) else ''
                        if not isinstance(data.get('shelf', ''), str) or len(data.get('shelf', '').strip()) > 80:
                            raise ValueError('货架名称需要为 80 字以内的文字')
                        if 'shelf' in data: data['shelf'] = data['shelf'].strip()
                    if kind == 'products' and data.get('beaconCode'):
                        data['beaconCode'] = normalize_code(data['beaconCode'])
                    if kind == 'placements':
                        if not self.db.execute('SELECT 1 FROM maps WHERE id=?', (data.get('mapId'),)).fetchone():
                            raise ValueError('请先上传并保存地图')
                        for axis in ('x', 'y'):
                            if not isinstance(data.get(axis), (int, float)) or not 0 <= data[axis] <= 1:
                                raise ValueError('地图位置必须位于图片范围内')
                        data['source'] = 'configured'
                    self.db.execute('INSERT OR REPLACE INTO entities VALUES(?,?,?,?)',
                                    (kind, ident, json.dumps(data), self.bump()))
            self.validate_catalog()
            if changes:
                self.bump()
        return self.snapshot()

    def validate_catalog(self):
        warehouses = {r['id'] for r in self.entities('warehouses')}
        areas = {r['id']: r for r in self.entities('areas')}
        bound = set()
        for area in areas.values():
            if area.get('warehouseId') not in warehouses:
                raise ValueError('区域所属仓库不存在')
        for product in self.entities('products'):
            wh, area = product.get('warehouseId'), product.get('areaId')
            if wh and wh not in warehouses:
                raise ValueError('商品所属仓库不存在')
            if area and (area not in areas or areas[area].get('warehouseId') != wh):
                raise ValueError('商品区域与仓库不匹配')
            if product.get('shelf') and not area:
                raise ValueError('绑定货架前请先选择所属区域')
            code = product.get('beaconCode')
            if code:
                if code in bound:
                    raise ValueError('同一个信标只能绑定一个商品，请先解除原绑定')
                bound.add(code)

    def upload_map(self, body):
        ident = body.get('id') or secrets.token_hex(12)
        warehouse = body.get('warehouseId')
        if not isinstance(ident, str) or not ident.isalnum():
            raise ValueError('地图 ID 不正确')
        with self.lock, self.db:
            if warehouse not in {w['id'] for w in self.entities('warehouses')}:
                raise ValueError('请先选择仓库')
            old = self.db.execute('SELECT * FROM maps WHERE id=?', (ident,)).fetchone()
            if body.get('expectedVersion', 0) != (old['version'] if old else 0):
                raise Conflict('地图已更新，请同步后再上传')
            raw = base64.b64decode(body['image'].split(',')[-1], validate=True)
            if len(raw) > 15 * 1024 * 1024:
                raise ValueError('地图图片最大 15MB')
            extension = 'png' if raw.startswith(b'\x89PNG\r\n\x1a\n') else 'jpg' if raw.startswith(b'\xff\xd8\xff') else None
            if not extension:
                raise ValueError('请上传 PNG 或 JPG 图片')
            width, height = int(body['width']), int(body['height'])
            if not 1 <= width <= 20000 or not 1 <= height <= 20000:
                raise ValueError('图片尺寸超出范围')
            version = self.bump()
            # Immutable filenames keep an interrupted replacement from corrupting the previous map.
            filename = f'{ident}-{version}.{extension}'
            temporary = self.directory / (filename + '.tmp')
            temporary.write_bytes(raw)
            temporary.replace(self.directory / filename)
            data = dict(id=ident, warehouseId=warehouse, name=str(body.get('name') or '仓库地图')[:120],
                        floor=str(body.get('floor') or '1F')[:40], width=width, height=height,
                        imageUrl='/maps/' + filename)
            self.db.execute('INSERT OR REPLACE INTO maps VALUES(?,?,?)', (ident, json.dumps(data), version))
        return self.snapshot()

    def record_run(self, tasks, origin):
        run = dict(id=secrets.token_hex(16), createdAt=int(time.time() * 1000),
                   origin=str(origin)[:30], items=tasks)
        self.db.execute('INSERT INTO task_runs VALUES(?,?)', (run['id'], json.dumps(run)))
        self.bump()
        return run

    def run_item(self, product_id, task=None, error=''):
        product = next((p for p in self.entities('products') if p['id'] == product_id), {})
        return dict(productId=product_id or '', name=product.get('name', task.get('beaconCode', '') if task else product_id),
                    warehouseId=product.get('warehouseId', ''), taskId=task['id'] if task else '', error=error)

    def find(self, body):
        with self.lock, self.db:
            self.expire()
            task = self._find(body)
            self.record_run([self.run_item(body.get('productId'), task)], body.get('origin', 'pc'))
            return task

    def find_batch(self, body):
        products = body.get('productIds')
        if not isinstance(products, list) or not 1 <= len(products) <= 500 or any(not isinstance(p, str) or not p or len(p) > 180 for p in products):
            raise ValueError('请选择 1–500 个商品')
        with self.lock, self.db:
            self.expire()
            items = []
            for product_id in dict.fromkeys(products):
                try:
                    task = self._find(dict(body, productId=product_id))
                    items.append(self.run_item(product_id, task))
                except ValueError as error:
                    items.append(self.run_item(product_id, error=str(error)))
            return self.record_run(items, body.get('origin', 'pc'))

    def _find(self, body):
        product_id = body.get('productId')
        product = next((p for p in self.entities('products') if p['id'] == product_id), None)
        code = normalize_code(product.get('beaconCode', '') if product else body.get('beaconCode', ''))
        candidates = [b for b in self.entities('beacons') if b['code'] == code]
        if product_id and not product:
            raise ValueError('商品不存在')
        base_sn = body.get('baseSn')
        if not base_sn:
            if len(candidates) != 1:
                raise ValueError('请在手机登记信标所属基站；存在多个基站时需明确选择')
            base_sn = candidates[0]['baseSn']
        if not any(b['baseSn'] == base_sn for b in candidates):
            raise ValueError('该信标未登记到指定基站')
        for row in self.db.execute('SELECT data FROM tasks'):
            existing = json.loads(row[0])
            if existing['beaconCode'] == code and existing['status'] not in TERMINAL:
                return existing
        duration = int(body.get('durationSec', 60))
        color = int(body.get('color', 2))
        interval = float(body.get('intervalSec', 0.5))
        # Match the installed BLE SDK's 127-unit ceiling on both control paths.
        if not 3 <= duration <= 381 or not 1 <= color <= 7 or not 0.1 <= interval <= 12.7:
            raise ValueError('点灯参数超出范围')
        now = int(time.time() * 1000)
        task = dict(id=secrets.token_hex(16), productId=product_id or '', beaconCode=code,
                    baseSn=base_sn, warehouseId=product.get('warehouseId', '') if product else '',
                    origin=str(body.get('origin', 'pc'))[:30], color=color,
                    durationSec=duration, intervalSec=interval, flash=bool(body.get('flash', True)),
                    beep=bool(body.get('beep', False)), status='queued', action='light', generation=1,
                    createdAt=now, updatedAt=now, expiresAt=now + duration * 1000,
                    message='等待手机控制桥接', owner='', leaseUntil=0)
        self.put_task(task)
        self.bump()
        return task

    def stop(self, ident):
        with self.lock, self.db:
            task = self.task(ident)
            # Stop targets a physical beacon. Redirect an old history row to its current task.
            for row in self.db.execute('SELECT data FROM tasks ORDER BY rowid DESC'):
                current = json.loads(row[0])
                if current['beaconCode'] == task['beaconCode'] and current['status'] not in TERMINAL:
                    task = current
                    break
            if task['status'] == 'stopped' or task['status'] == 'stop_pending':
                return task
            task.update(action='stop', status='stop_pending', generation=task['generation'] + 1,
                        leaseUntil=0, updatedAt=int(time.time() * 1000),
                        expiresAt=int(time.time() * 1000) + 30000, message='正在发送消灯指令')
            self.put_task(task)
            self.bump()
            return task

    def task(self, ident):
        row = self.db.execute('SELECT data FROM tasks WHERE id=?', (ident,)).fetchone()
        if not row:
            raise ValueError('寻找任务不存在')
        return json.loads(row[0])

    def claim(self, body):
        client = str(body.get('clientId', ''))
        bases = body.get('bases', [])
        if not client or not isinstance(bases, list):
            raise ValueError('桥接设备信息不正确')
        with self.lock, self.db:
            now = int(time.time() * 1000)
            self.expire()
            self.db.execute('INSERT OR REPLACE INTO clients VALUES(?,?)',
                            (client, json.dumps(dict(id=client, bases=bases, lastSeen=now))))
            for row in self.db.execute('SELECT data FROM tasks ORDER BY rowid'):
                task = json.loads(row[0])
                if task['baseSn'] not in bases or task['status'] not in ('queued', 'dispatching', 'stop_pending'):
                    continue
                if task['leaseUntil'] > now:
                    continue
                if task['status'] == 'dispatching':
                    # Never blindly resend a possibly executed light command after a bridge crash.
                    task.update(status='failed', message='桥接回执超时，请检查信标后重新寻找', updatedAt=now)
                    self.put_task(task)
                    continue
                task.update(owner=client, leaseUntil=now + 15000, updatedAt=now)
                if task['action'] == 'light':
                    task['status'] = 'dispatching'
                self.put_task(task)
                return dict(task, serverTime=now)
            return None

    def report(self, ident, body):
        with self.lock, self.db:
            task = self.task(ident)
            if task['owner'] != body.get('clientId') or task['generation'] != body.get('generation'):
                raise Conflict('任务已停止或被更新，本次旧回执已忽略')
            self.expire()
            task = self.task(ident)
            if task['status'] in TERMINAL:
                return task
            status = body.get('status')
            allowed = ('sent', 'accepted', 'failed') if task['action'] == 'light' else ('stopped', 'failed')
            if status not in allowed:
                raise ValueError('执行状态不正确')
            if task['status'] == 'accepted' and status == 'sent':
                return task
            task.update(status=status, message=str(body.get('message', ''))[:500], updatedAt=int(time.time() * 1000))
            self.put_task(task)
            self.bump()
            return task


def normalize_code(code):
    value = str(code).strip()
    if not value.isascii() or not value.isdigit() or len(value) > 10 or int(value) > 0xffffffff:
        raise ValueError('信标码需要 4 字节范围内的十位数字')
    return f'{int(value):010d}'


def network_urls(port):
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
            # Route lookup only: UDP connect sends no packet.
            probe.connect(('192.0.2.1', 9))
            address = probe.getsockname()[0]
        return [f'http://{address}:{port}']
    except OSError:
        return []


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        # Keep credentials and request payloads out of logs.
        pass

    def json_response(self, status, data):
        raw = json.dumps(data, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(raw)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.end_headers()
        self.wfile.write(raw)

    def authorized(self):
        return hmac.compare_digest(self.headers.get('X-Access-Key', '').encode('utf-8'), self.server.store.token.encode('utf-8'))

    def do_GET(self):
        path = urlparse(self.path).path
        if path == '/api/session' and self.client_address[0] in ('127.0.0.1', '::1') and urlparse('http://' + self.headers.get('Host', '')).hostname in ('localhost', '127.0.0.1', '::1'):
            return self.json_response(200, {'token': self.server.store.token})
        if path.startswith('/api/'):
            if not self.authorized():
                return self.json_response(401, {'error': '请配置连接密钥'})
            if path == '/api/state':
                return self.json_response(200, self.server.store.snapshot())
            if path == '/api/network':
                return self.json_response(200, {'addresses': network_urls(self.server.server_port)})
            return self.json_response(404, {'error': '接口不存在'})
        if path.startswith('/maps/'):
            # Image URLs carry no catalog data. Random filenames are shared with paired clients only.
            directory = self.server.store.directory
            name = unquote(path[6:])
        else:
            directory = ROOT / 'dist'
            name = unquote(path.removeprefix('/console/')) if path.startswith('/console/') else 'index.html'
            name = name or 'index.html'
        file = (directory / name).resolve()
        if not file.is_relative_to(directory.resolve()) or not file.is_file() or file.suffix not in ('.html', '.js', '.css', '.png', '.jpg', '.jpeg', '.svg', '.woff', '.woff2', '.ttf', '.ico'):
            return self.json_response(404, {'error': '页面未构建或文件不存在'})
        raw = file.read_bytes()
        self.send_response(200)
        self.send_header('Content-Type', mimetypes.guess_type(str(file))[0] or 'application/octet-stream')
        self.send_header('Content-Length', str(len(raw)))
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Referrer-Policy', 'no-referrer')
        self.end_headers()
        self.wfile.write(raw)

    def do_POST(self):
        if not self.authorized():
            return self.json_response(401, {'error': '连接密钥不正确'})
        # Same-origin browser requests only; native Android requests have no Origin.
        origin = self.headers.get('Origin')
        if origin and urlparse(origin).netloc != self.headers.get('Host'):
            return self.json_response(403, {'error': '来源不匹配'})
        try:
            length = int(self.headers.get('Content-Length', '0'))
            if not 0 < length <= 22 * 1024 * 1024:
                raise ValueError('请求大小超出限制')
            body = json.loads(self.rfile.read(length))
            if not isinstance(body, dict):
                raise ValueError('请求需要 JSON 对象')
            path = urlparse(self.path).path
            store = self.server.store
            if path == '/api/sync':
                result = store.sync(body)
            elif path == '/api/maps':
                result = store.upload_map(body)
            elif path == '/api/find':
                result = store.find(body)
            elif path == '/api/find-batch':
                result = store.find_batch(body)
            elif path == '/api/bridge/claim':
                result = store.claim(body)
            elif path.startswith('/api/tasks/') and path.endswith('/stop'):
                result = store.stop(path.split('/')[3])
            elif path.startswith('/api/tasks/') and path.endswith('/report'):
                result = store.report(path.split('/')[3], body)
            else:
                return self.json_response(404, {'error': '接口不存在'})
            self.json_response(200, result)
        except Conflict as error:
            self.json_response(409, {'error': str(error)})
        except (ValueError, KeyError, TypeError, OverflowError) as error:
            self.json_response(400, {'error': str(error)})
        except Exception:
            self.json_response(500, {'error': '服务处理失败，数据事务已回滚'})


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--port', type=int, default=8088)
    parser.add_argument('--host', default='0.0.0.0')
    parser.add_argument('--data', default=str(ROOT / 'data'))
    parser.add_argument('--auto-port', action='store_true')
    parser.add_argument('--open', action='store_true')
    args = parser.parse_args()
    store = Store(args.data)
    while True:
        try:
            server = ThreadingHTTPServer((args.host, args.port), Handler)
            break
        except OSError as error:
            if not args.auto_port or error.errno != errno.EADDRINUSE:
                raise
            args.port += 1
    server.store = store
    server.daemon_threads = True
    print(f'PC 控制台：http://localhost:{args.port}/console/', flush=True)
    for address in network_urls(args.port):
        print(f'手机服务地址：{address}', flush=True)
    print('手机连接：填写这台电脑的局域网 IP、端口及控制台「系统设置」中的密钥。', flush=True)
    if args.open:
        import webbrowser
        threading.Timer(0.5, lambda: webbrowser.open(f'http://localhost:{args.port}/console/')).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
        store.db.close()


if __name__ == '__main__':
    main()
