#!/usr/bin/env python3
"""Test-only stdin bridge to Chaos-Module's production C codec/VFS engine.
No radio, fake firmware or TCP service. stdout is a single response line per input.
"""
import ctypes as C
import os
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(sys.argv[1]).resolve()
with tempfile.TemporaryDirectory(prefix='chaos-android-peer-') as work:
    fs = Path(work)
    (fs/'data').mkdir()
    (fs/'resource').mkdir()
    os.environ['CHAOS_CONNECT_TEST_ROOT'] = work
    library = fs/'peer.dylib'
    subprocess.run(['clang','-std=c11','-D_DEFAULT_SOURCE','-shared','-fPIC','-O2','-Wall','-Wextra','-Werror',
        '-I'+str(root/'supervisor/bridge'),str(root/'supervisor/tests/chaos_connect_host.c'),
        str(root/'supervisor/bridge/chaos_connect.c'),str(root/'supervisor/bridge/chaos_files.c'),'-o',str(library)],check=True)
    peer = C.CDLL(str(library))
    peer.cc_pb_unwrap.argtypes = [C.c_void_p,C.c_uint32,C.POINTER(C.c_void_p),C.POINTER(C.c_uint32),C.POINTER(C.c_uint32)]
    peer.cc_pb_wrap.argtypes = [C.c_void_p,C.c_uint32,C.c_void_p,C.c_uint32,C.c_uint32]
    for line in sys.stdin:
        try:
            parts = line.strip().split()
            if not parts: continue
            if parts[0] == 'ANDROID': peer.cc_test_android(1,int(parts[1]),int(parts[2])); print('OK',flush=True); continue
            if parts[0] == 'POLICY': peer.cc_test_policy(int(parts[1])); print('OK',flush=True); continue
            if parts[0] == 'DECIDE':
                peer.cc_test_decide(int(parts[1]),int(parts[2])); peer.cc_files_tick(100); print('OK',flush=True); continue
            if parts[0] == 'FAULT': peer.cc_test_fault(int(parts[1]),int(parts[2])); print('OK',flush=True); continue
            packet = C.create_string_buffer(bytes.fromhex(parts[0]))
            pointer = C.c_void_p(); size = C.c_uint32(); packet_id = C.c_uint32()
            if peer.cc_pb_unwrap(packet,len(packet.raw)-1,C.byref(pointer),C.byref(size),C.byref(packet_id)) != 1:
                raise ValueError('C envelope decode rejected request')
            if peer.cc_test_dispatch(pointer,size.value): raise ValueError('C frame decode rejected request')
            response = []
            for index in range(32):
                frame = C.create_string_buffer(4096); n = peer.cc_test_reply(index,frame)
                if not n: break
                wire = C.create_string_buffer(4096)
                reply_id = packet_id.value if frame.raw[5] == 1 else packet_id.value & 0xffff0000
                count = peer.cc_pb_wrap(wire,4096,frame,n,reply_id)
                if count <= 0: raise ValueError('C envelope encode failed')
                response.append(wire.raw[:count].hex())
            print(' '.join(response),flush=True)
        except Exception as error:
            print('ERROR '+str(error),flush=True)
    peer.cc_files_reset()
