/*
 *  Copyright 2015 Aleksey Fomkin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import { Connection } from './connection.js';
import { Bridge, setProtocolDebugEnabled } from './bridge.js';
import { ConnectionLostWidget, getDeviceId } from './utils.js';

window['RSP'] = {
  'setProtocolDebugEnabled': setProtocolDebugEnabled,
  'invokeCallback': () => console.log('RSP is not ready'),
  'swapElementInRegistry': () => console.log('RSP is not ready'),
  'connectionState': 'connecting'
};

function createRspConnectionEvent(type, state) {
  let event;
  if (typeof Event === 'function') {
    event = new Event(type);
  } else {
    event = document.createEvent('Event');
    event.initEvent(type, false, false);
  }
  event['state'] = state;
  return event;
}

function setRspConnectionState(state) {
  window['RSP']['connectionState'] = state;
  if (document.body) {
    document.body.setAttribute('data-rsp-connection', state);
  }
  document.dispatchEvent(createRspConnectionEvent('rsp:connection-state', state));
  document.dispatchEvent(createRspConnectionEvent(`rsp:connection-${state}`, state));
}

window.document.addEventListener('DOMContentLoaded', () => {
  let reconnect = true;
  let reloading = false;
  let config = window['kfg'];
  let clw = new ConnectionLostWidget(config['clw']);
  let connection = new Connection(
    getDeviceId(),
    config['sid'],
    config['r'],
    window.location
  );
  // The bridge and its browser-side virtual DOM registry survive transient sockets.
  let bridge = new Bridge(config, connection);

  let reloadPage = () => {
    if (reloading) return;
    reloading = true;
    reconnect = false;
    bridge.suspend();
    connection.disconnect(true);
    setTimeout(() => window.location.reload(), 0);
  };

  window['RSP']['swapElementInRegistry'] = (a, b) => bridge._RSP.swapElementInRegistry(a, b);
  window['RSP']['element'] = (id) => bridge._RSP.element(id);
  window['RSP']['invokeCallback'] = (name, arg) => bridge._RSP.invokeCustomCallback(name, arg);
  window['RSP']['reload'] = reloadPage;
  window['RSP']['disconnect'] = () => {
    reconnect = false;
    bridge.destroy();
    connection.disconnect(true);
    setRspConnectionState('closed');
  };
  window['RSP']['connect'] = () => {
    reconnect = true;
    connection.connect();
  };

  connection.dispatcher.addEventListener('connecting', () => {
    setRspConnectionState('connecting');
  });

  connection.dispatcher.addEventListener('open', () => {
    bridge.resume();
    clw.hide();
    setRspConnectionState('open');
  });

  connection.dispatcher.addEventListener('close', () => {
    bridge.suspend();
    setRspConnectionState('closed');
    if (reconnect) {
      clw.show();
      connection.connect();
    }
  });

  connection.dispatcher.addEventListener('resume-rejected', () => reloadPage());

  window.addEventListener('beforeunload', () => {
    reconnect = false;
    connection.disconnect(true);
  });

  setRspConnectionState('connecting');
  connection.connect();
});
