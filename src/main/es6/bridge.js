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

import { Korolev, CallbackType } from './korolev.js';
import { Connection } from './connection.js';

const ProtocolDebugEnabledKey = "$bridge.protocolDebugEnabled";

var protocolDebugEnabled = window.localStorage.getItem(ProtocolDebugEnabledKey) === 'true';

export class Bridge {

  /**
   * @param {Connection} connection
   */
  constructor(config, connection) {
    this._korolev = new Korolev(config, this._onCallback.bind(this));
    this._korolev.registerRoot(document.documentElement);
    this._connection = connection;
    this._messageHandler = this._onMessage.bind(this);

    connection.dispatcher.addEventListener("message", this._messageHandler);

    let interval = parseInt(config['heartbeatInterval'], 10);

    if (interval > 0) {
      this._intervalId = setInterval(() => this._onCallback(CallbackType.HEARTBEAT), interval);
    }
  }

  /**
   * @param {CallbackType} type
   * @param {string} [args]
   */
  _onCallback(type, args, eventObject) {
    let messageArr = [];
    messageArr.push(type);
    if (args) {
        messageArr.push(args);
    }
    if (eventObject) {
        messageArr.push(eventObject);
    }
    let message = JSON.stringify(messageArr);
    if (protocolDebugEnabled)
      console.log('<-', message);
    this._connection.send(message);
  }

  _onMessage(event) {
    if (protocolDebugEnabled)
      console.log('->', event.data);
    let commands = /** @type {Array} */ (JSON.parse(event.data));
    let pCode = commands.shift();
    let k = this._korolev;
    switch (pCode) {
      case 0: k.setRenderNum.apply(k, commands); break;
      case 1: k.cleanRoot.apply(k, commands); break;
      case 2: k.listenEvent.apply(k, commands); break;
      case 3: k.extractProperty.apply(k, commands); break;
      case 4: k.modifyDom(commands); break;
      case 5: k.focus.apply(k, commands); break;
      case 6: k.setLocation.apply(k, commands); break;
      case 7: k.uploadForm.apply(k, commands); break;
      case 8: k.reloadCss.apply(k, commands); break;
      case 9: break;
      case 10: k.evalJs.apply(k, commands); break;
      case 11: k.extractEventData.apply(k, commands); break;
      case 12: k.listFiles.apply(k, commands); break;
      case 13: k.uploadFile.apply(k, commands); break;
      case 14: k.resetForm.apply(k, commands); break;
      case 15: k.forgetEvent.apply(k, commands); break;
      default: console.error(`Procedure ${pCode} is undefined`);
    }
  }

  destroy() {
    clearInterval(this._intervalId);
    this._connection.dispatcher.removeEventListener("message", this._messageHandler);
    this._korolev.destroy();
  }
}

/** @param {boolean} value */
export function setProtocolDebugEnabled(value) {
  window.localStorage.setItem(ProtocolDebugEnabledKey, value.toString());
  protocolDebugEnabled = value;
}
