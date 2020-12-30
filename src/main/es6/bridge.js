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

import { RSP, CallbackType } from './rsp.js';
import { Connection } from './connection.js';

const ProtocolDebugEnabledKey = "$bridge.protocolDebugEnabled";

/** @enum {number} */
const MessageType = {
    SET_RENDER_NUM: 0,
    CLEAN_ROOT: 1,
    LISTEN_EVENT: 2,
    EXTRACT_PROPERTY: 3,
    MODIFY_DOM: 4,
    FOCUS: 5,
    CHANGE_PAGE_URL: 6,
    UPLOAD_FORM: 7,
    RELOAD_CSS: 8,
    KEEP_ALIVE: 9,
    EVAL_JS: 10,
    EXTRACT_EVENT_DATA: 11,
    LIST_FILES: 12,
    UPLOAD_FILE: 13,
    REST_FORM: 14,
    FORGET_EVENT: 15
}

var protocolDebugEnabled = window.localStorage.getItem(ProtocolDebugEnabledKey) === 'true';

export class Bridge {

  /**
   * @param {Connection} connection
   */
  constructor(config, connection) {
    this._RSP = new RSP(config, this._onCallback.bind(this));
    this._RSP.registerRoot(document.documentElement);
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
   * @param {string} [argsString]
   * @param {Object} [eventObject]
   */
  _onCallback(type, argsString, eventObject) {
    let messageArr = [];
    messageArr.push(type);
    if (argsString) {
        messageArr.push(argsString);
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
    let k = this._RSP;
    switch (pCode) {
      case MessageType.SET_RENDER_NUM: k.setRenderNum.apply(k, commands); break;
      case MessageType.CLEAN_ROOT: k.cleanRoot.apply(k, commands); break;
      case MessageType.LISTEN_EVENT: k.listenEvent.apply(k, commands); break;
      case MessageType.EXTRACT_PROPERTY: k.extractProperty.apply(k, commands); break;
      case MessageType.MODIFY_DOM: k.modifyDom(commands); break;
      case MessageType.FOCUS: k.focus.apply(k, commands); break;
      case MessageType.CHANGE_PAGE_URL: k.setLocation.apply(k, commands); break;
      case MessageType.UPLOAD_FORM: k.uploadForm.apply(k, commands); break;
      case MessageType.RELOAD_CSS: k.reloadCss.apply(k, commands); break;
      case MessageType.KEEP_ALIVE: break;
      case MessageType.EVAL_JS: k.evalJs.apply(k, commands); break;
      case MessageType.EXTRACT_EVENT_DATA: k.extractEventData.apply(k, commands); break;
      case MessageType.LIST_FILES: k.listFiles.apply(k, commands); break;
      case MessageType.UPLOAD_FILE: k.uploadFile.apply(k, commands); break;
      case MessageType.REST_FORM: k.resetForm.apply(k, commands); break;
      case MessageType.FORGET_EVENT: k.forgetEvent.apply(k, commands); break;
      default: console.error(`Procedure ${pCode} is undefined`);
    }
  }

  destroy() {
    clearInterval(this._intervalId);
    this._connection.dispatcher.removeEventListener("message", this._messageHandler);
    this._RSP.destroy();
  }
}

/** @param {boolean} value */
export function setProtocolDebugEnabled(value) {
  window.localStorage.setItem(ProtocolDebugEnabledKey, value.toString());
  protocolDebugEnabled = value;
}
