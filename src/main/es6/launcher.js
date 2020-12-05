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

window['Korolev'] = {
  'setProtocolDebugEnabled': setProtocolDebugEnabled,
  'invokeCallback': () => console.log("Korolev is not ready"),
  'swapElementInRegistry': () => console.log("Korolev is not ready")
};

window.document.addEventListener("DOMContentLoaded", () => {

  let reconnect = true
  let config = window['kfg'];
  let clw = new ConnectionLostWidget(config['clw']);
  let connection = new Connection(
    getDeviceId(),
    config['sid'],
    config['r'],
    window.location
  );

  window['Korolev']['disconnect'] = () => {
    reconnect = false;
    connection.disconnect();
  }

  window['Korolev']['connect'] = () => connection.connect();

  connection.dispatcher.addEventListener('open', () => {
    clw.hide();
    let bridge = new Bridge(config, connection);
    window['Korolev']['swapElementInRegistry'] = (a, b) => bridge._korolev.swapElementInRegistry(a, b);
    window['Korolev']['element'] = (id) => bridge._korolev.element(id);
    window['Korolev']['invokeCallback'] = (name, arg) => bridge._korolev.invokeCustomCallback(name, arg);
    window['Korolev']['reload'] = () => { console.log('Reloading...'); window.location.reload(); };

    let closeHandler = (event) => {
      bridge.destroy();
      clw.show();
      connection
        .dispatcher
        .removeEventListener('close', closeHandler);
    };
    connection
      .dispatcher
      .addEventListener('close', closeHandler);
  });

  connection.dispatcher.addEventListener('close', () => {
    if (reconnect) {
      connection.connect();
    }
  });

  connection.connect();
});
