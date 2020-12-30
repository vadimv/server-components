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
  'invokeCallback': () => console.log("RSP is not ready"),
  'swapElementInRegistry': () => console.log("RSP is not ready")
};
var reconnect;
// TODO
//window.addEventListener("onbeforeunload", () => reconnect = false);
window.onbeforeunload = function(event) {
        console.log("onbeforeunload");
        reconnect = false;
}

window.document.addEventListener("DOMContentLoaded", () => {

  reconnect = true

  let config = window['kfg'];
  let clw = new ConnectionLostWidget(config['clw']);
  let connection = new Connection(
    getDeviceId(),
    config['sid'],
    config['r'],
    window.location
  );

  window['RSP']['disconnect'] = () => {
    reconnect = false;
    connection.disconnect();
  }

  window['RSP']['connect'] = () => connection.connect();

  connection.dispatcher.addEventListener('open', () => {
    clw.hide();
    let bridge = new Bridge(config, connection);
    window['RSP']['swapElementInRegistry'] = (a, b) => bridge._RSP.swapElementInRegistry(a, b);
    window['RSP']['element'] = (id) => bridge._RSP.element(id);
    window['RSP']['invokeCallback'] = (name, arg) => bridge._RSP.invokeCustomCallback(name, arg);
    window['RSP']['reload'] = () => {
        console.log('Reload command');
        reconnect = false;
        connection.disconnect();
        setTimeout(() => {
            console.log("Reloading...");
            window.location.reload();
            }, 3000);
    };

    let closeHandler = (event) => {
      bridge.destroy();
      if (reconnect) {
        clw.show();
        connection.connect();
      }
/*      connection
        .dispatcher
        .removeEventListener('close', closeHandler);*/
    };
    connection
      .dispatcher
      .addEventListener('close', closeHandler);
  });

/*  connection.dispatcher.addEventListener('close', () => {
    if (reconnect) {
      connection.connect();
    }
  });*/

  connection.connect();
});
